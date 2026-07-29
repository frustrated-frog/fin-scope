package com.finscope.service.research.agent.tool;

import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.domain.research.ResearchSearchEvidence;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.research.material.ResearchMaterialGateway;
import com.finscope.service.research.material.ResearchMaterialGatewayResult;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 将公告、互动问答、研报和快讯统一写入运行级研究证据域。 */
@Component
public class ResearchMaterialSearchTool implements ResearchAgentTool {
    private static final Set<String> ARGUMENTS = new HashSet<String>(
            Arrays.asList("stockCode", "materialType", "query"));
    private static final int RESULT_LIMIT = 30;

    private final ResearchMaterialGateway gateway;
    private final ResearchSearchEvidenceRepository evidenceRepository;
    private final ResearchEvidenceAcquisitionService acquisitionService;

    public ResearchMaterialSearchTool(ResearchMaterialGateway gateway,
                                      ResearchSearchEvidenceRepository evidenceRepository) {
        this(gateway, evidenceRepository, null);
    }

    @Autowired
    public ResearchMaterialSearchTool(ResearchMaterialGateway gateway,
                                      ResearchSearchEvidenceRepository evidenceRepository,
                                      ResearchEvidenceAcquisitionService acquisitionService) {
        this.gateway = gateway;
        this.evidenceRepository = evidenceRepository;
        this.acquisitionService = acquisitionService;
    }

    @Override
    public ResearchToolDescriptor descriptor() {
        ResearchToolDescriptor value = new ResearchToolDescriptor();
        value.setCode("research_material_search");
        value.setName("结构化研究资料检索");
        value.setDescription("从公告、互动问答、研报和财经快讯等结构化来源检索材料，并写入本次研究证据域");
        Map<String, String> input = new LinkedHashMap<String, String>();
        input.put("stockCode", "六位 A 股代码");
        input.put("materialType", "ANNOUNCEMENT|INTERACTION|BROKER_REPORT|NEWS_FLASH");
        input.put("query", "0..100字符检索关键词，可为空字符串");
        value.setInputSchema(input);
        value.setOutputSchema(Collections.singletonMap("observation", "本次研究新增结构化证据与独立来源"));
        value.setTimeoutMs(45_000);
        value.setReadOnly(true);
        value.setParallelizable(true);
        value.setRiskLevel("LOW");
        value.setBudgetType("EXTERNAL_ACTION");
        return value;
    }

    @Override
    public void validate(Map<String, Object> arguments) {
        if (arguments == null || !arguments.keySet().equals(ARGUMENTS)) {
            throw new IllegalArgumentException("结构化资料检索参数必须且只能包含 stockCode、materialType 和 query");
        }
        String stockCode = text(arguments.get("stockCode"));
        String query = text(arguments.get("query"));
        if (!stockCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("结构化资料检索仅支持六位 A 股代码");
        }
        if (query.length() > 100 || query.contains("://")) {
            throw new IllegalArgumentException("结构化资料检索 query 未通过安全校验");
        }
        parseType(arguments.get("materialType"));
    }

    @Override
    public ResearchToolObservation execute(ResearchAgentToolContext context, Map<String, Object> arguments) {
        validate(arguments);
        ResearchMaterialType type = parseType(arguments.get("materialType"));
        String stockCode = text(arguments.get("stockCode"));
        String query = text(arguments.get("query"));
        try {
            Set<String> existingSources = new HashSet<String>();
            for (ResearchSearchEvidence evidence : evidenceRepository.findByRunId(context.getResearchRunId())) {
                String source = sourceIdentity(evidence.getSourceDomain(), evidence.getProvider());
                if (!source.isEmpty()) existingSources.add(source);
            }
            ResearchMaterialGatewayResult result = gateway.search(
                    type, new ResearchMaterialRequest(stockCode, query, RESULT_LIMIT));
            List<String> refs = new ArrayList<String>();
            Set<String> newSources = new HashSet<String>();
            int duplicates = 0;
            for (ResearchMaterial material : result.getMaterials()) {
                String url = text(material.getUrl());
                if (!url.isEmpty() && evidenceRepository.findByRunIdAndUrl(
                        context.getResearchRunId(), url).isPresent()) {
                    duplicates++;
                    continue;
                }
                ResearchSearchEvidence saved = evidenceRepository.save(toEvidence(context, query, type, material));
                if (saved.getId() != null) refs.add("search-evidence:" + saved.getId());
                String source = sourceIdentity(saved.getSourceDomain(), saved.getProvider());
                if (!source.isEmpty() && !existingSources.contains(source)) newSources.add(source);
            }
            ResearchToolObservation observation = new ResearchToolObservation();
            observation.setStatus(refs.isEmpty() ? "NO_PROGRESS" : "SUCCESS");
            observation.setEvidenceDelta(refs.size());
            observation.setSourceDelta(newSources.size());
            observation.setDataRefs(refs);
            observation.setRetryable(false);
            observation.setStateHash("material:" + type + ":" + refs.size() + ":" + newSources.size());
            observation.setObservationSummary("结构化资料检索完成：类型=" + type
                    + "，返回=" + result.getMaterials().size() + "，重复=" + duplicates
                    + "，新增证据=" + refs.size() + "，新增独立来源=" + newSources.size()
                    + warningSummary(result.getWarnings()));
            observation.setNewInformation(refs.isEmpty()
                    ? "本次查询没有获得新的运行内证据"
                    : "结构化材料已完整写入本次研究证据域");
            return observation;
        } catch (RuntimeException error) {
            return error(type, error);
        }
    }

    private ResearchSearchEvidence toEvidence(ResearchAgentToolContext context, String query,
                                               ResearchMaterialType type, ResearchMaterial material) {
        ResearchSearchEvidence value = new ResearchSearchEvidence();
        value.setResearchRunId(context.getResearchRunId());
        value.setDecisionId(context.getDecisionId());
        value.setProvider(text(material.getProviderCode()));
        value.setQueryText(query);
        value.setIntent(type == ResearchMaterialType.ANNOUNCEMENT || type == ResearchMaterialType.INTERACTION
                ? "PRIMARY" : "BREADTH");
        value.setTitle(text(material.getTitle()));
        value.setUrl(text(material.getUrl()));
        String structuredContent = text(material.getContent());
        ResearchEvidenceAcquisitionResult acquired = shouldReadFullText(type, value.getUrl())
                ? acquisitionService.acquire(value.getUrl(), query, structuredContent, text(material.getStockCode()))
                : null;
        value.setContent(acquired == null ? structuredContent : acquired.getContent());
        value.setSearchSnippet(acquired == null ? structuredContent : acquired.getSearchSnippet());
        value.setContentOrigin(acquired == null ? "STRUCTURED_MATERIAL" : acquired.getContentOrigin());
        value.setExtractionMethod(acquired == null
                ? "STRUCTURED:" + text(material.getProviderCode()) + ":" + type.name()
                : acquired.getExtractionMethod());
        value.setFetchStatus(acquired == null ? "FETCHED" : acquired.getFetchStatus());
        value.setContentCharCount(acquired == null ? value.getContent().length() : acquired.getContentCharCount());
        value.setFetchedAt(LocalDateTime.now());
        value.setSourceDomain(domain(value.getUrl(), material.getProviderFamily()));
        value.setSourceTier(text(material.getSourceTier()));
        value.setRelevanceScore("T1".equalsIgnoreCase(value.getSourceTier()) ? 1.0D : 0.85D);
        value.setPublishedAt(material.getPublishedAt() == null ? "" : material.getPublishedAt().toString());
        return value;
    }

    private boolean shouldReadFullText(ResearchMaterialType type, String url) {
        return acquisitionService != null && !text(url).isEmpty()
                && (type == ResearchMaterialType.ANNOUNCEMENT || type == ResearchMaterialType.BROKER_REPORT);
    }

    private ResearchToolObservation error(ResearchMaterialType type, RuntimeException error) {
        ResearchToolObservation value = new ResearchToolObservation();
        value.setStatus("RETRYABLE_ERROR");
        value.setErrorType("RESEARCH_MATERIAL_SEARCH_FAILED");
        value.setRetryable(true);
        value.setEvidenceDelta(0);
        value.setSourceDelta(0);
        value.setStateHash("material:error:" + type);
        value.setObservationSummary("结构化资料检索失败：" + safe(error.getMessage()));
        value.setNewInformation("没有写入新的研究证据");
        return value;
    }

    private ResearchMaterialType parseType(Object value) {
        try {
            return ResearchMaterialType.valueOf(text(value));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("结构化资料检索 materialType 未通过安全校验");
        }
    }

    private String domain(String url, String fallback) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? text(fallback) : host.toLowerCase();
        } catch (RuntimeException ignored) {
            return text(fallback);
        }
    }

    private String sourceIdentity(String domain, String provider) {
        String value = text(domain);
        return value.isEmpty() ? text(provider).toLowerCase() : value.toLowerCase();
    }

    private String warningSummary(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return "";
        return "，降级提示=" + warnings.size() + "（" + safe(warnings.get(0)) + "）";
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String safe(String value) {
        String normalized = text(value).replaceAll("[\\r\\n\\t]+", " ");
        if (normalized.isEmpty()) return "未知错误";
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
    }
}
