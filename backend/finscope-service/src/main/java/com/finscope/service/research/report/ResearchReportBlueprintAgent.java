package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ResearchReportBlueprintAgent {
    private static final int TIMEOUT_MS = 90_000;
    private static final int OUTPUT_TOKENS = 3_000;
    private static final int MINIMUM_MODEL_SECTIONS = 3;
    private static final Pattern MODEL_REF = Pattern.compile("\\[(?:E|e)\\d+]", Pattern.CASE_INSENSITIVE);

    private final LlmChatClient llm;
    private final ResearchReportBlueprintValidator validator;
    private final DeterministicReportBlueprintBuilder baselineBuilder;
    private final ResearchReportSectionParser sectionParser;

    public ResearchReportBlueprintAgent(LlmChatClient llm, ResearchReportBlueprintValidator validator) {
        this(llm, validator, new DeterministicReportBlueprintBuilder(), new ResearchReportSectionParser());
    }

    ResearchReportBlueprintAgent(LlmChatClient llm,
                                 ResearchReportBlueprintValidator validator,
                                 DeterministicReportBlueprintBuilder baselineBuilder,
                                 ResearchReportSectionParser sectionParser) {
        this.llm = llm;
        this.validator = validator;
        this.baselineBuilder = baselineBuilder;
        this.sectionParser = sectionParser;
    }

    public ResearchReportBlueprint generate(ResearchThesis thesis, List<ResearchEvidenceDossier> dossier)
            throws ResearchReportGenerationException {
        ResearchReportBlueprint blueprint = baselineBuilder.build(thesis, dossier, sufficient(dossier));
        Set<String> allowed = allowedSections(blueprint);
        blueprint.setExpectedModelSectionCount(allowed.size());
        Map<String, String> sections = java.util.Collections.emptyMap();
        boolean repairAttempted = false;
        try {
            String output = llm.complete(systemPrompt(), userPrompt(thesis, blueprint, dossier, allowed),
                    TIMEOUT_MS, OUTPUT_TOKENS);
            sections = sectionParser.parse(output, allowed);
            if (sections.size() < MINIMUM_MODEL_SECTIONS) {
                blueprint.getDiagnostics().add("BLUEPRINT_MODEL_FORMAT_UNUSABLE");
                repairAttempted = true;
                String repaired = llm.complete(systemPrompt(), repairPrompt(thesis, blueprint, dossier, allowed),
                        TIMEOUT_MS, OUTPUT_TOKENS);
                Map<String, String> repairedSections = sectionParser.parse(repaired, allowed);
                if (!repairedSections.isEmpty()) sections = repairedSections;
            }
        } catch (Exception error) {
            blueprint.getDiagnostics().add((repairAttempted
                    ? "BLUEPRINT_MODEL_REPAIR_FAILED:" : "BLUEPRINT_MODEL_CALL_FAILED:")
                    + error.getClass().getSimpleName());
        }
        apply(blueprint, sections);
        blueprint.setModelSectionCount(sections.size());
        blueprint.setModelEnhanced(sections.size() >= MINIMUM_MODEL_SECTIONS);
        blueprint.setRepaired(repairAttempted || (!sections.isEmpty() && sections.size() < allowed.size()));
        List<String> issues = validator.validate(blueprint, dossier);
        if (!issues.isEmpty()) {
            ResearchReportBlueprint baseline = baselineBuilder.build(thesis, dossier, sufficient(dossier));
            baseline.setExpectedModelSectionCount(allowed.size());
            baseline.setDiagnostics(new ArrayList<String>(blueprint.getDiagnostics()));
            baseline.getDiagnostics().add("BLUEPRINT_SERVER_VALIDATION_RESTORED_BASELINE");
            return baseline;
        }
        return blueprint;
    }

    private Set<String> allowedSections(ResearchReportBlueprint blueprint) {
        Set<String> result = new LinkedHashSet<String>();
        result.add("DIRECT_ANSWER");
        result.add("CONFIDENCE_BASIS");
        for (int index = 0; index < blueprint.getKeyInsights().size(); index++) {
            result.add("KEY_INSIGHT_" + (index + 1) + "_FINDING");
            result.add("KEY_INSIGHT_" + (index + 1) + "_MEANING");
        }
        for (int index = 0; index < blueprint.getSubQuestions().size(); index++) {
            result.add("SUBQUESTION_" + (index + 1) + "_QUESTION");
            result.add("SUBQUESTION_" + (index + 1) + "_ANSWER");
            result.add("SUBQUESTION_" + (index + 1) + "_IMPACT");
        }
        for (int index = 0; index < blueprint.getArgumentChains().size(); index++) {
            result.add("ARGUMENT_" + (index + 1) + "_INFERENCE");
            result.add("ARGUMENT_" + (index + 1) + "_JUDGMENT");
            result.add("ARGUMENT_" + (index + 1) + "_ALTERNATIVE");
        }
        result.add("COUNTER_CLAIM");
        result.add("COUNTER_RESPONSE");
        return result;
    }

    private void apply(ResearchReportBlueprint blueprint, Map<String, String> sections) {
        if (sections == null || sections.isEmpty()) return;
        blueprint.setDirectAnswer(slot(sections, "DIRECT_ANSWER", blueprint.getDirectAnswer()));
        blueprint.setConfidenceBasis(slot(sections, "CONFIDENCE_BASIS", blueprint.getConfidenceBasis()));
        for (int index = 0; index < blueprint.getKeyInsights().size(); index++) {
            ResearchReportBlueprint.KeyInsight item = blueprint.getKeyInsights().get(index);
            String prefix = "KEY_INSIGHT_" + (index + 1);
            item.setFinding(slot(sections, prefix + "_FINDING", item.getFinding()));
            item.setMeaning(slot(sections, prefix + "_MEANING", item.getMeaning()));
        }
        for (int index = 0; index < blueprint.getSubQuestions().size(); index++) {
            ResearchReportBlueprint.SubQuestion item = blueprint.getSubQuestions().get(index);
            String prefix = "SUBQUESTION_" + (index + 1);
            item.setQuestion(slot(sections, prefix + "_QUESTION", item.getQuestion()));
            item.setAnswer(slot(sections, prefix + "_ANSWER", item.getAnswer()));
            item.setImpact(slot(sections, prefix + "_IMPACT", item.getImpact()));
        }
        for (int index = 0; index < blueprint.getArgumentChains().size(); index++) {
            ResearchReportBlueprint.ArgumentChain item = blueprint.getArgumentChains().get(index);
            String prefix = "ARGUMENT_" + (index + 1);
            item.setInference(slot(sections, prefix + "_INFERENCE", item.getInference()));
            item.setJudgment(slot(sections, prefix + "_JUDGMENT", item.getJudgment()));
            item.setAlternativeExplanation(slot(sections, prefix + "_ALTERNATIVE", item.getAlternativeExplanation()));
        }
        ResearchReportBlueprint.Counterargument counter = blueprint.getStrongestCounterargument();
        if (counter != null) {
            counter.setClaim(slot(sections, "COUNTER_CLAIM", counter.getClaim()));
            counter.setResponse(slot(sections, "COUNTER_RESPONSE", counter.getResponse()));
        }
    }

    private String slot(Map<String, String> sections, String name, String fallback) {
        String value = sections.get(name);
        if (value == null || value.trim().isEmpty()) return fallback;
        return MODEL_REF.matcher(value).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    private String systemPrompt() {
        return "你是FinScope研究论证增强器。Java已经生成合法蓝图、数组和证据引用；你只能改写指定文本槽位。"
                + "不要输出JSON、Markdown围栏、数组、对象、URL或证据编号，不得新增输入之外的事实、数字和日期。"
                + "每个槽位严格使用<<<SLOT_NAME>>>开始、<<<END>>>结束；槽位名必须来自用户消息。"
                + "正文使用中文，直接回答研究问题，区分事实、推理、判断和替代解释。";
    }

    private String userPrompt(ResearchThesis thesis,
                              ResearchReportBlueprint blueprint,
                              List<ResearchEvidenceDossier> dossier,
                              Set<String> allowed) {
        StringBuilder out = context(thesis, blueprint, dossier);
        out.append("\n请增强以下槽位，每个槽位都必须独立闭合：\n");
        for (String name : allowed) out.append("<<<").append(name).append(">>>\n对象特定文本\n<<<END>>>\n");
        return out.toString();
    }

    private String repairPrompt(ResearchThesis thesis,
                                ResearchReportBlueprint blueprint,
                                List<ResearchEvidenceDossier> dossier,
                                Set<String> allowed) {
        StringBuilder out = context(thesis, blueprint, dossier);
        out.append("\n上一次输出未使用分段协议。不要解释原因，不要输出JSON。至少返回以下关键槽位：\n")
                .append("<<<DIRECT_ANSWER>>>\n直接回答原问题\n<<<END>>>\n")
                .append("<<<KEY_INSIGHT_1_MEANING>>>\n第一项事实的研究含义\n<<<END>>>\n")
                .append("<<<SUBQUESTION_1_ANSWER>>>\n第一个子问题的当前回答\n<<<END>>>\n");
        return out.toString();
    }

    private StringBuilder context(ResearchThesis thesis,
                                  ResearchReportBlueprint blueprint,
                                  List<ResearchEvidenceDossier> dossier) {
        StringBuilder out = new StringBuilder();
        out.append("研究对象：").append(value(thesis.getSubjectName())).append('\n')
                .append("研究问题：").append(value(thesis.getQuestion())).append('\n')
                .append("服务端方向：").append(value(blueprint.getDirection())).append('\n')
                .append("服务端置信度：").append(value(blueprint.getConfidence())).append("\n证据档案：\n");
        if (dossier != null) for (ResearchEvidenceDossier item : dossier) {
            out.append(item.getEvidenceRef()).append(" | stance=").append(item.getStance())
                    .append(" | sourceTier=").append(item.getSourceTier())
                    .append(" | title=").append(value(item.getTitle()))
                    .append(" | fact=").append(value(item.getFactExcerpt())).append('\n');
        }
        return out;
    }

    private boolean sufficient(List<ResearchEvidenceDossier> dossier) {
        if (dossier == null || dossier.size() < 6) return false;
        Set<String> sources = new HashSet<String>();
        boolean support = false;
        boolean counter = false;
        for (ResearchEvidenceDossier item : dossier) {
            sources.add(value(item.getSourceIdentity()));
            support |= "SUPPORT".equals(item.getStance());
            counter |= "COUNTER".equals(item.getStance());
        }
        return sources.size() >= 2 && support && counter;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
