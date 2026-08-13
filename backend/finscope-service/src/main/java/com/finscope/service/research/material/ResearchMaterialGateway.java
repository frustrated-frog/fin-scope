package com.finscope.service.research.material;

import com.finscope.dao.cache.ResearchMaterialCacheRepository;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialCacheEntry;
import com.finscope.common.enums.research.ResearchMaterialType;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import com.finscope.rpc.research.material.ResearchMaterialProvider;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.marketdata.ProviderRoutePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Service
public class ResearchMaterialGateway {
    private final List<ResearchMaterialProvider> providers;
    private final ProviderRoutePolicy routePolicy;
    private final ProviderRequestGuard guard;
    private final ResearchMaterialCacheRepository cache;
    private final Duration cacheTtl;
    private final Duration sourceSnapshotTtl;
    private final Executor newsFetchExecutor;

    public ResearchMaterialGateway(List<ResearchMaterialProvider> providers,
                                   ProviderRoutePolicy routePolicy,
                                   ProviderRequestGuard guard) {
        this(providers, routePolicy, guard, ResearchMaterialCacheRepository.noop(), Runnable::run,
                Duration.ofMinutes(4), Duration.ofMinutes(2));
    }

    public ResearchMaterialGateway(List<ResearchMaterialProvider> providers,
                                   ProviderRoutePolicy routePolicy,
                                   ProviderRequestGuard guard,
                                   ResearchMaterialCacheRepository cache) {
        this(providers, routePolicy, guard, cache, Runnable::run, Duration.ofMinutes(4), Duration.ofMinutes(2));
    }

    @Autowired
    public ResearchMaterialGateway(List<ResearchMaterialProvider> providers,
                                   ProviderRoutePolicy routePolicy,
                                   ProviderRequestGuard guard,
                                   ResearchMaterialCacheRepository cache,
                                   @Qualifier("newsFetchExecutor") Executor newsFetchExecutor,
                                   @Value("${finscope.redis.cache.research-material-ttl-seconds:240}") long cacheTtlSeconds,
                                   @Value("${finscope.news.source-snapshot-ttl-seconds:120}") long sourceSnapshotTtlSeconds) {
        this(providers, routePolicy, guard, cache, newsFetchExecutor,
                Duration.ofSeconds(Math.max(1L, cacheTtlSeconds)),
                Duration.ofSeconds(Math.max(1L, sourceSnapshotTtlSeconds)));
    }

    private ResearchMaterialGateway(List<ResearchMaterialProvider> providers,
                                    ProviderRoutePolicy routePolicy,
                                    ProviderRequestGuard guard,
                                    ResearchMaterialCacheRepository cache,
                                    Executor newsFetchExecutor,
                                    Duration cacheTtl,
                                    Duration sourceSnapshotTtl) {
        this.providers = providers == null ? new ArrayList<ResearchMaterialProvider>()
                : new ArrayList<ResearchMaterialProvider>(providers);
        this.routePolicy = routePolicy;
        this.guard = guard;
        this.cache = cache == null ? ResearchMaterialCacheRepository.noop() : cache;
        this.cacheTtl = cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()
                ? Duration.ofMinutes(4) : cacheTtl;
        this.sourceSnapshotTtl = sourceSnapshotTtl == null || sourceSnapshotTtl.isNegative() || sourceSnapshotTtl.isZero()
                ? Duration.ofMinutes(2) : sourceSnapshotTtl;
        this.newsFetchExecutor = newsFetchExecutor == null ? Runnable::run : newsFetchExecutor;
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

    /**
     * 后台定时任务专用：并发拉取每个新闻来源，并独立保存最近一次成功快照。
     */
    public ResearchMaterialGatewayResult refreshNewsFlashSources(ResearchMaterialRequest request) {
        ResearchMaterialRequest normalized = request == null ? new ResearchMaterialRequest("000001", "", 50) : request;
        List<ResearchMaterialProvider> ordered = orderedProviders(ResearchMaterialType.NEWS_FLASH, normalized);
        if (ordered.isEmpty()) {
            return new ResearchMaterialGatewayResult(Collections.<ResearchMaterial>emptyList(),
                    Collections.singletonList("没有可用的实时资讯来源"));
        }
        List<CompletableFuture<SourceResult>> futures = new ArrayList<CompletableFuture<SourceResult>>();
        for (ResearchMaterialProvider provider : ordered) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> fetchSource(provider, ResearchMaterialType.NEWS_FLASH, normalized), newsFetchExecutor));
        }
        List<ResearchMaterial> materials = new ArrayList<ResearchMaterial>();
        List<String> warnings = new ArrayList<String>();
        Map<String, ResearchMaterial> unique = new LinkedHashMap<String, ResearchMaterial>();
        for (CompletableFuture<SourceResult> future : futures) {
            SourceResult result;
            try {
                result = future.join();
            } catch (CompletionException error) {
                warnings.add("资讯来源刷新任务异常：" + safe(error));
                continue;
            }
            warnings.addAll(result.warnings);
            for (ResearchMaterial material : result.materials) {
                if (valid(material)) unique.putIfAbsent(key(material), material);
            }
        }
        materials.addAll(unique.values());
        return new ResearchMaterialGatewayResult(materials, warnings);
    }

    /** 页面与雷达生产专用：只读取最近一次来源快照，绝不调用外部 Provider。 */
    public ResearchMaterialGatewayResult readNewsFlashSources(ResearchMaterialRequest request) {
        ResearchMaterialRequest normalized = request == null ? new ResearchMaterialRequest("000001", "", 50) : request;
        List<ResearchMaterialProvider> providers = newsProviders(normalized);
        Map<String, ResearchMaterial> unique = new LinkedHashMap<String, ResearchMaterial>();
        List<String> warnings = new ArrayList<String>();
        for (ResearchMaterialProvider provider : providers) {
            Optional<ResearchMaterialCacheEntry> cached = cache.get(sourceSnapshotKey(provider));
            if (!cached.isPresent()) {
                warnings.add(provider.providerCode() + "：正在等待后台同步");
                continue;
            }
            warnings.addAll(cached.get().getWarnings());
            for (ResearchMaterial material : cached.get().getMaterials()) {
                if (valid(material)) unique.putIfAbsent(key(material), material);
            }
        }
        if (unique.isEmpty() && warnings.isEmpty()) {
            warnings.add("正在同步资讯来源");
        }
        return new ResearchMaterialGatewayResult(new ArrayList<ResearchMaterial>(unique.values()), warnings);
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

    private SourceResult fetchSource(ResearchMaterialProvider provider, ResearchMaterialType type,
                                     ResearchMaterialRequest request) {
        try {
            ProviderResult<List<ResearchMaterial>> fetched = guard.execute(provider, "RESEARCH_MATERIAL_" + type.name(),
                    () -> provider.fetch(type, request));
            if (fetched == null || fetched.getData() == null) {
                return failedSource(provider, "返回空结果对象");
            }
            List<ResearchMaterial> values = new ArrayList<ResearchMaterial>();
            for (ResearchMaterial material : fetched.getData()) if (valid(material)) values.add(material);
            List<String> warnings = new ArrayList<String>(fetched.getWarnings());
            cache.put(sourceSnapshotKey(provider), new ResearchMaterialCacheEntry(values, warnings, LocalDateTime.now()),
                    sourceSnapshotTtl);
            return new SourceResult(values, warnings);
        } catch (RuntimeException error) {
            return failedSource(provider, safe(error));
        }
    }

    private SourceResult failedSource(ResearchMaterialProvider provider, String message) {
        List<String> warnings = new ArrayList<String>();
        warnings.add(provider.providerCode() + "：" + message);
        Optional<ResearchMaterialCacheEntry> cached = cache.get(sourceSnapshotKey(provider));
        if (cached.isPresent()) {
            warnings.add(provider.providerCode() + "：已使用最近一次成功快照");
            return new SourceResult(cached.get().getMaterials(), warnings);
        }
        return new SourceResult(Collections.<ResearchMaterial>emptyList(), warnings);
    }

    private List<ResearchMaterialProvider> orderedProviders(ResearchMaterialType type, ResearchMaterialRequest request) {
        return routePolicy.orderExternal(providers, "RESEARCH_MATERIAL_" + type.name(),
                provider -> provider.supports(type, request));
    }

    private List<ResearchMaterialProvider> newsProviders(ResearchMaterialRequest request) {
        List<ResearchMaterialProvider> ordered = new ArrayList<ResearchMaterialProvider>(
                orderedProviders(ResearchMaterialType.NEWS_FLASH, request));
        for (ResearchMaterialProvider provider : providers) {
            if (provider.supports(ResearchMaterialType.NEWS_FLASH, request) && !ordered.contains(provider)) {
                ordered.add(provider);
            }
        }
        return ordered;
    }

    private String sourceSnapshotKey(ResearchMaterialProvider provider) {
        return "finscope:news-source:" + provider.providerCode();
    }

    private static final class SourceResult {
        private final List<ResearchMaterial> materials;
        private final List<String> warnings;

        private SourceResult(List<ResearchMaterial> materials, List<String> warnings) {
            this.materials = materials == null ? Collections.<ResearchMaterial>emptyList() : materials;
            this.warnings = warnings == null ? Collections.<String>emptyList() : warnings;
        }
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
