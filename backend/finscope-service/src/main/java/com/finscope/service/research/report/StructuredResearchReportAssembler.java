package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class StructuredResearchReportAssembler {
    public String assemble(ResearchThesis thesis, ResearchReportBlueprint blueprint,
                           ResearchReportNarrative narrative, List<ResearchEvidenceDossier> dossier) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(text(thesis.getSubjectName())).append("深度研究报告\n\n")
                .append("> 研究日期：").append(LocalDate.now()).append("  \n")
                .append("> 研究命题：").append(text(thesis.getQuestion())).append("\n\n");
        heading(out, "核心结论");
        out.append(blueprint.getDirectAnswer()).append(' ').append(refs(coreEvidenceRefs(blueprint)))
                .append("\n\n");
        heading(out, "关键事实与 AI 解读");
        for (int i = 0; i < blueprint.getArgumentChains().size(); i++) {
            ResearchReportBlueprint.ArgumentChain item = blueprint.getArgumentChains().get(i);
            out.append("### 事实 ").append(i + 1).append("\n\n")
                    .append("**事实：** ").append(item.getFact()).append(' ')
                    .append(refs(item.getEvidenceRefs())).append("\n\n")
                    .append("**AI 解读：** ")
                    .append(ground(narrative.getArgumentAnalysis().get(i), item.getEvidenceRefs())).append("\n\n")
                    .append("**解释边界：** ").append(item.getAlternativeExplanation()).append("\n\n");
        }
        heading(out, "命题拆解与综合判断");
        for (int i = 0; i < blueprint.getSubQuestions().size(); i++) {
            ResearchReportBlueprint.SubQuestion item = blueprint.getSubQuestions().get(i);
            out.append("### ").append(i + 1).append(". ").append(item.getQuestion()).append("\n\n")
                    .append("**判断：** ").append(item.getAnswer()).append(' ').append(refs(item.getEvidenceRefs())).append("\n\n")
                    .append(ground(narrative.getSubQuestionAnalysis().get(i), subQuestionRefs(item))).append("\n\n")
                    .append("**对命题的含义：** ").append(item.getImpact()).append("\n\n");
            if (!item.getUnknowns().isEmpty()) out.append("**仍未知：** ").append(String.join("；", item.getUnknowns())).append("\n\n");
        }
        heading(out, "影响机制");
        out.append(ground(narrative.getWhatHappened(), coreEvidenceRefs(blueprint))).append("\n\n");
        heading(out, "不同解释与不确定性");
        ResearchReportBlueprint.Counterargument counter = blueprint.getStrongestCounterargument();
        out.append("**另一种解释：** ").append(counter.getClaim()).append(' ').append(refs(counter.getEvidenceRefs())).append("\n\n")
                .append("**当前判断：** ").append(counter.getResponse()).append("\n\n")
                .append(ground(narrative.getCounterAnalysis(), counter.getEvidenceRefs())).append("\n\n")
                .append("**需要调整当前认识的情况：** ")
                .append(String.join("；", counter.getBecomesDominantWhen())).append("\n\n");
        for (String item : blueprint.getUnknowns()) out.append("- **尚未确认：** ").append(item).append("\n");
        heading(out, "情景推演");
        for (int i = 0; i < narrative.getScenarioAnalysis().size(); i++) {
            String name = i < blueprint.getScenarios().size()
                    ? text(blueprint.getScenarios().get(i).getName()) : "情景 " + (i + 1);
            out.append("### ").append(name.isEmpty() ? "情景 " + (i + 1) : name).append("\n\n")
                    .append(ground(narrative.getScenarioAnalysis().get(i), coreEvidenceRefs(blueprint))).append("\n\n");
        }
        heading(out, "结论更新条件");
        out.append(ground(narrative.getKnowledgeSynthesis(), coreEvidenceRefs(blueprint))).append("\n\n");
        for (String item : blueprint.getKnowledgeTakeaways()) out.append("- **当前认识：** ").append(item).append("\n");
        out.append(ground(narrative.getMonitoringPlan(), coreEvidenceRefs(blueprint))).append("\n\n");
        for (ResearchReportBlueprint.WatchItem item : blueprint.getWatchItems()) {
            out.append("- **").append(item.getMetric()).append("**：当前基线为").append(item.getBaseline())
                    .append("；按").append(item.getFrequency()).append("观察；出现“").append(item.getUpgradeCondition())
                    .append("”时强化结论；出现“").append(item.getDowngradeCondition()).append("”时削弱或推翻结论。\n");
        }
        heading(out, "资料来源");
        for (ResearchEvidenceDossier item : dossier) {
            out.append("### ").append(item.getEvidenceRef()).append(" · ").append(item.getTitle()).append("\n\n")
                    .append("- 来源：").append(item.getSourceName()).append("\n")
                    .append("- 发布时间：").append(item.getPublishedAt() == null ? "未提供" : item.getPublishedAt()).append("\n")
                    .append("- 原文：[").append(item.getTitle()).append("](").append(item.getUrl()).append(")\n\n");
        }
        return out.toString().trim();
    }

    private void heading(StringBuilder out, String title) { out.append("## ").append(title).append("\n\n"); }
    private String refs(List<String> values) { StringBuilder out = new StringBuilder(); for (String value : values) out.append(ref(value)); return out.toString(); }
    private List<String> coreEvidenceRefs(ResearchReportBlueprint blueprint) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        for (ResearchReportBlueprint.KeyInsight item : blueprint.getKeyInsights()) values.addAll(item.getEvidenceRefs());
        return new ArrayList<String>(values);
    }
    private List<String> subQuestionRefs(ResearchReportBlueprint.SubQuestion item) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        values.addAll(item.getEvidenceRefs());
        values.addAll(item.getCounterEvidenceRefs());
        return new ArrayList<String>(values);
    }
    private String ground(String value, List<String> evidenceRefs) {
        String citations = refs(evidenceRefs);
        if (citations.isEmpty() || value == null || value.trim().isEmpty()) return text(value);
        StringBuilder out = new StringBuilder();
        for (String line : value.split("\\n", -1)) {
            if (out.length() > 0) out.append('\n');
            String clean = line.trim();
            out.append(clean);
            if (!clean.isEmpty()) out.append(' ').append(citations);
        }
        return out.toString();
    }
    private String ref(String value) { return "[" + value + "](#evidence-" + value.toLowerCase() + ")"; }
    private String text(String value) { return value == null ? "" : value.replace("\n", " ").trim(); }
}
