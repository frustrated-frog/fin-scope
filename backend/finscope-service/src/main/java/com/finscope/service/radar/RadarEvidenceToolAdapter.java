package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarEvidencePlan;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.research.material.ResearchMaterialGateway;
import com.finscope.service.research.material.ResearchMaterialGatewayResult;
import com.finscope.service.search.evidence.SearchDepth;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class RadarEvidenceToolAdapter {
    private final ResearchMaterialGateway materials;
    private final SearchEvidenceGateway search;

    public RadarEvidenceToolAdapter(ResearchMaterialGateway materials, SearchEvidenceGateway search) {
        this.materials = materials; this.search = search;
    }

    public ToolResult execute(RadarEvidencePlan.Action action) {
        try {
            if ("research_material_search".equals(action.getToolCode())) return material(action);
            if ("public_news_search".equals(action.getToolCode())) return publicSearch(action);
            return ToolResult.failure("工具不在个人雷达白名单内");
        } catch (RuntimeException error) {
            return ToolResult.failure("证据检索失败：" + safe(error.getMessage()));
        }
    }

    private ToolResult material(RadarEvidencePlan.Action action) {
        ResearchMaterialType type = ResearchMaterialType.valueOf(action.getMaterialType());
        ResearchMaterialGatewayResult result = materials.search(type,
                new ResearchMaterialRequest(action.getStockCode(), action.getQuery(), 6));
        List<RadarEvidence> values = new ArrayList<RadarEvidence>();
        for (ResearchMaterial item : result.getMaterials()) { RadarEvidence value = new RadarEvidence();
            value.setToolCode(action.getToolCode()); value.setEvidenceType(type.name()); value.setTitle(item.getTitle());
            value.setSummary(compact(item.getContent(), 500)); value.setUrl(item.getUrl()); value.setSourceName(item.getProviderCode());
            value.setSourceTier(item.getSourceTier()); value.setPublishedAt(item.getPublishedAt()); values.add(value); }
        return new ToolResult(values, result.getWarnings());
    }

    private ToolResult publicSearch(RadarEvidencePlan.Action action) {
        if (search == null || !search.isConfigured(SearchDepth.QUICK)) return ToolResult.failure("公开搜索尚未配置");
        SearchEvidenceBatch batch = search.search(new SearchEvidenceRequest(action.getQuery(), SearchDepth.QUICK,
                4, 6, "cn", "zh", 12_000));
        if (batch.isAllProvidersFailed()) return ToolResult.failure("公开搜索供应商均不可用");
        List<RadarEvidence> values = new ArrayList<RadarEvidence>();
        for (SearchEvidence item : batch.getEvidence()) { RadarEvidence value = new RadarEvidence();
            value.setToolCode(action.getToolCode()); value.setEvidenceType("PUBLIC_NEWS"); value.setTitle(item.getTitle());
            value.setSummary(compact(item.getContent(), 500)); value.setUrl(item.getUrl()); value.setSourceName(item.getSourceDomain());
            value.setSourceTier(item.getSourceTier()); value.setPublishedAt(parseTime(item.getPublishedAt())); values.add(value); }
        return ToolResult.success(values);
    }

    private LocalDateTime parseTime(String value) { try { return value==null||value.trim().isEmpty()?null:LocalDateTime.parse(value); } catch(DateTimeParseException ignored){return null;} }
    private String compact(String value,int max){String v=value==null?"":value.replaceAll("[\\r\\n\\t]+"," ").trim();return v.length()<=max?v:v.substring(0,max);}
    private String safe(String value){return compact(value==null?"未知错误":value,180);}

    public static final class ToolResult {
        private final List<RadarEvidence> evidence;
        private final List<String> warnings;
        ToolResult(List<RadarEvidence> evidence,List<String>warnings){this.evidence=evidence==null?Collections.<RadarEvidence>emptyList():evidence;this.warnings=warnings==null?Collections.<String>emptyList():warnings;}
        public static ToolResult success(List<RadarEvidence> values){return new ToolResult(values,Collections.<String>emptyList());}
        public static ToolResult failure(String warning){return new ToolResult(Collections.<RadarEvidence>emptyList(),Collections.singletonList(warning));}
        public List<RadarEvidence> getEvidence(){return evidence;} public List<String> getWarnings(){return warnings;}
    }
}
