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
                .append("> 原始问题：").append(text(thesis.getQuestion())).append("  \n")
                .append("> 判断：").append(text(blueprint.getDirection())).append(" · 置信度：")
                .append(text(blueprint.getConfidence())).append("\n\n");

        heading(out, "核心结论");
        out.append(text(blueprint.getDirectAnswer())).append(' ').append(refs(coreEvidenceRefs(blueprint)))
                .append("\n\n**置信度依据：** ").append(text(blueprint.getConfidenceBasis())).append("\n\n");

        heading(out, "关键认识");
        for (ResearchReportBlueprint.KeyInsight item : safe(blueprint.getKeyInsights())) {
            out.append("- **").append(text(item.getFinding())).append("**：")
                    .append(text(item.getMeaning())).append(' ').append(refs(item.getEvidenceRefs())).append("\n");
        }
        out.append('\n');

        heading(out, "执行摘要");
        out.append(ground(narrative.getExecutiveSummary(), coreEvidenceRefs(blueprint))).append("\n\n");

        heading(out, "研究范围与口径");
        out.append("- **研究对象：** ").append(text(thesis.getSubjectName())).append("\n")
                .append("- **研究问题：** ").append(text(thesis.getQuestion())).append("\n")
                .append("- **观察范围：** ").append(textOr(blueprint.getTimeRange(), "本次运行收集的公开证据所覆盖期间")).append("\n");
        for (String item : safeStrings(blueprint.getDefinitions())) {
            out.append("- **口径：** ").append(text(item)).append("\n");
        }
        for (String item : safeStrings(blueprint.getExcludedQuestions())) {
            out.append("- **本次不能回答：** ").append(text(item)).append("\n");
        }
        out.append('\n');

        heading(out, "关键事实与数字");
        out.append("| 证据 | 立场 | 时间 | 可验证事实 | 来源层级 | 相关性 |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n");
        for (ResearchEvidenceDossier item : safe(dossier)) {
            out.append("| ").append(ref(item.getEvidenceRef())).append(" | ")
                    .append(stance(item.getStance())).append(" | ")
                    .append(item.getPublishedAt() == null ? "未提供" : item.getPublishedAt()).append(" | ")
                    .append(tableExcerpt(item.getFactExcerpt())).append(" | ")
                    .append(tableCell(textOr(item.getSourceTier(), "MEDIA"))).append(" | ")
                    .append(item.getRelevanceScore()).append("/100 |\n");
        }
        if (dossier == null || dossier.isEmpty()) {
            out.append("| — | 未知 | 未提供 | 当前公开证据未覆盖 | — | — |\n");
        }
        out.append('\n');

        heading(out, "发生了什么");
        out.append(ground(narrative.getWhatHappened(), coreEvidenceRefs(blueprint))).append("\n\n");

        heading(out, "命题拆解与逐题判断");
        List<ResearchReportBlueprint.SubQuestion> subQuestions = safe(blueprint.getSubQuestions());
        for (int index = 0; index < subQuestions.size(); index++) {
            ResearchReportBlueprint.SubQuestion item = subQuestions.get(index);
            List<String> itemRefs = subQuestionRefs(item);
            out.append("### ").append(index + 1).append(". ").append(text(item.getQuestion())).append("\n\n")
                    .append("**当前回答：** ").append(text(item.getAnswer())).append(' ')
                    .append(refs(item.getEvidenceRefs())).append("\n\n")
                    .append(ground(at(narrative.getSubQuestionAnalysis(), index,
                            text(item.getImpact())), itemRefs)).append("\n\n")
                    .append("**对总命题的影响：** ").append(text(item.getImpact())).append("\n\n");
            if (!safeStrings(item.getUnknowns()).isEmpty()) {
                out.append("**仍未知：** ").append(String.join("；", item.getUnknowns())).append("\n\n");
            }
        }

        heading(out, "核心证据链");
        List<ResearchReportBlueprint.ArgumentChain> chains = safe(blueprint.getArgumentChains());
        for (int index = 0; index < chains.size(); index++) {
            ResearchReportBlueprint.ArgumentChain item = chains.get(index);
            out.append("### 证据链 ").append(index + 1).append("\n\n")
                    .append("- **事实：** ").append(text(item.getFact())).append(' ')
                    .append(refs(item.getEvidenceRefs())).append("\n")
                    .append("- **推理：** ").append(text(item.getInference())).append("\n")
                    .append("- **判断：** ").append(text(item.getJudgment())).append("\n")
                    .append("- **替代解释：** ").append(text(item.getAlternativeExplanation())).append("\n\n")
                    .append(ground(at(narrative.getArgumentAnalysis(), index,
                            item.getInference()), item.getEvidenceRefs())).append("\n\n");
        }

        heading(out, "反方解释与争议");
        ResearchReportBlueprint.Counterargument counter = blueprint.getStrongestCounterargument();
        if (counter == null) {
            out.append("**最强反方观点：** 当前证据尚未形成可独立验证的反方解释，这本身构成结论局限。\n\n");
        } else {
            out.append("**最强反方观点：** ").append(text(counter.getClaim())).append(' ')
                    .append(refs(counter.getEvidenceRefs())).append("\n\n")
                    .append("**当前回应：** ").append(text(counter.getResponse())).append("\n\n")
                    .append(ground(narrative.getCounterAnalysis(), counter.getEvidenceRefs())).append("\n\n")
                    .append("**反方成为更优解释的条件：** ")
                    .append(String.join("；", safeStrings(counter.getBecomesDominantWhen()))).append("\n\n");
        }

        heading(out, "机制与情景推演");
        List<ResearchReportBlueprint.Scenario> scenarios = safe(blueprint.getScenarios());
        for (int index = 0; index < scenarios.size(); index++) {
            ResearchReportBlueprint.Scenario item = scenarios.get(index);
            out.append("### ").append(textOr(item.getName(), "情景 " + (index + 1))).append("\n\n")
                    .append("- **触发条件：** ").append(text(item.getTrigger())).append("\n")
                    .append("- **传导机制：** ").append(text(item.getMechanism())).append("\n")
                    .append("- **可观察结果：** ").append(text(item.getObservableResult())).append("\n")
                    .append("- **对当前结论的影响：** ").append(text(item.getImpact())).append(' ')
                    .append(refs(item.getEvidenceRefs())).append("\n\n")
                    .append(ground(at(narrative.getScenarioAnalysis(), index, item.getMechanism()),
                            item.getEvidenceRefs())).append("\n\n");
        }

        heading(out, "最终认识与未知项");
        out.append(ground(narrative.getKnowledgeSynthesis(), coreEvidenceRefs(blueprint))).append("\n\n");
        for (String item : safeStrings(blueprint.getKnowledgeTakeaways())) {
            out.append("- **可以形成的认识：** ").append(text(item)).append("\n");
        }
        for (String item : safeStrings(blueprint.getUnknowns())) {
            out.append("- **仍然未知：** ").append(text(item)).append("\n");
        }
        out.append('\n');

        heading(out, "跟踪清单与失效条件");
        out.append(ground(narrative.getMonitoringPlan(), coreEvidenceRefs(blueprint))).append("\n\n");
        for (ResearchReportBlueprint.WatchItem item : safe(blueprint.getWatchItems())) {
            out.append("- **").append(text(item.getMetric())).append("**：当前基线=")
                    .append(textOr(item.getBaseline(), "当前公开证据未覆盖"))
                    .append("；观察频率=").append(text(item.getFrequency()))
                    .append("；结论上调=").append(text(item.getUpgradeCondition()))
                    .append("；结论下调/失效=").append(text(item.getDowngradeCondition())).append("\n");
        }
        out.append('\n');

        heading(out, "证据附录");
        for (ResearchEvidenceDossier item : safe(dossier)) {
            out.append("<a id=\"evidence-").append(text(item.getEvidenceRef()).toLowerCase()).append("\"></a>\n")
                    .append("### ").append(text(item.getEvidenceRef())).append(" · ").append(text(item.getTitle())).append("\n\n")
                    .append("- 来源：").append(text(item.getSourceName())).append("（")
                    .append(textOr(item.getSourceTier(), "MEDIA")).append("）\n")
                    .append("- 发布时间：").append(item.getPublishedAt() == null ? "未提供" : item.getPublishedAt()).append("\n")
                    .append("- 立场：").append(stance(item.getStance())).append("；相关性：")
                    .append(item.getRelevanceScore()).append("/100\n")
                    .append("- 事实摘录：").append(text(item.getFactExcerpt())).append("\n")
                    .append("- 原文：[").append(linkText(item.getTitle())).append("](")
                    .append(text(item.getUrl())).append(")")
                    .append(item.getArticleId() == null ? "" : " · 文章 #" + item.getArticleId()).append("\n\n");
        }
        return out.toString().trim();
    }

    private void heading(StringBuilder out, String title) {
        out.append("## ").append(title).append("\n\n");
    }

    private String refs(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : safeStrings(values)) out.append(ref(value));
        return out.toString();
    }

    private List<String> coreEvidenceRefs(ResearchReportBlueprint blueprint) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        for (ResearchReportBlueprint.KeyInsight item : safe(blueprint.getKeyInsights())) {
            values.addAll(safeStrings(item.getEvidenceRefs()));
        }
        if (values.isEmpty()) {
            for (ResearchReportBlueprint.ArgumentChain item : safe(blueprint.getArgumentChains())) {
                values.addAll(safeStrings(item.getEvidenceRefs()));
            }
        }
        return new ArrayList<String>(values);
    }

    private List<String> subQuestionRefs(ResearchReportBlueprint.SubQuestion item) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        values.addAll(safeStrings(item.getEvidenceRefs()));
        values.addAll(safeStrings(item.getCounterEvidenceRefs()));
        return new ArrayList<String>(values);
    }

    private String ground(String value, List<String> evidenceRefs) {
        String clean = text(value);
        String citations = refs(evidenceRefs);
        if (citations.isEmpty() || clean.isEmpty()) return clean;
        return clean + (clean.endsWith(citations) ? "" : " " + citations);
    }

    private String at(List<String> values, int index, String fallback) {
        return values != null && index >= 0 && index < values.size() && !text(values.get(index)).isEmpty()
                ? values.get(index) : text(fallback);
    }

    private String ref(String value) {
        String clean = text(value);
        return clean.isEmpty() ? "" : "[" + clean + "](#evidence-" + clean.toLowerCase() + ")";
    }

    private String tableExcerpt(String value) {
        String clean = tableCell(text(value));
        return clean.length() <= 420 ? clean : ResearchFactText.completeExcerpt(clean, 420);
    }

    private String tableCell(String value) {
        return text(value).replace("|", "｜").replace("\n", " ");
    }

    private String linkText(String value) {
        return text(value).replace("[", "［").replace("]", "］");
    }

    private String stance(String value) {
        if ("SUPPORT".equals(value)) return "支持";
        if ("COUNTER".equals(value)) return "反向";
        return "中性";
    }

    private String textOr(String value, String fallback) {
        String clean = text(value);
        return clean.isEmpty() ? fallback : clean;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? new ArrayList<T>() : values;
    }

    private List<String> safeStrings(List<String> values) {
        return values == null ? new ArrayList<String>() : values;
    }
}
