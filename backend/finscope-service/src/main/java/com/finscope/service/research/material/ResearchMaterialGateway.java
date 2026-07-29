package com.finscope.service.research.material;

import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import com.finscope.rpc.research.material.ResearchMaterialProvider;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.marketdata.ProviderRoutePolicy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResearchMaterialGateway {
    private final List<ResearchMaterialProvider> providers;
    private final ProviderRoutePolicy routePolicy;
    private final ProviderRequestGuard guard;

    public ResearchMaterialGateway(List<ResearchMaterialProvider> providers,
                                   ProviderRoutePolicy routePolicy,
                                   ProviderRequestGuard guard) {
        this.providers = providers == null ? new ArrayList<ResearchMaterialProvider>()
                : new ArrayList<ResearchMaterialProvider>(providers);
        this.routePolicy = routePolicy;
        this.guard = guard;
    }

    public ResearchMaterialGatewayResult search(ResearchMaterialType type, ResearchMaterialRequest request) {
        String capability = "RESEARCH_MATERIAL_" + type.name();
        List<ResearchMaterialProvider> ordered = routePolicy.orderExternal(
                providers, capability, provider -> provider.supports(type, request));
        Map<String, ResearchMaterial> unique = new LinkedHashMap<String, ResearchMaterial>();
        List<String> warnings = new ArrayList<String>();
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
