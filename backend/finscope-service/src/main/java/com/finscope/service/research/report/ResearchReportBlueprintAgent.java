package com.finscope.service.research.report;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Component
public class ResearchReportBlueprintAgent {
    private static final int TIMEOUT_MS = 90000;
    private final LlmChatClient llm;
    private final ResearchReportBlueprintValidator validator;
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(JsonParser.Feature.ALLOW_TRAILING_COMMA);

    public ResearchReportBlueprintAgent(LlmChatClient llm, ResearchReportBlueprintValidator validator) {
        this.llm = llm;
        this.validator = validator;
    }

    public ResearchReportBlueprint generate(ResearchThesis thesis, List<ResearchEvidenceDossier> dossier)
            throws ResearchReportGenerationException {
        String raw;
        try {
            raw = llm.complete(systemPrompt(), userPrompt(thesis, dossier), TIMEOUT_MS, 3000);
        } catch (Exception ex) {
            throw new ResearchReportGenerationException("BLUEPRINT_FAILED:" + ex.getClass().getSimpleName(), ex);
        }
        try {
            return parseAndValidate(raw, dossier);
        } catch (Exception firstFailure) {
            return repair(thesis, dossier, raw, diagnostic(firstFailure));
        }
    }

    private String systemPrompt() {
        return "你是FinScope研究报告论证架构师。只能使用输入证据，不得补造事实、数字、来源或证据编号。"
                + "输出单个严格JSON对象，不要Markdown、注释或额外字段。"
                + "必须直接回答原问题，并按问题本身动态设计3到6个子问题，禁止套用固定公司经营模板。"
                + "keyInsights为3到6项，argumentChains至少2项，必须给出有实质内容的最强反方解释。"
                + "所有evidenceRefs只能使用输入给出的E编号。"
                + "字段严格为directAnswer,direction,confidence,confidenceBasis,timeRange,definitions,excludedQuestions,"
                + "keyInsights,subQuestions,argumentChains,strongestCounterargument,scenarios,knowledgeTakeaways,unknowns,watchItems。"
                + "direction只能是SUPPORT、PARTIAL_SUPPORT、MIXED、PARTIAL_CHALLENGE、CHALLENGE；confidence只能是HIGH、MEDIUM、LOW。"
                + "嵌套字段契约严格如下，不得把数组写成字符串："
                + "keyInsights=[{finding:string,meaning:string,evidenceRefs:string[]}];"
                + "subQuestions=[{key:string,question:string,answer:string,evidenceRefs:string[],counterEvidenceRefs:string[],impact:string,unknowns:string[]}];"
                + "argumentChains=[{fact:string,inference:string,judgment:string,alternativeExplanation:string,evidenceRefs:string[]}];"
                + "strongestCounterargument={claim:string,evidenceRefs:string[],response:string,becomesDominantWhen:string[]};"
                + "scenarios=[{name:string,trigger:string,mechanism:string,observableResult:string,impact:string,evidenceRefs:string[]}];"
                + "watchItems=[{metric:string,baseline:string,frequency:string,upgradeCondition:string,downgradeCondition:string}]。"
                + "definitions、excludedQuestions、knowledgeTakeaways、unknowns均为string[]。"
                + "数组即使为空也输出[]；所有文本字段必须是JSON字符串。";
    }

    private ResearchReportBlueprint repair(ResearchThesis thesis,
                                            List<ResearchEvidenceDossier> dossier,
                                            String invalidRaw,
                                            String firstDiagnostic) throws ResearchReportGenerationException {
        try {
            String repairPrompt = userPrompt(thesis, dossier)
                    + "\n上一次JSON校验失败：" + firstDiagnostic
                    + "\n请按系统消息中的精确嵌套字段契约修复以下输出。保留可用内容，只输出修复后的完整JSON：\n"
                    + stripFence(invalidRaw);
            String repairedRaw = llm.complete(systemPrompt(), repairPrompt, TIMEOUT_MS, 3000);
            ResearchReportBlueprint repaired = parseAndValidate(repairedRaw, dossier);
            repaired.setRepaired(true);
            return repaired;
        } catch (Exception repairFailure) {
            throw new ResearchReportGenerationException("BLUEPRINT_REPAIR_FAILED:"
                    + firstDiagnostic + ":" + diagnostic(repairFailure), repairFailure);
        }
    }

    private ResearchReportBlueprint parseAndValidate(String raw, List<ResearchEvidenceDossier> dossier)
            throws Exception {
        JsonNode root = mapper.readTree(stripFence(raw));
        if (!(root instanceof ObjectNode)) {
            throw new ResearchReportGenerationException("BLUEPRINT_INVALID:ROOT_NOT_OBJECT");
        }
        boolean[] normalized = new boolean[]{false};
        normalize((ObjectNode) root, normalized);
        ResearchReportBlueprint result = mapper.treeToValue(root, ResearchReportBlueprint.class);
        result.setRepaired(normalized[0]);
        List<String> issues = validator.validate(result, dossier);
        if (!issues.isEmpty()) {
            throw new ResearchReportGenerationException("BLUEPRINT_INVALID:" + String.join(",", issues));
        }
        return result;
    }

    private void normalize(ObjectNode root, boolean[] changed) {
        normalizeStringArrays(root, changed,
                "definitions", "excludedQuestions", "knowledgeTakeaways", "unknowns");
        normalizeObjectArrayFields(root.get("keyInsights"), changed, "evidenceRefs");
        normalizeObjectArrayFields(root.get("subQuestions"), changed,
                "evidenceRefs", "counterEvidenceRefs", "unknowns");
        normalizeSubQuestionKeys(root.get("subQuestions"), changed);
        normalizeObjectArrayFields(root.get("argumentChains"), changed, "evidenceRefs");
        JsonNode counter = root.get("strongestCounterargument");
        if (counter instanceof ObjectNode) {
            normalizeStringArrays((ObjectNode) counter, changed, "evidenceRefs", "becomesDominantWhen");
        }
        normalizeObjectArrayFields(root.get("scenarios"), changed, "evidenceRefs");
    }

    private void normalizeObjectArrayFields(JsonNode values, boolean[] changed, String... fields) {
        if (values == null || !values.isArray()) return;
        for (JsonNode value : values) {
            if (value instanceof ObjectNode) normalizeStringArrays((ObjectNode) value, changed, fields);
        }
    }

    private void normalizeStringArrays(ObjectNode value, boolean[] changed, String... fields) {
        for (String field : fields) {
            JsonNode node = value.get(field);
            if (node == null || node.isArray()) continue;
            if (node.isNull()) {
                value.set(field, mapper.createArrayNode());
                changed[0] = true;
            } else if (node.isTextual()) {
                ArrayNode array = mapper.createArrayNode();
                String text = node.asText().trim();
                if (!text.isEmpty()) array.add(text);
                value.set(field, array);
                changed[0] = true;
            }
        }
    }

    private void normalizeSubQuestionKeys(JsonNode values, boolean[] changed) {
        if (values == null || !values.isArray()) return;
        Set<String> keys = new HashSet<String>();
        int index = 0;
        for (JsonNode value : values) {
            index++;
            if (!(value instanceof ObjectNode)) continue;
            ObjectNode item = (ObjectNode) value;
            String key = item.path("key").asText("").trim();
            if (key.matches("[a-z][a-z0-9_]{2,47}") && keys.add(key)) continue;
            String normalized = "question_" + index;
            int suffix = 1;
            while (keys.contains(normalized)) normalized = "question_" + index + "_" + suffix++;
            item.put("key", normalized);
            keys.add(normalized);
            changed[0] = true;
        }
    }

    private String diagnostic(Exception ex) {
        String message = ex.getMessage();
        return ex.getClass().getSimpleName() + (message == null ? "" : "(" + message + ")");
    }

    private String userPrompt(ResearchThesis thesis, List<ResearchEvidenceDossier> dossier) {
        StringBuilder out = new StringBuilder();
        out.append("研究问题：").append(value(thesis.getQuestion())).append('\n')
                .append("研究对象：").append(value(thesis.getSubjectName())).append('\n')
                .append("对象类型：").append(value(thesis.getSubjectType())).append("\n证据档案：\n");
        for (ResearchEvidenceDossier item : dossier) {
            out.append(item.getEvidenceRef()).append(" | stance=").append(item.getStance())
                    .append(" | sourceTier=").append(item.getSourceTier())
                    .append(" | publishedAt=").append(item.getPublishedAt())
                    .append(" | source=").append(item.getSourceName())
                    .append(" | title=").append(item.getTitle())
                    .append(" | fact=").append(item.getFactExcerpt()).append('\n');
        }
        return out.toString();
    }

    private String stripFence(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("```json")) value = value.substring(7);
        else if (value.startsWith("```")) value = value.substring(3);
        if (value.endsWith("```")) value = value.substring(0, value.length() - 3);
        return value.trim();
    }

    private String value(String value) { return value == null ? "" : value.trim(); }
}
