package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ResearchReportNarrativeAgent {
    private static final int TIMEOUT_MS = 240_000;
    private static final int OUTPUT_TOKENS = 7_000;
    private static final Pattern MODEL_REF = Pattern.compile("\\[(?:E|e)\\d+]", Pattern.CASE_INSENSITIVE);

    private final LlmChatClient llm;
    private final DeterministicReportNarrativeBuilder baselineBuilder;
    private final ResearchReportSectionParser sectionParser;

    @Autowired
    public ResearchReportNarrativeAgent(LlmChatClient llm) {
        this(llm, new DeterministicReportNarrativeBuilder(), new ResearchReportSectionParser());
    }

    ResearchReportNarrativeAgent(LlmChatClient llm,
                                 DeterministicReportNarrativeBuilder baselineBuilder,
                                 ResearchReportSectionParser sectionParser) {
        this.llm = llm;
        this.baselineBuilder = baselineBuilder;
        this.sectionParser = sectionParser;
    }

    public ResearchReportNarrative generate(ResearchThesis thesis,
                                            ResearchReportBlueprint blueprint,
                                            List<ResearchEvidenceDossier> dossier)
            throws ResearchReportGenerationException {
        ResearchReportNarrative narrative = baselineBuilder.build(thesis, blueprint, dossier);
        Set<String> allowed = allowedSections(blueprint);
        narrative.setExpectedModelSectionCount(allowed.size());
        Map<String, String> sections = new LinkedHashMap<String, String>();
        boolean repairAttempted = false;
        try {
            String output = llm.complete(systemPrompt(), userPrompt(thesis, blueprint, dossier, allowed),
                    TIMEOUT_MS, OUTPUT_TOKENS);
            sections.putAll(sectionParser.parse(output, allowed));
            if (sections.size() < minimumCoverage(allowed.size())) {
                narrative.getDiagnostics().add("NARRATIVE_MODEL_FORMAT_INCOMPLETE");
                repairAttempted = true;
                Set<String> missing = new LinkedHashSet<String>(allowed);
                missing.removeAll(sections.keySet());
                String repaired = llm.complete(systemPrompt(),
                        repairPrompt(thesis, blueprint, dossier, missing), TIMEOUT_MS, OUTPUT_TOKENS);
                Map<String, String> repairedSections = sectionParser.parse(repaired, missing);
                for (Map.Entry<String, String> entry : repairedSections.entrySet()) {
                    if (!sections.containsKey(entry.getKey())) sections.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception error) {
            narrative.getDiagnostics().add((repairAttempted
                    ? "NARRATIVE_MODEL_REPAIR_FAILED:" : "NARRATIVE_MODEL_CALL_FAILED:")
                    + error.getClass().getSimpleName());
        }
        apply(narrative, blueprint, sections);
        narrative.setModelSectionCount(sections.size());
        narrative.setModelEnhanced(sections.size() >= minimumCoverage(allowed.size()));
        narrative.setRepaired(repairAttempted || (!sections.isEmpty() && sections.size() < allowed.size()));
        return narrative;
    }

    private Set<String> allowedSections(ResearchReportBlueprint blueprint) {
        Set<String> result = new LinkedHashSet<String>();
        result.add("EXECUTIVE_SUMMARY");
        result.add("WHAT_HAPPENED");
        for (int index = 0; index < blueprint.getSubQuestions().size(); index++) {
            result.add("SUBQUESTION_" + (index + 1));
        }
        for (int index = 0; index < blueprint.getArgumentChains().size(); index++) {
            result.add("ARGUMENT_" + (index + 1));
        }
        result.add("COUNTER_ANALYSIS");
        for (int index = 0; index < blueprint.getScenarios().size(); index++) {
            result.add("SCENARIO_" + (index + 1));
        }
        result.add("KNOWLEDGE_SYNTHESIS");
        result.add("MONITORING_PLAN");
        return result;
    }

    private void apply(ResearchReportNarrative narrative,
                       ResearchReportBlueprint blueprint,
                       Map<String, String> sections) {
        narrative.setExecutiveSummary(slot(sections, "EXECUTIVE_SUMMARY", narrative.getExecutiveSummary()));
        narrative.setWhatHappened(slot(sections, "WHAT_HAPPENED", narrative.getWhatHappened()));
        for (int index = 0; index < blueprint.getSubQuestions().size(); index++) {
            narrative.getSubQuestionAnalysis().set(index, slot(sections, "SUBQUESTION_" + (index + 1),
                    narrative.getSubQuestionAnalysis().get(index)));
        }
        for (int index = 0; index < blueprint.getArgumentChains().size(); index++) {
            narrative.getArgumentAnalysis().set(index, slot(sections, "ARGUMENT_" + (index + 1),
                    narrative.getArgumentAnalysis().get(index)));
        }
        narrative.setCounterAnalysis(slot(sections, "COUNTER_ANALYSIS", narrative.getCounterAnalysis()));
        for (int index = 0; index < blueprint.getScenarios().size(); index++) {
            narrative.getScenarioAnalysis().set(index, slot(sections, "SCENARIO_" + (index + 1),
                    narrative.getScenarioAnalysis().get(index)));
        }
        narrative.setKnowledgeSynthesis(slot(sections, "KNOWLEDGE_SYNTHESIS", narrative.getKnowledgeSynthesis()));
        narrative.setMonitoringPlan(slot(sections, "MONITORING_PLAN", narrative.getMonitoringPlan()));
    }

    private String slot(Map<String, String> sections, String name, String fallback) {
        String value = sections.get(name);
        if (value == null || value.trim().isEmpty()) return fallback;
        return MODEL_REF.matcher(value).replaceAll("").trim();
    }

    private int minimumCoverage(int expected) {
        return Math.max(4, (expected + 2) / 3);
    }

    private String systemPrompt() {
        return "你是FinScope深度研究作者。Java已经固定报告章节、事实表、证据引用和附录；你只增强指定正文槽位。"
                + "不要输出JSON、Markdown围栏、数组、对象、二级标题、URL或证据编号。"
                + "不得新增证据档案之外的事实、数字和日期；需要引用的事实由Java在组装时绑定。"
                + "每个槽位严格使用<<<SLOT_NAME>>>开始、<<<END>>>结束。"
                + "分析必须使用中文，围绕研究对象解释事实如何影响关键变量、哪些结论可以形成、哪些不能形成，"
                + "并呈现替代解释、未知项和可证伪条件。避免空泛风险提示和投资买卖指令。";
    }

    private String userPrompt(ResearchThesis thesis,
                              ResearchReportBlueprint blueprint,
                              List<ResearchEvidenceDossier> dossier,
                              Set<String> allowed) {
        StringBuilder out = context(thesis, blueprint, dossier);
        out.append("\n请完整增强以下正文槽位。执行摘要和事件脉络各800至1600字；"
                + "子问题、论证链和情景各300至700字；最终认识和监测计划各500至1000字。\n");
        for (String name : allowed) out.append("<<<").append(name).append(">>>\n对象特定分析\n<<<END>>>\n");
        return out.toString();
    }

    private String repairPrompt(ResearchThesis thesis,
                                ResearchReportBlueprint blueprint,
                                List<ResearchEvidenceDossier> dossier,
                                Set<String> missing) {
        StringBuilder out = context(thesis, blueprint, dossier);
        out.append("\n上一次输出缺少可解析正文。不要重复已完成内容，不要解释原因，不要输出JSON。"
                + "只返回以下缺失槽位：\n");
        for (String name : missing) out.append("<<<").append(name).append(">>>\n详细对象特定分析\n<<<END>>>\n");
        return out.toString();
    }

    private StringBuilder context(ResearchThesis thesis,
                                  ResearchReportBlueprint blueprint,
                                  List<ResearchEvidenceDossier> dossier) {
        StringBuilder out = new StringBuilder();
        out.append("研究对象：").append(value(thesis.getSubjectName())).append('\n')
                .append("研究问题：").append(value(thesis.getQuestion())).append('\n')
                .append("直接回答：").append(value(blueprint.getDirectAnswer())).append('\n')
                .append("方向与置信度：").append(value(blueprint.getDirection())).append(" / ")
                .append(value(blueprint.getConfidence())).append("\n子问题：\n");
        for (int index = 0; index < blueprint.getSubQuestions().size(); index++) {
            ResearchReportBlueprint.SubQuestion item = blueprint.getSubQuestions().get(index);
            out.append(index + 1).append(". ").append(value(item.getQuestion())).append(" | 当前回答=")
                    .append(value(item.getAnswer())).append(" | 影响=").append(value(item.getImpact())).append('\n');
        }
        out.append("论证链：\n");
        for (int index = 0; index < blueprint.getArgumentChains().size(); index++) {
            ResearchReportBlueprint.ArgumentChain item = blueprint.getArgumentChains().get(index);
            out.append(index + 1).append(". 事实=").append(value(item.getFact()))
                    .append(" | 推理=").append(value(item.getInference()))
                    .append(" | 判断=").append(value(item.getJudgment()))
                    .append(" | 替代解释=").append(value(item.getAlternativeExplanation())).append('\n');
        }
        out.append("证据档案：\n");
        if (dossier != null) for (ResearchEvidenceDossier item : dossier) {
            out.append(item.getEvidenceRef()).append(" | stance=").append(item.getStance())
                    .append(" | sourceTier=").append(item.getSourceTier())
                    .append(" | title=").append(value(item.getTitle()))
                    .append(" | fact=").append(value(item.getFactExcerpt())).append('\n');
        }
        return out;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
