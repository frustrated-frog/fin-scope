package com.finscope.service.research.agent.tool;

import com.finscope.domain.fetch.FetchRun;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.domain.source.Source;
import com.finscope.service.fetch.FetchService;
import com.finscope.service.research.ResearchRunOutputService;
import com.finscope.service.research.mission.ResearchSearchSourceFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PublicNewsSearchTool implements ResearchAgentTool {
    private final FetchService fetchService;
    private final ResearchRunOutputService outputService;
    private final ResearchSearchSourceFactory sourceFactory;

    public PublicNewsSearchTool(FetchService fetchService,
                                ResearchRunOutputService outputService,
                                ResearchSearchSourceFactory sourceFactory) {
        this.fetchService = fetchService;
        this.outputService = outputService;
        this.sourceFactory = sourceFactory;
    }

    @Override
    public ResearchToolDescriptor descriptor() {
        ResearchToolDescriptor value = new ResearchToolDescriptor();
        value.setCode("public_news_search");
        value.setName("公开新闻搜索");
        value.setDescription("按自然语言关键词搜索公开新闻，并通过现有摄入链路生成文章和证据");
        Map<String, String> input = new LinkedHashMap<String, String>();
        input.put("query", "2..180字符自然语言关键词");
        input.put("intent", "SUPPORT|COUNTER|PRIMARY|UPDATE");
        value.setInputSchema(input);
        value.setOutputSchema(Collections.singletonMap("observation", "证据与独立来源增量"));
        value.setTimeoutMs(15_000);
        value.setReadOnly(true);
        value.setParallelizable(false);
        value.setRiskLevel("LOW");
        value.setBudgetType("EXTERNAL_ACTION");
        return value;
    }

    @Override
    public void validate(Map<String, Object> arguments) {
        if (arguments == null || !arguments.keySet().equals(
                new java.util.HashSet<String>(Arrays.asList("query", "intent")))) {
            throw new IllegalArgumentException("公开新闻搜索参数必须且只能包含 query 和 intent");
        }
        String query = text(arguments.get("query"));
        String intent = text(arguments.get("intent"));
        if (query.length() < 2 || query.length() > 180 || query.contains("://")) {
            throw new IllegalArgumentException("公开新闻搜索 query 未通过安全校验");
        }
        if (!Arrays.asList("SUPPORT", "COUNTER", "PRIMARY", "UPDATE").contains(intent)) {
            throw new IllegalArgumentException("公开新闻搜索 intent 未通过安全校验");
        }
    }

    @Override
    public ResearchToolObservation execute(ResearchAgentToolContext context, Map<String, Object> arguments) {
        validate(arguments);
        int evidenceBefore = outputService.count(context.getResearchRunId(), ResearchRunOutputService.EVIDENCE);
        int sourcesBefore = outputService.countDistinctArticleSources(context.getResearchRunId());
        ResearchMissionTask task = task(context, arguments);
        Source source = sourceFactory.create(task);
        FetchRun fetchRun = fetchService.fetch(source);
        int evidenceAfter = outputService.count(context.getResearchRunId(), ResearchRunOutputService.EVIDENCE);
        int sourcesAfter = outputService.countDistinctArticleSources(context.getResearchRunId());
        int evidenceDelta = Math.max(0, evidenceAfter - evidenceBefore);
        int sourceDelta = Math.max(0, sourcesAfter - sourcesBefore);

        ResearchToolObservation value = new ResearchToolObservation();
        value.setEvidenceDelta(evidenceDelta);
        value.setSourceDelta(sourceDelta);
        value.setStateHash(evidenceAfter + ":" + sourcesAfter);
        if (fetchRun != null && fetchRun.getId() != null) {
            value.setDataRefs(Collections.singletonList("fetch-run:" + fetchRun.getId()));
        }
        if (fetchRun == null || !"SUCCESS".equals(fetchRun.getStatus())) {
            value.setStatus("RETRYABLE_ERROR");
            value.setObservationSummary("公开新闻搜索失败：" + safe(fetchRun == null ? null : fetchRun.getErrorMessage()));
            value.setNewInformation("没有得到可用搜索结果");
            value.setErrorType("SEARCH_FETCH_FAILED");
            value.setRetryable(true);
            return value;
        }
        value.setStatus(evidenceDelta == 0 && sourceDelta == 0 ? "NO_PROGRESS" : "SUCCESS");
        value.setObservationSummary("公开新闻搜索完成：抓取=" + fetchRun.getSuccessCount()
                + "，重复=" + fetchRun.getDuplicateCount() + "，新增证据=" + evidenceDelta
                + "，新增独立来源=" + sourceDelta);
        value.setNewInformation(evidenceDelta == 0 && sourceDelta == 0
                ? "本次查询没有改变当前证据状态"
                : "研究证据状态已更新为 evidence=" + evidenceAfter + ", sources=" + sourcesAfter);
        value.setRetryable(false);
        return value;
    }

    private ResearchMissionTask task(ResearchAgentToolContext context, Map<String, Object> arguments) {
        ResearchMissionTask value = new ResearchMissionTask();
        value.setTaskKey(context.decisionTaskKey());
        value.setTitle(intentTitle(text(arguments.get("intent"))));
        value.setQuestion("该查询能否补齐当前研究证据缺口？");
        value.setTaskType("SEARCH");
        value.setToolCode("public_news_search");
        value.setIntent(text(arguments.get("intent")));
        value.setQueryText(text(arguments.get("query")));
        return value;
    }

    private String intentTitle(String intent) {
        if ("COUNTER".equals(intent)) return "反方证据搜索";
        if ("SUPPORT".equals(intent)) return "支持证据搜索";
        if ("PRIMARY".equals(intent)) return "一手证据搜索";
        return "最新进展搜索";
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "未知抓取错误";
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240);
    }
}
