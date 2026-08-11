package com.finscope.service.industrychain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.industrychain.IndustryChainEdge;
import com.finscope.domain.industrychain.IndustryChainEvidence;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainNode;
import com.finscope.domain.industrychain.IndustryChainResearchContent;
import com.finscope.rpc.llm.LlmChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 将冻结的公开证据严格归纳为可发布的产业链图谱。 */
@Service
public class IndustryChainSynthesisAgent {
    private static final Logger log = LoggerFactory.getLogger(IndustryChainSynthesisAgent.class);
    private static final int PRIMARY_TIMEOUT_MS = 90_000;
    private static final int REPAIR_TIMEOUT_MS = 60_000;
    private static final int MAX_OUTPUT_TOKENS = 9000;
    private static final int MAX_EVIDENCE_EXCERPT = 3000;
    private static final int MAX_LIMITATIONS_LENGTH = 1600;
    private static final String REMOVED_SUPPLY_NOTICE = "未明确披露的企业供销关系已从图谱中移除。";
    private static final Set<String> ROOT_FIELDS = set("summary", "limitations", "researchContent", "nodes", "edges");
    private static final Set<String> NODE_FIELDS = set("nodeKey", "type", "name", "description",
            "stageOrder", "stockCode", "confidence", "evidenceRefs");
    private static final Set<String> EDGE_FIELDS = set("edgeKey", "sourceKey", "targetKey", "type",
            "nature", "description", "confidence", "evidenceRefs");
    private static final Set<String> RESEARCH_FIELDS = set("overview", "stageProfiles", "companyProfiles");
    private static final Set<String> OVERVIEW_FIELDS = set("lifecycle", "prosperity", "supplyDemand", "cycleType",
            "demandDrivers", "supplyDrivers", "keyVariables", "bottlenecks", "overcapacityRisks", "trendTags");
    private static final Set<String> STAGE_PROFILE_FIELDS = set("nodeKey", "roleSummary", "businessModel",
            "costStructure", "valueCapture", "bottleneck", "prosperity", "supplyDemand", "lifecycle",
            "profitDrivers", "barriers", "coreMetrics", "risks", "keyVariables", "trendTags");
    private static final Set<String> COMPANY_PROFILE_FIELDS = set("nodeKey", "industryPosition", "coreProducts",
            "downstreamMarkets", "competitiveAdvantages", "keyVariables");
    private static final Set<String> LIFECYCLES = set("EMERGING", "GROWTH", "MATURE", "CONSOLIDATING", "DECLINING");
    private static final Set<String> PROSPERITY = set("RISING", "STABLE", "COOLING", "MIXED");
    private static final Set<String> SUPPLY_DEMAND = set("TIGHT", "BALANCED", "LOOSE", "STRUCTURAL");

    private final LlmChatClient llm;
    private final ObjectMapper objectMapper;
    private final IndustryChainGraphValidator validator;

    public IndustryChainSynthesisAgent(LlmChatClient llm, ObjectMapper objectMapper,
                                       IndustryChainGraphValidator validator) {
        this.llm = llm;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public IndustryChainGraph synthesize(String chainName,
                                         List<IndustryChainEvidence> evidence) throws Exception {
        if (evidence == null || evidence.isEmpty()) {
            throw new IllegalArgumentException("产业链归纳缺少公开证据");
        }
        if (llm == null || !llm.isConfigured()) {
            throw new IllegalStateException("产业链归纳模型暂不可用");
        }
        String input = objectMapper.writeValueAsString(payload(chainName, evidence));
        String raw = llm.complete(systemPrompt(), input, PRIMARY_TIMEOUT_MS, MAX_OUTPUT_TOKENS);
        try {
            return parse(raw, chainName, evidence);
        } catch (Exception firstError) {
            log.info("Industry-chain graph output rejected before repair: chain={}, reason={}",
                    chainName, compact(firstError.getMessage(), 200));
            String repaired = llm.complete(repairPrompt(), repairInput(input, raw, firstError),
                    REPAIR_TIMEOUT_MS, MAX_OUTPUT_TOKENS);
            return parse(repaired, chainName, evidence);
        }
    }

    private IndustryChainGraph parse(String raw, String chainName,
                                     List<IndustryChainEvidence> evidence) throws Exception {
        JsonNode root = objectMapper.readTree(extractJson(raw));
        validateFields(root, ROOT_FIELDS);
        IndustryChainGraph graph = new IndustryChainGraph();
        graph.setName(chainName);
        graph.setSummary(required(root, "summary", 800));
        graph.setLimitations(required(root, "limitations", MAX_LIMITATIONS_LENGTH));
        graph.setResearchContent(researchContent(root.path("researchContent")));
        graph.setNodes(nodes(root.path("nodes")));
        graph.setEdges(edges(root.path("edges")));
        graph.setEvidence(evidence);
        graph.setSchemaVersion("INDUSTRY_CHAIN_V2");
        graph.setModel(llm.modelName());
        graph.setGeneratedAt(LocalDateTime.now());
        removeUndisclosedSupplyRelationships(graph);
        return validator.validate(graph);
    }

    private void removeUndisclosedSupplyRelationships(IndustryChainGraph graph) {
        int removed = 0;
        Iterator<IndustryChainEdge> iterator = graph.getEdges().iterator();
        while (iterator.hasNext()) {
            IndustryChainEdge edge = iterator.next();
            if ("SUPPLIES_TO".equals(edge.getType()) && !"DISCLOSED".equals(edge.getNature())) {
                iterator.remove();
                removed++;
            }
        }
        if (removed == 0) {
            return;
        }
        String limitations = graph.getLimitations();
        if (!limitations.contains(REMOVED_SUPPLY_NOTICE)) {
            String separator = limitations.endsWith("。") || limitations.endsWith("；") ? "" : "；";
            String suffix = separator + REMOVED_SUPPLY_NOTICE;
            int prefixLength = Math.min(limitations.length(), MAX_LIMITATIONS_LENGTH - suffix.length());
            graph.setLimitations(limitations.substring(0, prefixLength) + suffix);
        }
        log.info("Removed undisclosed supply relationships before graph validation: chain={}, count={}",
                graph.getName(), removed);
    }

    private List<IndustryChainNode> nodes(JsonNode values) {
        if (!values.isArray() || values.size() < 3 || values.size() > 80) {
            throw new IllegalArgumentException("nodes 必须包含 3 至 80 个节点");
        }
        List<IndustryChainNode> result = new ArrayList<IndustryChainNode>();
        for (JsonNode value : values) {
            validateFields(value, NODE_FIELDS);
            IndustryChainNode node = new IndustryChainNode();
            node.setNodeKey(required(value, "nodeKey", 100));
            node.setType(required(value, "type", 30));
            node.setName(required(value, "name", 100));
            node.setDescription(required(value, "description", 600));
            node.setStageOrder(optionalInteger(value, "stageOrder"));
            node.setStockCode(optionalText(value, "stockCode", 30));
            node.setConfidence(required(value, "confidence", 20));
            node.setEvidenceRefs(refs(value.path("evidenceRefs")));
            result.add(node);
        }
        return result;
    }

    private List<IndustryChainEdge> edges(JsonNode values) {
        if (!values.isArray() || values.size() < 2 || values.size() > 160) {
            throw new IllegalArgumentException("edges 必须包含 2 至 160 条关系");
        }
        List<IndustryChainEdge> result = new ArrayList<IndustryChainEdge>();
        for (JsonNode value : values) {
            validateFields(value, EDGE_FIELDS);
            IndustryChainEdge edge = new IndustryChainEdge();
            edge.setEdgeKey(required(value, "edgeKey", 120));
            edge.setSourceKey(required(value, "sourceKey", 100));
            edge.setTargetKey(required(value, "targetKey", 100));
            edge.setType(required(value, "type", 30));
            edge.setNature(required(value, "nature", 30));
            edge.setDescription(required(value, "description", 600));
            edge.setConfidence(required(value, "confidence", 20));
            edge.setEvidenceRefs(refs(value.path("evidenceRefs")));
            result.add(edge);
        }
        return result;
    }

    private IndustryChainResearchContent researchContent(JsonNode value) {
        validateFields(value, RESEARCH_FIELDS);
        IndustryChainResearchContent content = new IndustryChainResearchContent();
        content.setOverview(overview(value.path("overview")));
        content.setStageProfiles(stageProfiles(value.path("stageProfiles")));
        content.setCompanyProfiles(companyProfiles(value.path("companyProfiles")));
        return content;
    }

    private IndustryChainResearchContent.Overview overview(JsonNode value) {
        validateFields(value, OVERVIEW_FIELDS);
        IndustryChainResearchContent.Overview result = new IndustryChainResearchContent.Overview();
        result.setLifecycle(enumValue(value, "lifecycle", LIFECYCLES));
        result.setProsperity(enumValue(value, "prosperity", PROSPERITY));
        result.setSupplyDemand(enumValue(value, "supplyDemand", SUPPLY_DEMAND));
        result.setCycleType(required(value, "cycleType", 160));
        result.setDemandDrivers(phrases(value, "demandDrivers"));
        result.setSupplyDrivers(phrases(value, "supplyDrivers"));
        result.setKeyVariables(phrases(value, "keyVariables"));
        result.setBottlenecks(phrases(value, "bottlenecks"));
        result.setOvercapacityRisks(phrases(value, "overcapacityRisks"));
        result.setTrendTags(phrases(value, "trendTags"));
        return result;
    }

    private List<IndustryChainResearchContent.StageProfile> stageProfiles(JsonNode values) {
        if (!values.isArray() || values.size() == 0 || values.size() > 40) {
            throw new IllegalArgumentException("stageProfiles 必须包含 1 至 40 个环节画像");
        }
        List<IndustryChainResearchContent.StageProfile> result = new ArrayList<IndustryChainResearchContent.StageProfile>();
        for (JsonNode value : values) {
            validateFields(value, STAGE_PROFILE_FIELDS);
            IndustryChainResearchContent.StageProfile profile = new IndustryChainResearchContent.StageProfile();
            profile.setNodeKey(required(value, "nodeKey", 100));
            profile.setRoleSummary(required(value, "roleSummary", 240));
            profile.setBusinessModel(required(value, "businessModel", 240));
            profile.setCostStructure(required(value, "costStructure", 240));
            profile.setValueCapture(required(value, "valueCapture", 240));
            profile.setBottleneck(required(value, "bottleneck", 240));
            profile.setProsperity(enumValue(value, "prosperity", PROSPERITY));
            profile.setSupplyDemand(enumValue(value, "supplyDemand", SUPPLY_DEMAND));
            profile.setLifecycle(enumValue(value, "lifecycle", LIFECYCLES));
            profile.setProfitDrivers(phrases(value, "profitDrivers"));
            profile.setBarriers(phrases(value, "barriers"));
            profile.setCoreMetrics(phrases(value, "coreMetrics"));
            profile.setRisks(phrases(value, "risks"));
            profile.setKeyVariables(phrases(value, "keyVariables"));
            profile.setTrendTags(phrases(value, "trendTags"));
            result.add(profile);
        }
        return result;
    }

    private List<IndustryChainResearchContent.CompanyProfile> companyProfiles(JsonNode values) {
        if (!values.isArray() || values.size() > 40) {
            throw new IllegalArgumentException("companyProfiles 必须是最多 40 项的数组");
        }
        List<IndustryChainResearchContent.CompanyProfile> result = new ArrayList<IndustryChainResearchContent.CompanyProfile>();
        for (JsonNode value : values) {
            validateFields(value, COMPANY_PROFILE_FIELDS);
            IndustryChainResearchContent.CompanyProfile profile = new IndustryChainResearchContent.CompanyProfile();
            profile.setNodeKey(required(value, "nodeKey", 100));
            profile.setIndustryPosition(required(value, "industryPosition", 240));
            profile.setCoreProducts(phrases(value, "coreProducts"));
            profile.setDownstreamMarkets(phrases(value, "downstreamMarkets"));
            profile.setCompetitiveAdvantages(phrases(value, "competitiveAdvantages"));
            profile.setKeyVariables(phrases(value, "keyVariables"));
            result.add(profile);
        }
        return result;
    }

    private String enumValue(JsonNode node, String field, Set<String> allowed) {
        String value = required(node, field, 30);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(field + " 枚举值无效");
        }
        return value;
    }

    private List<String> phrases(JsonNode node, String field) {
        JsonNode values = node.path(field);
        if (!values.isArray() || values.size() > 6) {
            throw new IllegalArgumentException(field + " 必须是最多 6 项的数组");
        }
        Set<String> unique = new LinkedHashSet<String>();
        for (JsonNode value : values) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException(field + " 包含非文本内容");
            }
            String phrase = value.asText().trim();
            if (phrase.isEmpty() || phrase.length() > 100) {
                throw new IllegalArgumentException(field + " 包含无效短语");
            }
            unique.add(phrase);
        }
        return new ArrayList<String>(unique);
    }

    private List<String> refs(JsonNode values) {
        if (!values.isArray() || values.size() == 0) {
            throw new IllegalArgumentException("图谱节点或关系必须引用证据");
        }
        Set<String> result = new LinkedHashSet<String>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().trim().isEmpty()) {
                throw new IllegalArgumentException("证据引用格式无效");
            }
            result.add(value.asText().trim());
        }
        return new ArrayList<String>(result);
    }

    private Map<String, Object> payload(String chainName, List<IndustryChainEvidence> evidence) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("chainName", chainName);
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        for (IndustryChainEvidence item : evidence) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("evidenceCode", item.getEvidenceCode());
            row.put("title", item.getTitle());
            row.put("source", item.getSource());
            row.put("sourceTier", item.getSourceTier());
            row.put("publishedAt", item.getPublishedAt());
            row.put("content", compact(item.getExcerpt(), MAX_EVIDENCE_EXCERPT));
            rows.add(row);
        }
        result.put("evidence", rows);
        return result;
    }

    private String systemPrompt() {
        return "你是谨慎的产业研究员。只依据输入 evidence 输出一个 JSON 对象，不要 markdown。"
                + "根字段只能是 summary、limitations、researchContent、nodes、edges。节点字段必须完整且只能为 nodeKey、type、"
                + "name、description、stageOrder、stockCode、confidence、evidenceRefs；关系字段必须完整且只能为"
                + "edgeKey、sourceKey、targetKey、type、nature、description、confidence、evidenceRefs。"
                + "node.type 只能是 INDUSTRY_CHAIN、STAGE、PRODUCT、COMPANY；edge.type 只能是 "
                + "CONTAINS_STAGE、FLOWS_TO、BELONGS_TO_STAGE、INPUT_TO、PRODUCES、PARTICIPATES_IN、SUPPLIES_TO；"
                + "nature 只能是 DISCLOSED、INDUSTRY_LOGIC、INFERRED；confidence 只能是 HIGH、MEDIUM、LOW。"
                + "至少生成三个按 stageOrder 排序并由 FLOWS_TO 连通的"
                + "STAGE。每个节点与关系至少引用一个输入中存在的 evidenceCode。行业通用关系使用 INDUSTRY_LOGIC，"
                + "推断使用 INFERRED；具体企业之间的 SUPPLIES_TO 只能在公开资料明确披露时使用 DISCLOSED，"
                + "否则不得生成。不得猜测匿名客户、供应商或合同，不得输出投资建议。"
                + researchPrompt();
    }

    private String researchPrompt() {
        return "researchContent 必须且只能包含 overview、stageProfiles、companyProfiles。overview 必须输出"
                + "lifecycle、prosperity、supplyDemand、cycleType、demandDrivers、supplyDrivers、keyVariables、"
                + "bottlenecks、overcapacityRisks、trendTags，用于表达景气度、供需状态、核心指标、产业瓶颈。"
                + "lifecycle 只能为 EMERGING、GROWTH、MATURE、CONSOLIDATING、DECLINING；prosperity 只能为"
                + "RISING、STABLE、COOLING、MIXED；supplyDemand 只能为 TIGHT、BALANCED、LOOSE、STRUCTURAL。"
                + "每个 STAGE 都应有 stageProfiles 画像，字段只能为 nodeKey、roleSummary、businessModel、"
                + "costStructure、valueCapture、bottleneck、prosperity、supplyDemand、lifecycle、profitDrivers、"
                + "barriers、coreMetrics、risks、keyVariables、trendTags。每个 COMPANY 都应有 companyProfiles"
                + "公司竞争格局，字段只能为 nodeKey、industryPosition、coreProducts、downstreamMarkets、"
                + "competitiveAdvantages、keyVariables。画像 nodeKey 必须引用对应类型节点；列表最多 6 项。"
                + "未知列表输出 []，未知短文本输出‘待观察’，不要补造价格、份额、客户或产能数字。";
    }

    private String repairPrompt() {
        return systemPrompt() + "上一次输出未通过结构或证据校验。根据 validationError 修正 invalidOutput，"
                + "事实仍只能来自 originalInput.evidence，只返回修正后的 JSON。";
    }

    private String repairInput(String input, String invalid, Exception error) throws Exception {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("validationError", compact(error.getMessage(), 500));
        result.put("invalidOutput", compact(invalid, 16000));
        result.put("originalInput", objectMapper.readTree(input));
        return objectMapper.writeValueAsString(result);
    }

    private void validateFields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("产业链模型输出必须是 JSON 对象");
        }
        Set<String> actual = new HashSet<String>();
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            actual.add(fields.next());
        }
        if (!actual.equals(allowed)) {
            throw new IllegalArgumentException("产业链模型输出字段不符合契约");
        }
    }

    private String required(JsonNode node, String field, int maxLength) {
        String value = optionalText(node, field, maxLength);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " 缺失或超过长度限制");
        }
        return value;
    }

    private String optionalText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " 类型无效");
        }
        String text = value.asText().trim();
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(field + " 超过长度限制");
        }
        return text;
    }

    private Integer optionalInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.canConvertToInt()) {
            throw new IllegalArgumentException(field + " 类型无效");
        }
        return value.asInt();
    }

    private String extractJson(String raw) {
        String value = raw == null ? "" : raw.trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("产业链模型未返回 JSON 对象");
        }
        return value.substring(start, end + 1);
    }

    private String compact(String value, int maxLength) {
        String text = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static Set<String> set(String... values) {
        return java.util.Collections.unmodifiableSet(
                new LinkedHashSet<String>(Arrays.asList(values)));
    }
}
