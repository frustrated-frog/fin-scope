package com.finscope.service.research.material;

import com.finscope.domain.financials.BrokerResearchCandidate;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.research.material.ResearchMaterialProvider;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.financials.BrokerResearchSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class BrokerReportResearchMaterialProvider implements ResearchMaterialProvider {
    private final BrokerResearchSource source;

    public BrokerReportResearchMaterialProvider(BrokerResearchSource source) {
        this.source = source;
    }

    @Autowired
    public BrokerReportResearchMaterialProvider(List<BrokerResearchSource> sources) {
        this(select(sources));
    }

    private static BrokerResearchSource select(List<BrokerResearchSource> sources) {
        if (sources != null) {
            for (BrokerResearchSource source : sources) {
                if (source != null && "EASTMONEY".equals(source.sourceCode())) return source;
            }
        }
        throw new IllegalArgumentException("缺少可用的东财研报来源");
    }

    @Override public String providerCode() { return "EASTMONEY_BROKER_REPORT"; }
    @Override public String providerFamily() { return "EASTMONEY"; }
    @Override public int priority() { return 10; }
    @Override public int batchLimit() { return 50; }
    @Override public Duration minimumInterval() { return Duration.ofSeconds(1); }
    @Override public Duration timeout() { return Duration.ofSeconds(20); }
    @Override public Set<ResearchMaterialType> materialTypes() { return Collections.singleton(ResearchMaterialType.BROKER_REPORT); }

    @Override
    public ProviderResult<List<ResearchMaterial>> fetch(ResearchMaterialType type, ResearchMaterialRequest request) {
        if (!supports(type, request)) {
            throw new ProviderContractException("UNSUPPORTED_CAPABILITY", "研报来源不支持该资料类型", false);
        }
        LocalDate today = LocalDate.now();
        List<BrokerResearchCandidate> candidates = source.list(
                request.getStockCode(), today.minusYears(2), today, request.getLimit());
        List<ResearchMaterial> result = new ArrayList<ResearchMaterial>();
        for (BrokerResearchCandidate item : candidates) {
            if (item == null || blank(item.getTitle()) || !matches(request.getQuery(), item.getTitle())) continue;
            ResearchMaterial value = new ResearchMaterial();
            value.setMaterialType(type); value.setExternalId(item.getExternalId());
            value.setStockCode(request.getStockCode()); value.setTitle(item.getTitle());
            value.setContent("机构：" + text(item.getInstitution()) + "；评级：" + text(item.getRating()));
            value.setUrl(item.getSourceUrl());
            if (item.getPublishedDate() != null) value.setPublishedAt(item.getPublishedDate().atStartOfDay());
            value.setProviderCode(source.sourceCode()); value.setProviderFamily(providerFamily()); value.setSourceTier("T2");
            result.add(value);
        }
        return ProviderResult.of(result, LocalDateTime.now(), ProviderResult.hashOf(result), Collections.emptyList());
    }

    private boolean matches(String query, String value) { return blank(query) || value.contains(query); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private String text(String value) { return blank(value) ? "未提供" : value.trim(); }
}
