package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class StructuredResearchReportAssembler {
    public String assemble(ResearchThesis thesis, ResearchReportBlueprint blueprint,
                           ResearchReportNarrative narrative, List<ResearchEvidenceDossier> dossier) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(text(thesis.getSubjectName())).append("深度研究报告\n\n")
                .append("> 研究日期：").append(LocalDate.now()).append("  \n")
                .append("> 原始问题：").append(text(thesis.getQuestion())).append("  \n")
                .append("> 判断：").append(blueprint.getDirection()).append(" · 置信度：")
                .append(blueprint.getConfidence()).append("\n\n");
        heading(out, "核心结论");
        out.append(blueprint.getDirectAnswer()).append("\n\n**置信度依据：** ")
                .append(blueprint.getConfidenceBasis()).append("\n\n");
        heading(out, "关键认识");
        for (ResearchReportBlueprint.KeyInsight item : blueprint.getKeyInsights()) {
            out.append("- **").append(item.getFinding()).append("**：").append(item.getMeaning()).append(' ')
                    .append(refs(item.getEvidenceRefs())).append("\n");
        }
        heading(out, "执行摘要"); out.append(narrative.getExecutiveSummary()).append("\n\n");
        heading(out, "研究范围与口径");
        out.append("- **观察范围：** ").append(blueprint.getTimeRange()).append("\n");
        for (String item : blueprint.getDefinitions()) out.append("- **口径：** ").append(item).append("\n");
        for (String item : blueprint.getExcludedQuestions()) out.append("- **本次不能回答：** ").append(item).append("\n");
        heading(out, "关键事实与数字");
        out.append("| 证据 | 时间 | 可验证事实 | 来源层级 |\n| --- | --- | --- | --- |\n");
        for (ResearchEvidenceDossier item : dossier) {
            out.append("| ").append(ref(item.getEvidenceRef())).append(" | ").append(item.getPublishedAt() == null ? "未提供" : item.getPublishedAt())
                    .append(" | ").append(tableExcerpt(item.getFactExcerpt())).append(" | ").append(item.getSourceTier()).append(" |\n");
        }
        heading(out, "发生了什么"); out.append(narrative.getWhatHappened()).append("\n\n");
        heading(out, "命题拆解与逐题判断");
        for (int i = 0; i < blueprint.getSubQuestions().size(); i++) {
            ResearchReportBlueprint.SubQuestion item = blueprint.getSubQuestions().get(i);
            out.append("### ").append(i + 1).append(". ").append(item.getQuestion()).append("\n\n")
                    .append("**当前回答：** ").append(item.getAnswer()).append(' ').append(refs(item.getEvidenceRefs())).append("\n\n")
                    .append(narrative.getSubQuestionAnalysis().get(i)).append("\n\n")
                    .append("**对总命题的影响：** ").append(item.getImpact()).append("\n\n");
            if (!item.getUnknowns().isEmpty()) out.append("**仍未知：** ").append(String.join("；", item.getUnknowns())).append("\n\n");
        }
        heading(out, "核心证据链");
        for (int i = 0; i < blueprint.getArgumentChains().size(); i++) {
            ResearchReportBlueprint.ArgumentChain item = blueprint.getArgumentChains().get(i);
            out.append("### 证据链 ").append(i + 1).append("\n\n")
                    .append("- **事实：** ").append(item.getFact()).append(' ').append(refs(item.getEvidenceRefs())).append("\n")
                    .append("- **推理：** ").append(item.getInference()).append("\n")
                    .append("- **判断：** ").append(item.getJudgment()).append("\n")
                    .append("- **替代解释：** ").append(item.getAlternativeExplanation()).append("\n\n")
                    .append(narrative.getArgumentAnalysis().get(i)).append("\n\n");
        }
        heading(out, "反方解释与争议");
        ResearchReportBlueprint.Counterargument counter = blueprint.getStrongestCounterargument();
        out.append("**最强反方观点：** ").append(counter.getClaim()).append(' ').append(refs(counter.getEvidenceRefs())).append("\n\n")
                .append("**当前回应：** ").append(counter.getResponse()).append("\n\n")
                .append(narrative.getCounterAnalysis()).append("\n\n")
                .append("**反方成为更优解释的条件：** ").append(String.join("；", counter.getBecomesDominantWhen())).append("\n\n");
        heading(out, "机制与情景推演");
        for (int i = 0; i < narrative.getScenarioAnalysis().size(); i++)
            out.append("### 情景 ").append(i + 1).append("\n\n").append(narrative.getScenarioAnalysis().get(i)).append("\n\n");
        heading(out, "最终认识与未知项");
        out.append(narrative.getKnowledgeSynthesis()).append("\n\n");
        for (String item : blueprint.getKnowledgeTakeaways()) out.append("- **可以形成的认识：** ").append(item).append("\n");
        for (String item : blueprint.getUnknowns()) out.append("- **仍然未知：** ").append(item).append("\n");
        heading(out, "跟踪清单与失效条件"); out.append(narrative.getMonitoringPlan()).append("\n\n");
        for (ResearchReportBlueprint.WatchItem item : blueprint.getWatchItems()) {
            out.append("- **").append(item.getMetric()).append("**：基线=").append(item.getBaseline())
                    .append("；频率=").append(item.getFrequency()).append("；上调=").append(item.getUpgradeCondition())
                    .append("；下调/失效=").append(item.getDowngradeCondition()).append("\n");
        }
        heading(out, "证据附录");
        for (ResearchEvidenceDossier item : dossier) {
            out.append("<a id=\"evidence-").append(item.getEvidenceRef().toLowerCase()).append("\"></a>\n")
                    .append("### ").append(item.getEvidenceRef()).append(" · ").append(item.getTitle()).append("\n\n")
                    .append("- 来源：").append(item.getSourceName()).append("（").append(item.getSourceTier()).append("）\n")
                    .append("- 发布时间：").append(item.getPublishedAt() == null ? "未提供" : item.getPublishedAt()).append("\n")
                    .append("- 立场：").append(item.getStance()).append("；相关性：").append(item.getRelevanceScore()).append("/100\n")
                    .append("- 事实摘录：").append(item.getFactExcerpt()).append("\n")
                    .append("- 原文：[").append(item.getTitle()).append("](").append(item.getUrl()).append(") · 文章 #")
                    .append(item.getArticleId()).append("\n\n");
        }
        return out.toString().trim();
    }

    private void heading(StringBuilder out, String title) { out.append("## ").append(title).append("\n\n"); }
    private String refs(List<String> values) { StringBuilder out = new StringBuilder(); for (String value : values) out.append(ref(value)); return out.toString(); }
    private String ref(String value) { return "[" + value + "](#evidence-" + value.toLowerCase() + ")"; }
    private String text(String value) { return value == null ? "" : value.replace("\n", " ").trim(); }
    private String cell(String value) { return text(value).replace("|", "｜"); }
    private String tableExcerpt(String value) {
        String clean = cell(value);
        return clean.length() <= 260 ? clean : clean.substring(0, 259).trim() + "…";
    }
}
