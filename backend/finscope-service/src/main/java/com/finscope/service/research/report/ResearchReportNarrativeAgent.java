package com.finscope.service.research.report;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResearchReportNarrativeAgent {
    private static final int TIMEOUT_MS = 120000;
    private final LlmChatClient llm;
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(JsonParser.Feature.ALLOW_TRAILING_COMMA);

    public ResearchReportNarrativeAgent(LlmChatClient llm) { this.llm = llm; }

    public ResearchReportNarrative generate(ResearchThesis thesis, ResearchReportBlueprint blueprint,
                                            List<ResearchEvidenceDossier> dossier)
            throws ResearchReportGenerationException {
        String raw;
        try {
            raw = llm.complete(systemPrompt(), userPrompt(thesis, blueprint, dossier), TIMEOUT_MS, 7000);
        } catch (Exception ex) {
            throw new ResearchReportGenerationException("NARRATIVE_FAILED:" + ex.getClass().getSimpleName(), ex);
        }
        try {
            return parseAndValidate(raw, blueprint);
        } catch (Exception firstFailure) {
            return repair(thesis, blueprint, dossier, raw, diagnostic(firstFailure));
        }
    }

    private void validateShape(ResearchReportNarrative value, ResearchReportBlueprint blueprint)
            throws ResearchReportGenerationException {
        if (value == null || blank(value.getExecutiveSummary()) || blank(value.getWhatHappened())
                || blank(value.getCounterAnalysis()) || blank(value.getKnowledgeSynthesis())
                || blank(value.getMonitoringPlan())
                || value.getSubQuestionAnalysis() == null
                || value.getSubQuestionAnalysis().size() != blueprint.getSubQuestions().size()
                || value.getArgumentAnalysis() == null
                || value.getArgumentAnalysis().size() != blueprint.getArgumentChains().size()) {
            throw new ResearchReportGenerationException("NARRATIVE_INVALID:FIELD_COVERAGE");
        }
    }

    private String systemPrompt() {
        return "你是FinScope深度研究报告作者。只能使用已校验论证蓝图和证据档案，不得补造事实、数字、来源或链接。"
                + "输出单个严格JSON对象，不要Markdown围栏和额外字段。字段严格为executiveSummary,whatHappened,"
                + "subQuestionAnalysis,argumentAnalysis,counterAnalysis,scenarioAnalysis,knowledgeSynthesis,monitoringPlan。"
                + "字段类型严格为：executiveSummary:string，whatHappened:string，subQuestionAnalysis:string[]，"
                + "argumentAnalysis:string[]，counterAnalysis:string，scenarioAnalysis:string[]，"
                + "knowledgeSynthesis:string，monitoringPlan:string。不得把数组写成字符串或对象。"
                + "正文必须是中文、对象特定、信息密集；事实和数字后用[E1]格式引用。"
                + "明确区分事实、推理和判断，解释事实为何重要，并呈现最强替代解释。"
                + "目标总正文8000到16000中文字符；subQuestionAnalysis与蓝图子问题逐项对应，argumentAnalysis与论证链逐项对应。"
                + "禁止复述通用研究方法、禁止投资买卖指令、禁止用空泛风险提示填充长度。";
    }

    private ResearchReportNarrative repair(ResearchThesis thesis,
                                            ResearchReportBlueprint blueprint,
                                            List<ResearchEvidenceDossier> dossier,
                                            String invalidRaw,
                                            String firstDiagnostic) throws ResearchReportGenerationException {
        try {
            String repairPrompt = userPrompt(thesis, blueprint, dossier)
                    + "\n上一次正文JSON校验失败：" + firstDiagnostic
                    + "\n请按系统消息中的精确字段类型修复以下输出。数组长度必须与蓝图逐项对应，"
                    + "保留已有详细分析，只输出修复后的完整JSON：\n" + stripFence(invalidRaw);
            String repairedRaw = llm.complete(systemPrompt(), repairPrompt, TIMEOUT_MS, 7000);
            ResearchReportNarrative repaired = parseAndValidate(repairedRaw, blueprint);
            repaired.setRepaired(true);
            return repaired;
        } catch (Exception repairFailure) {
            throw new ResearchReportGenerationException("NARRATIVE_REPAIR_FAILED:"
                    + firstDiagnostic + ":" + diagnostic(repairFailure), repairFailure);
        }
    }

    private ResearchReportNarrative parseAndValidate(String raw, ResearchReportBlueprint blueprint)
            throws Exception {
        ResearchReportNarrative result = mapper.readValue(stripFence(raw), ResearchReportNarrative.class);
        validateShape(result, blueprint);
        return result;
    }

    private String diagnostic(Exception ex) {
        String message = ex.getMessage();
        return ex.getClass().getSimpleName() + (message == null ? "" : "(" + message + ")");
    }

    private String userPrompt(ResearchThesis thesis, ResearchReportBlueprint blueprint,
                              List<ResearchEvidenceDossier> dossier) throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("研究问题：").append(value(thesis.getQuestion())).append('\n')
                .append("研究对象：").append(value(thesis.getSubjectName())).append('\n')
                .append("论证蓝图：").append(mapper.writeValueAsString(blueprint)).append("\n证据档案：\n");
        for (ResearchEvidenceDossier item : dossier) {
            out.append(item.getEvidenceRef()).append(" | ").append(item.getPublishedAt()).append(" | ")
                    .append(item.getSourceTier()).append(" | ").append(item.getSourceName()).append(" | ")
                    .append(item.getTitle()).append(" | ").append(item.getFactExcerpt()).append('\n');
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

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private String value(String value) { return value == null ? "" : value.trim(); }
}
