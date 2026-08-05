package com.finscope.service.research.material;

import com.finscope.dao.cache.ResearchMaterialCacheRepository;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialCacheEntry;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import com.finscope.rpc.research.material.ResearchMaterialProvider;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.marketdata.ProviderRoutePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ResearchMaterialGateway {
    private final List<ResearchMaterialProvider> providers;
    private final ProviderRoutePolicy routePolicy;
    private final ProviderRequestGuard guard;
    private final ResearchMaterialCacheRepository cache;
    private final Duration cacheTtl;

    public ResearchMaterialGateway(List<ResearchMaterialProvider> providers,
                                   ProviderRoutePolicy routePolicy,
                                   ProviderRequestGuard guard) {
        this(providers, routePolicy, guard, ResearchMaterialCacheRepository.noop(), Duration.ofMinutes(4));
    }

    public ResearchMaterialGateway(List<ResearchMaterialProvider> providers,
                                   ProviderRoutePolicy routePolicy,
                                   ProviderRequestGuard guard,
                                   ResearchMaterialCacheRepository cache) {
        this(providers, routePolicy, guard, cache, Duration.ofMinutes(4));
    }

    @Autowired
    public ResearchMaterialGateway(List<ResearchMaterialProvider> providers,
                                   ProviderRoutePolicy routePolicy,
                                   ProviderRequestGuard guard,
                                   ResearchMaterialCacheRepository cache,
                                   @Value("${finscope.redis.cache.research-material-ttl-seconds:240}") long cacheTtlSeconds) {
        this(providers, routePolicy, guard, cache,
                Duration.ofSeconds(Math.max(1L, cacheTtlSeconds)));
    }

    private ResearchMaterialGateway(List<ResearchMaterialProvider> providers,
                                    ProviderRoutePolicy routePolicy,
                                    ProviderRequestGuard guard,
                                    ResearchMaterialCacheRepository cache,
                                    Duration cacheTtl) {
        this.providers = providers == null ? new ArrayList<ResearchMaterialProvider>()
                : new ArrayList<ResearchMaterialProvider>(providers);
        this.routePolicy = routePolicy;
        this.guard = guard;
        this.cache = cache == null ? ResearchMaterialCacheRepository.noop() : cache;
        this.cacheTtl = cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()
                ? Duration.ofMinutes(4) : cacheTtl;
    }

    public ResearchMaterialGatewayResult search(ResearchMaterialType type, ResearchMaterialRequest request) {
        String cacheKey = cacheKey(type, request);
        try {
            Optional<ResearchMaterialCacheEntry> cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                ResearchMaterialCacheEntry entry = cached.get();
                return new ResearchMaterialGatewayResult(entry.getMaterials(), entry.getWarnings());
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时继续走现有 Provider 链路。
        }

        ResearchMaterialGatewayResult result = fetch(type, request);
        if (!result.getMaterials().isEmpty()) {
            try {
                cache.put(cacheKey, new ResearchMaterialCacheEntry(
                        result.getMaterials(), result.getWarnings(), LocalDateTime.now()), cacheTtl);
            } catch (RuntimeException ignored) {
                // 缓存写入失败不能阻断已成功取得的外部资料。
            }
        }
        return result;
    }

    private ResearchMaterialGatewayResult fetch(ResearchMaterialType type,
                                                 ResearchMaterialRequest request) {
        String capability = "RESEARCH_MATERIAL_" + type.name();
        List<ResearchMaterialProvider> ordered = routePolicy.orderExternal(
                providers, capability, provider -> provider.supports(type, request));
        Map<String, ResearchMaterial> unique = new LinkedHashMap<String, ResearchMaterial>();
        List<String> warnings = new ArrayList<String>();
        int supportedProviders = 0;
        for (ResearchMaterialProvider provider : providers) {
            if (provider.supports(type, request)) supportedProviders++;
        }
        if (ordered.isEmpty()) {
            warnings.add(supportedProviders == 0
                    ? "没有配置支持 " + type + " 的研究资料来源"
                    : "支持 " + type + " 的研究资料来源当前均被熔断或暂停");
        }
        for (ResearchMaterialProvider provider : ordered) {
            try {
                ProviderResult<List<ResearchMaterial>> fetched = guard.execute(
                        provider, capability, () -> provider.fetch(type, request));
                if (fetched == null || fetched.getData() == null) {
                    warnings.add(provider.providerCode() + "：返回空结果对象");
                    continue;
                }
                warnings.addAll(fetched.getWarnings());
                for (ResearchMaterial material : fetched.getData()) {
                    if (valid(material)) unique.putIfAbsent(key(material), material);
                }
            } catch (RuntimeException error) {
                warnings.add(provider.providerCode() + "：" + safe(error));
            }
        }
        return new ResearchMaterialGatewayResult(new ArrayList<ResearchMaterial>(unique.values()), warnings);
    }

    private String cacheKey(ResearchMaterialType type, ResearchMaterialRequest request) {
        String raw = (type == null ? "" : type.name()) + "|"
                + (request == null ? "" : request.getStockCode()) + "|"
                + (request == null ? "" : request.getQuery()) + "|"
                + (request == null ? "" : request.getLimit());
        return "finscope:research-material:" + sha256(raw);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception error) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private boolean valid(ResearchMaterial value) {
        return value != null && value.getMaterialType() != null
                && !blank(value.getTitle()) && !blank(value.getContent())
                && !blank(value.getProviderCode()) && !blank(value.getSourceTier());
    }

    private String key(ResearchMaterial value) {
        if (!blank(value.getUrl())) return value.getUrl().trim();
        return value.getMaterialType() + "|" + value.getProviderFamily() + "|" + value.getExternalId();
    }

    private String safe(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName()
                : message.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
