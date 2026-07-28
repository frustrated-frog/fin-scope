package com.finscope.service.research.report;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResearchReportBlueprintAgent {
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
        try {
            String raw = llm.complete(systemPrompt(), userPrompt(thesis, dossier), 45000, 3000);
            ResearchReportBlueprint result = mapper.readValue(stripFence(raw), ResearchReportBlueprint.class);
            List<String> issues = validator.validate(result, dossier);
            if (!issues.isEmpty()) throw new ResearchReportGenerationException("BLUEPRINT_INVALID:" + String.join(",", issues));
            return result;
        } catch (ResearchReportGenerationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResearchReportGenerationException("BLUEPRINT_FAILED:" + ex.getClass().getSimpleName(), ex);
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
                + "数组即使为空也输出[]；所有文本字段必须是JSON字符串。";
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
