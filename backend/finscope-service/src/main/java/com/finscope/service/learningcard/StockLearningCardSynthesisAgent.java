package com.finscope.service.learningcard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.learningcard.StockLearningCardClaim;
import com.finscope.domain.learningcard.StockLearningCardEvidence;
import com.finscope.domain.learningcard.StockLearningCardSection;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StockLearningCardSynthesisAgent {
    private static final Set<String> RATINGS = immutableSet(
            "HIGH", "MEDIUM_HIGH", "MEDIUM", "MEDIUM_LOW", "LOW", "UNKNOWN");
    private static final Set<String> CONFIDENCE = immutableSet("HIGH", "MEDIUM", "LOW");
    private static final Set<String> VERIFICATION = immutableSet(
            "SUPPORTED", "PARTIALLY_SUPPORTED", "UNVERIFIED", "CONTRADICTED");
    private final LlmChatClient llm;
    private final ObjectMapper json;

    public StockLearningCardSynthesisAgent(LlmChatClient llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
    }

    public StockLearningCardClaim synthesize(String companyName, String companyCode, String dimension,
                                             List<StockLearningCardEvidence> evidence) throws Exception {
        StockLearningDimensionSchema schema = StockLearningFramework.schemaFor(dimension);
        if (evidence == null || evidence.isEmpty()) {
            return insufficient(schema, "没有检索到足够的公开资料");
        }
        if (llm == null || !llm.isConfigured()) {
            return insufficient(schema, "模型暂不可用，已保留公开资料供后续重试");
        }
        String raw = llm.complete(systemPrompt(), json.writeValueAsString(payload(
                companyName, companyCode, schema, evidence)), 20_000, 1800);
        JsonNode root = json.readTree(extractJson(raw));
        validateFields(root, immutableSet("headline", "ratingValue", "sections", "confidence"));
        StockLearningCardClaim claim = new StockLearningCardClaim();
        claim.setDimensionCode(dimension);
        claim.setStatus("READY");
        claim.setHeadline(required(root, "headline", 260));
        claim.setRatingLabel(schema.getRatingLabel());
        claim.setRatingValue(enumValue(root, "ratingValue", RATINGS));
        claim.setConfidence(enumValue(root, "confidence", CONFIDENCE));
        claim.setSections(parseSections(root.get("sections"), schema, evidence));
        validateTradingLanguage(claim);
        return claim;
    }

    private List<StockLearningCardSection> parseSections(JsonNode node, StockLearningDimensionSchema schema,
                                                         List<StockLearningCardEvidence> evidence) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("sections 必须是数组");
        }
        Set<String> availableEvidence = new HashSet<String>();
        for (StockLearningCardEvidence item : evidence) {
            availableEvidence.add(item.getEvidenceCode());
        }
        Set<String> seen = new LinkedHashSet<String>();
        List<StockLearningCardSection> sections = new ArrayList<StockLearningCardSection>();
        int previousOrder = -1;
        for (JsonNode value : node) {
            validateFields(value, immutableSet("key", "title", "content", "evidenceRefs", "verificationStatus"));
            String key = required(value, "key", 80);
            StockLearningDimensionSchema.SectionDefinition definition = schema.section(key);
            if (definition == null || !seen.add(key)) {
                throw new IllegalArgumentException("栏目不属于当前维度或重复：" + key);
            }
            int order = schema.orderOf(key);
            if (order <= previousOrder) {
                throw new IllegalArgumentException("栏目顺序不符合当前维度契约");
            }
            previousOrder = order;
            String title = required(value, "title", 40);
            if (!definition.getTitle().equals(title)) {
                throw new IllegalArgumentException("栏目标题不符合当前维度契约：" + key);
            }
            StockLearningCardSection section = new StockLearningCardSection();
            section.setSectionKey(key);
            section.setTitle(title);
            section.setContent(required(value, "content", 900));
            section.setEvidenceRefs(evidenceRefs(value.get("evidenceRefs"), availableEvidence));
            section.setVerificationStatus(enumValue(value, "verificationStatus", VERIFICATION));
            section.setSortOrder(sections.size() + 1);
            sections.add(section);
        }
        if (!seen.containsAll(schema.requiredKeys())) {
            throw new IllegalArgumentException("缺少当前维度必答栏目");
        }
        return sections;
    }

    private List<String> evidenceRefs(JsonNode node, Set<String> availableEvidence) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("evidenceRefs 必须是数组");
        }
        List<String> refs = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (JsonNode value : node) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("evidenceRefs 只能包含证据编号");
            }
            String ref = value.asText().trim();
            if (!availableEvidence.contains(ref) || !seen.add(ref)) {
                throw new IllegalArgumentException("证据编号无效或重复：" + ref);
            }
            refs.add(ref);
        }
        return refs;
    }

    private StockLearningCardClaim insufficient(StockLearningDimensionSchema schema, String reason) {
        StockLearningCardClaim claim = new StockLearningCardClaim();
        claim.setDimensionCode(schema.getDimensionCode());
        claim.setStatus("INSUFFICIENT_EVIDENCE");
        claim.setFailureMessage(reason);
        claim.setHeadline("证据不足，暂不形成判断");
        claim.setRatingLabel(schema.getRatingLabel());
        claim.setRatingValue("UNKNOWN");
        claim.setConfidence("LOW");
        List<StockLearningCardSection> sections = new ArrayList<StockLearningCardSection>();
        for (StockLearningDimensionSchema.SectionDefinition definition : schema.getRequiredSections()) {
            StockLearningCardSection section = new StockLearningCardSection();
            section.setSectionKey(definition.getKey());
            section.setTitle(definition.getTitle());
            section.setContent(reason);
            section.setEvidenceRefs(Collections.<String>emptyList());
            section.setVerificationStatus("UNVERIFIED");
            section.setSortOrder(sections.size() + 1);
            sections.add(section);
        }
        claim.setSections(sections);
        return claim;
    }

    private Map<String, Object> payload(String name, String code, StockLearningDimensionSchema schema,
                                        List<StockLearningCardEvidence> evidence) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("companyName", name);
        payload.put("companyCode", code);
        payload.put("dimension", schema.getDimensionCode());
        payload.put("ratingLabel", schema.getRatingLabel());
        payload.put("requiredSections", sectionPayload(schema.getRequiredSections()));
        payload.put("optionalSections", sectionPayload(schema.getOptionalSections()));
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        for (StockLearningCardEvidence item : evidence) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("id", item.getId());
            row.put("title", item.getTitle());
            row.put("source", item.getSource());
            row.put("publishedAt", item.getPublishedAt());
            row.put("content", compact(item.content(), 1800));
            rows.add(row);
        }
        payload.put("evidence", rows);
        return payload;
    }

    private List<Map<String, String>> sectionPayload(
            List<StockLearningDimensionSchema.SectionDefinition> definitions) {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        for (StockLearningDimensionSchema.SectionDefinition definition : definitions) {
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("key", definition.getKey());
            row.put("title", definition.getTitle());
            rows.add(row);
        }
        return rows;
    }

    private String systemPrompt() {
        return "你是股票研究学习卡Agent。只根据输入evidence分析指定dimension，不得补充外部事实。"
                + "输出单个JSON对象，只允许headline、ratingValue、sections、confidence。"
                + "sections必须包含全部requiredSections，可在证据真正支持时加入optionalSections，不得创造栏目或改标题。"
                + "每个栏目只允许key、title、content、evidenceRefs、verificationStatus；引用编号必须来自evidence。"
                + "ratingValue只能是HIGH、MEDIUM_HIGH、MEDIUM、MEDIUM_LOW、LOW、UNKNOWN；"
                + "verificationStatus只能是SUPPORTED、PARTIALLY_SUPPORTED、UNVERIFIED、CONTRADICTED；"
                + "confidence只能是HIGH、MEDIUM、LOW。必须区分事实、推断和未知。"
                + "不得给出买卖、加减仓、目标价、收益承诺或操作建议。只返回JSON。";
    }

    private String required(JsonNode root, String field, int max) {
        if (root == null || !root.has(field) || !root.get(field).isTextual()) {
            throw new IllegalArgumentException(field + " 缺失");
        }
        String value = root.get(field).asText().trim();
        if (value.isEmpty() || value.length() > max) {
            throw new IllegalArgumentException(field + " 长度不合法");
        }
        return value;
    }

    private String enumValue(JsonNode root, String field, Set<String> allowed) {
        String value = required(root, field, 40).toUpperCase();
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(field + " 不合法");
        }
        return value;
    }

    private void validateFields(JsonNode root, Set<String> allowed) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("输出必须是JSON对象");
        }
        Set<String> actual = new HashSet<String>();
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            actual.add(names.next());
        }
        if (!allowed.equals(actual)) {
            throw new IllegalArgumentException("输出字段不符合学习卡契约");
        }
    }

    private void validateTradingLanguage(StockLearningCardClaim claim) {
        StringBuilder combined = new StringBuilder(claim.getHeadline());
        for (StockLearningCardSection section : claim.getSections()) {
            combined.append(section.getContent());
        }
        if (!StockLearningFramework.isAllowedText(combined.toString())) {
            throw new IllegalArgumentException("输出包含交易语言");
        }
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return "";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end >= start ? raw.substring(start, end + 1) : raw.trim();
    }

    private String compact(String value, int max) {
        String clean = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }
}
