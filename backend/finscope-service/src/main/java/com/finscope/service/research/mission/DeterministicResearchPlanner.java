package com.finscope.service.research.mission;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class DeterministicResearchPlanner {
    public ResearchMissionDraft plan(ResearchPlanningInput input) {
        String subject = compact(input == null ? null : input.getSubjectName(), "研究对象");
        String question = compact(input == null ? null : input.getQuestion(), "研究命题");
        ResearchMissionDraft draft = new ResearchMissionDraft();
        draft.setScopeSummary("围绕“" + question + "”核对需求、供给、公司兑现与反方风险");
        draft.setSuccessCriteria(Arrays.asList(
                "至少六条有效证据",
                "至少两个独立来源",
                "同时包含支持与反方或风险证据"));

        List<ResearchMissionTaskDraft> tasks = new ArrayList<ResearchMissionTaskDraft>();
        tasks.add(task("baseline_scan", "基线来源扫描", "现有信息源提供了哪些相关事实？",
                "COLLECT", "source_scan", "BASELINE", Collections.<String>emptyList(),
                null, "先建立已有证据基线", "配置来源中的相关文章"));
        tasks.add(task("search_support", "支持证据搜索", "哪些最新事实支持该命题？",
                "SEARCH", "public_news_search", "SUPPORT", Arrays.asList("baseline_scan"),
                subject + " 资本开支 订单 需求 最新公告", "验证核心驱动是否继续成立", "订单、需求或资本开支一手信息"));
        tasks.add(task("search_counter", "反方证据搜索", "哪些事实可能推翻或限制该命题？",
                "SEARCH", "public_news_search", "COUNTER", Arrays.asList("baseline_scan"),
                subject + " 资本开支 风险 下调 延迟 反方证据", "主动寻找证伪与边界", "下调、延迟、风险或需求转弱证据"));
        tasks.add(task("search_primary", "一手证据搜索", "是否存在公司或机构的一手披露？",
                "SEARCH", "public_news_search", "PRIMARY", Arrays.asList("baseline_scan"),
                subject + " 公司公告 财报 官方数据 原始来源", "提高来源独立性和可追溯性", "公司公告、财报或官方数据"));
        tasks.add(task("assess_evidence", "证据充分性判断", "当前证据是否达到研究合同标准？",
                "ASSESS", "evidence_assess", "ASSESS",
                Arrays.asList("search_support", "search_counter", "search_primary"),
                null, "用确定性门槛决定是否停止搜索", "证据数量、独立来源和正反覆盖"));
        tasks.add(task("synthesize_report", "研究报告合成", "证据支持怎样的阶段性结论？",
                "SYNTHESIS", "report_synthesis", "SYNTHESIS", Arrays.asList("assess_evidence"),
                null, "只用冻结证据形成可溯源结论", "带边界、风险与后续验证的研究报告"));
        draft.setTasks(tasks);
        return draft;
    }

    private ResearchMissionTaskDraft task(String key,
                                          String title,
                                          String question,
                                          String taskType,
                                          String toolCode,
                                          String intent,
                                          List<String> dependencies,
                                          String queryText,
                                          String rationale,
                                          String expectedEvidence) {
        ResearchMissionTaskDraft task = new ResearchMissionTaskDraft();
        task.setTaskKey(key);
        task.setTitle(title);
        task.setQuestion(question);
        task.setTaskType(taskType);
        task.setToolCode(toolCode);
        task.setIntent(intent);
        task.setDependencies(dependencies);
        task.setParallelGroup("public_news_search".equals(toolCode) ? "evidence_search" : null);
        task.setQueryText(queryText);
        task.setRationale(rationale);
        task.setExpectedEvidence(expectedEvidence);
        return task;
    }

    private String compact(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String compacted = value.trim().replaceAll("\\s+", " ");
        return compacted.length() <= 80 ? compacted : compacted.substring(0, 80);
    }
}
