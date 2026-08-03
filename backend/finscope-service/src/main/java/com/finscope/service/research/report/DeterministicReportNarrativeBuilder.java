package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class DeterministicReportNarrativeBuilder {
    public ResearchReportNarrative build(ResearchThesis thesis,
                                         ResearchReportBlueprint blueprint,
                                         List<ResearchEvidenceDossier> dossier) {
        List<ResearchEvidenceDossier> evidence = dossier == null
                ? Collections.<ResearchEvidenceDossier>emptyList() : dossier;
        ResearchReportNarrative result = new ResearchReportNarrative();
        result.setExecutiveSummary(executiveSummary(thesis, blueprint, evidence));
        result.setWhatHappened(whatHappened(thesis, evidence));
        for (ResearchReportBlueprint.SubQuestion item : blueprint.getSubQuestions()) {
            result.getSubQuestionAnalysis().add(subQuestionAnalysis(thesis, item));
        }
        for (ResearchReportBlueprint.ArgumentChain item : blueprint.getArgumentChains()) {
            result.getArgumentAnalysis().add(argumentAnalysis(thesis, item));
        }
        result.setCounterAnalysis(counterAnalysis(thesis, blueprint.getStrongestCounterargument()));
        for (ResearchReportBlueprint.Scenario item : blueprint.getScenarios()) {
            result.getScenarioAnalysis().add(scenarioAnalysis(thesis, item));
        }
        result.setKnowledgeSynthesis(knowledgeSynthesis(thesis, blueprint));
        result.setMonitoringPlan(monitoringPlan(thesis, blueprint));
        return result;
    }

    private String executiveSummary(ResearchThesis thesis,
                                    ResearchReportBlueprint blueprint,
                                    List<ResearchEvidenceDossier> evidence) {
        StringBuilder out = new StringBuilder();
        out.append("围绕“").append(value(thesis.getQuestion())).append("”，当前可以形成的阶段性答案是：")
                .append(value(blueprint.getDirectAnswer())).append(' ');
        for (ResearchReportBlueprint.KeyInsight item : blueprint.getKeyInsights()) {
            out.append(value(item.getFinding())).append("。其研究含义是")
                    .append(value(item.getMeaning())).append(' ').append(refs(item.getEvidenceRefs())).append(' ');
        }
        out.append("对").append(value(thesis.getSubjectName())).append("的判断仍需保留两层边界：第一，公开材料的时间和口径并不完全一致；")
                .append("第二，短期现象只有在后续正式披露和结果变量中得到验证，才可以被解释为持续趋势。")
                .append("因此，本报告把已经确认的事实、基于事实的机制推理和带条件的判断分别呈现，并把最强反方解释和失效条件放在同一证据框架内。")
                .append("本次证据档案覆盖").append(evidence.size()).append("项材料；数量只代表覆盖面，不代替来源质量和独立性判断。");
        return out.toString();
    }

    private String whatHappened(ResearchThesis thesis, List<ResearchEvidenceDossier> evidence) {
        StringBuilder out = new StringBuilder("本次材料显示，");
        for (ResearchEvidenceDossier item : evidence) {
            out.append(value(item.getFactExcerpt())).append(' ').append('[').append(item.getEvidenceRef()).append("] ");
        }
        out.append("这些材料共同描述了").append(value(thesis.getSubjectName()))
                .append("在当前观察期内的对象特定变化。理解变化时需要先区分事实发生的时间、来源和口径，再判断不同材料是否描述同一事件。")
                .append("同源转载只能增加信息可见度，不能被当作独立确认；只有新增事实改变关键变量，才会真正改变研究结论。")
                .append("从研究问题出发，当前最重要的不是把所有报道相加，而是检查这些事实能否形成从外部驱动、关键变量到可观察结果的连续传导。");
        return out.toString();
    }

    private String subQuestionAnalysis(ResearchThesis thesis, ResearchReportBlueprint.SubQuestion item) {
        return "该子问题把总命题拆成可验证环节。现有材料给出的回答是“" + value(item.getAnswer()) + "” "
                + refs(item.getEvidenceRefs()) + "。这一回答对“" + value(thesis.getQuestion()) + "”的作用在于："
                + value(item.getImpact()) + "。如果支持材料与反方材料描述的是不同时间窗口、不同业务范围或不同统计口径，"
                + "就不能机械抵消；应先统一口径，再观察关键结果是否按推理方向兑现。当前未知项包括"
                + String.join("；", item.getUnknowns()) + "，因此该子问题只形成阶段性判断。";
    }

    private String argumentAnalysis(ResearchThesis thesis, ResearchReportBlueprint.ArgumentChain item) {
        return "这条证据链以“" + value(item.getFact()) + "”为事实起点 " + refs(item.getEvidenceRefs())
                + "。它与“" + value(thesis.getQuestion()) + "”的联系不是标题层面的相关，而是通过以下传导建立："
                + value(item.getInference()) + "。据此，当前判断为“" + value(item.getJudgment()) + "”。"
                + "需要特别保留的替代解释是“" + value(item.getAlternativeExplanation()) + "”。"
                + "若后续事实只重复现象而没有兑现到结果变量，这条链的解释力不会因为报道数量增加而提高；"
                + "若独立来源和后续结果同时确认同一方向，才可以提高其在总判断中的权重。";
    }

    private String counterAnalysis(ResearchThesis thesis, ResearchReportBlueprint.Counterargument counter) {
        if (counter == null) return "当前没有形成可验证的独立反方解释，结论必须因反方覆盖不足而主动限制置信度。";
        return "对“" + value(thesis.getQuestion()) + "”而言，最强反方不是一般性的风险提示，而是“"
                + value(counter.getClaim()) + "” " + refs(counter.getEvidenceRefs()) + "。当前回应是“"
                + value(counter.getResponse()) + "”。这意味着主结论与反方解释需要由同一组后续结果区分，不能只比较正负面标题数量。"
                + "当出现“" + String.join("；", counter.getBecomesDominantWhen()) + "”时，反方应被提升为更优解释，"
                + "当前结论也必须同步降级或推翻。";
    }

    private String scenarioAnalysis(ResearchThesis thesis, ResearchReportBlueprint.Scenario item) {
        return "在" + value(item.getName()) + "中，触发条件是“" + value(item.getTrigger()) + "”。"
                + "其传导路径为“" + value(item.getMechanism()) + "”，需要观察的结果是“"
                + value(item.getObservableResult()) + "”。如果这些结果出现，对“" + value(thesis.getQuestion())
                + "”的影响为“" + value(item.getImpact()) + "” " + refs(item.getEvidenceRefs()) + "。"
                + "该情景不是概率预测，而是把当前认识转化为可证伪条件；未触发时不应提前把它写成已经发生的事实。";
    }

    private String knowledgeSynthesis(ResearchThesis thesis, ResearchReportBlueprint blueprint) {
        StringBuilder out = new StringBuilder();
        out.append("综合现有证据，关于").append(value(thesis.getSubjectName())).append("可以形成的认识，不是一个脱离条件的方向标签，")
                .append("而是一组可以随新事实更新的判断：").append(value(blueprint.getDirectAnswer())).append(' ')
                .append(refs(coreRefs(blueprint))).append("。当前最容易出现的误读，是把单次价格、单篇报道或同源转载直接解释为长期结果；")
                .append("这些现象最多提供线索，不能替代连续经营、行业或监管事实。仍然未知的部分包括")
                .append(String.join("；", blueprint.getUnknowns())).append("。只要这些未知项没有被新证据覆盖，")
                .append("报告就应保留阶段性和可修订属性，而不是通过增加通用文字制造确定感。");
        return out.toString();
    }

    private String monitoringPlan(ResearchThesis thesis, ResearchReportBlueprint blueprint) {
        StringBuilder out = new StringBuilder("下一轮更新不应重复本轮搜索，而应围绕能改变“")
                .append(value(thesis.getQuestion())).append("”结论的变量定向取证。具体顺序是：先补一手正式披露，")
                .append("再补独立行业或业务统计，最后检查反向风险是否得到重复确认。只要关键指标连续改善且结果同步兑现，")
                .append("可以上调结论；若关键指标连续转弱、正式披露否定当前事实，或反方条件成为现实，结论应下调或失效。")
                .append("当前跟踪项包括：");
        for (ResearchReportBlueprint.WatchItem item : blueprint.getWatchItems()) {
            out.append(value(item.getMetric())).append("，以“").append(value(item.getBaseline())).append("”为基线，按“")
                    .append(value(item.getFrequency())).append("”更新；");
        }
        return out.toString();
    }

    private List<String> coreRefs(ResearchReportBlueprint blueprint) {
        java.util.LinkedHashSet<String> refs = new java.util.LinkedHashSet<String>();
        for (ResearchReportBlueprint.KeyInsight item : blueprint.getKeyInsights()) refs.addAll(item.getEvidenceRefs());
        return new java.util.ArrayList<String>(refs);
    }

    private String refs(List<String> refs) {
        StringBuilder out = new StringBuilder();
        if (refs != null) for (String ref : refs) out.append('[').append(ref).append(']');
        return out.toString();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
