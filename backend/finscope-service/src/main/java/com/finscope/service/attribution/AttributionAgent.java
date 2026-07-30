package com.finscope.service.attribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.util.StringUtils;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.attribution.AttributionDriver;
import com.finscope.domain.attribution.AttributionEvidence;
import com.finscope.domain.attribution.AttributionNarrative;
import com.finscope.domain.attribution.AttributionReport;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.search.evidence.SearchDepth;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 归因研究 Agent：给定标的与行情异动，跑六节点研究工作流，产出结构化归因。
 * 每个节点通过 publisher 推进度、写 agent_run trace；搜索/模型不可用时诚实兜底。
 */
@Service
@Slf4j
public class AttributionAgent {
    @Resource
    private SearchEvidenceGateway searchEvidenceGateway;
    @Resource
    private LlmChatClient llmChatClient;
    @Resource
    private ArticleRepository articleRepository;
    @Resource
    private AgentRunRepository agentRunRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行归因研究。结果写入传入的 report（drivers/evidences/summary/status）。
     */
    public void research(AttributionReport report,
                         Instrument instrument,
                         Double changePct,
                         String taskId,
                         AttributionProgressPublisher publisher) {
        researchInternal(report, instrument, changePct, taskId, publisher, null,
                AttributionResearchProgressListener.NO_OP);
    }

    /** 按 Harness 已校验的研究计划执行，确保计划不是仅用于展示的旁路数据。 */
    public AttributionResearchExecution researchWithPlan(AttributionReport report,
                                 Instrument instrument,
                                 Double changePct,
                                 String taskId,
                                 AttributionProgressPublisher publisher,
                                 AttributionResearchPlan plan) {
        return researchWithPlan(report, instrument, changePct, taskId, publisher, plan,
                AttributionResearchProgressListener.NO_OP);
    }

    /** 执行期只上报事实，具体落库由 Harness 负责。 */
    public AttributionResearchExecution researchWithPlan(AttributionReport report,
                                 Instrument instrument,
                                 Double changePct,
                                 String taskId,
                                 AttributionProgressPublisher publisher,
                                 AttributionResearchPlan plan,
                                 AttributionResearchProgressListener progressListener) {
        return researchInternal(report, instrument, changePct, taskId, publisher, plan,
                progressListener == null ? AttributionResearchProgressListener.NO_OP : progressListener);
    }

    private AttributionResearchExecution researchInternal(AttributionReport report,
                                  Instrument instrument,
                                  Double changePct,
                                  String taskId,
                                  AttributionProgressPublisher publisher,
                                  AttributionResearchPlan plan,
                                  AttributionResearchProgressListener progressListener) {
        List<AttributionEvidence> evidences = new ArrayList<>();
        Set<String> evidenceKeys = new LinkedHashSet<>();
        AttributionResearchExecution execution = new AttributionResearchExecution();

        // ① question-plan
        long t0 = System.currentTimeMillis();
        progressListener.stageStarted("question-plan");
        Map<String, String> queryTracks = plan == null
                ? legacyQueryTracks(instrument, changePct) : plannedQueryTracks(plan);
        List<String> questions = new ArrayList<>(queryTracks.keySet());
        for (String track : queryTracks.values()) execution.track(track);
        publisher.publish(taskId, AttributionProgressEvent.stage("question-plan",
                "已生成 " + questions.size() + " 个研究方向"));
        agentRunRepository.record("attribution:question-plan", "SUCCESS",
                instrument.getCode(), String.join(" | ", questions), null, System.currentTimeMillis() - t0);

        // ② web-search
        long t1 = System.currentTimeMillis();
        if (searchEvidenceGateway.isConfigured(SearchDepth.DEEP)) {
            progressListener.stageStarted("web-search");
            publisher.publish(taskId, AttributionProgressEvent.stage("web-search", "正在检索全网线索"));
            int successfulQueries = 0;
            List<String> failures = new ArrayList<>();
            Set<String> startedTracks = new LinkedHashSet<>();
            long deadline = plan == null ? Long.MAX_VALUE
                    : System.currentTimeMillis() + plan.getBudget().getMaxRunSeconds() * 1000L;
            for (String q : questions) {
                String track = queryTracks.get(q);
                AttributionResearchExecution.TrackResult trackResult = execution.track(track);
                if (startedTracks.add(track)) {
                    progressListener.trackStarted(trackResult);
                }
                if (System.currentTimeMillis() >= deadline) {
                    trackResult.setBudgetStopped(true);
                    trackResult.setLastError("超过研究时间预算");
                    progressListener.trackUpdated(trackResult);
                    report.setWarningMessage("研究达到时间预算，已基于当前证据生成部分报告。");
                    continue;
                }
                trackResult.attempted();
                progressListener.trackUpdated(trackResult);
                try {
                    long remainingMs = deadline == Long.MAX_VALUE ? 15_000L
                            : Math.max(100L, Math.min(15_000L, deadline - System.currentTimeMillis()));
                    boolean chinese = q.matches(".*[\\u4e00-\\u9fa5].*");
                    SearchEvidenceBatch batch = searchEvidenceGateway.search(new SearchEvidenceRequest(
                            q, SearchDepth.DEEP, 4, 4, chinese ? "cn" : "intl",
                            chinese ? "zh" : "en", remainingMs));
                    if (batch.isAllProvidersFailed()) {
                        throw new IllegalStateException("所有搜索供应商均不可用");
                    }
                    successfulQueries++;
                    trackResult.succeeded();
                    for (SearchEvidence hit : batch.getEvidence()) {
                        AttributionEvidence evidence = toEvidence(hit, queryTracks.get(q));
                        if (addEvidenceIfAbsent(evidences, evidenceKeys, evidence)) {
                            trackResult.foundEvidence();
                            publisher.publish(taskId, AttributionProgressEvent.clue(
                                    "找到：" + shorten(hit.getTitle(), 40) + "（" + hit.getSourceTier() + "）"));
                        }
                    }
                    progressListener.trackUpdated(trackResult);
                } catch (Exception ex) {
                    log.warn("归因搜索失败 q={} type={}", q, ex.getClass().getSimpleName());
                    failures.add("公开资料搜索失败");
                    trackResult.setLastError("公开资料搜索失败");
                    progressListener.trackUpdated(trackResult);
                }
            }
            for (String track : queryTracks.values()) {
                progressListener.trackFinished(execution.track(track));
            }
            String searchStatus = failures.isEmpty() ? "SUCCESS" : successfulQueries == 0 ? "FAILED" : "PARTIAL_SUCCESS";
            String errorMessage = failures.isEmpty() ? null : shorten(String.join("；", failures), 300);
            if (!failures.isEmpty()) {
                report.setWarningMessage(successfulQueries == 0 ? "全网搜索暂不可用，本次归因仅基于本地新闻与行情信息。" : "部分全网搜索请求失败，报告已基于可获得的证据生成。");
            }
            agentRunRepository.record("attribution:web-search", searchStatus, String.join(" | ", questions),
                    "queries=" + questions.size() + ", successful=" + successfulQueries + ", uniqueHits=" + evidences.size(),
                    errorMessage, System.currentTimeMillis() - t1);
        } else {
            progressListener.stageStarted("web-search");
            publisher.publish(taskId, AttributionProgressEvent.stage("web-search", "未配置联网搜索，跳过"));
            report.setWarningMessage("未配置联网搜索，本次归因仅基于本地新闻与行情信息。");
            agentRunRepository.record("attribution:web-search", "SKIPPED", null, null,
                    "SearchEvidenceGateway not configured", System.currentTimeMillis() - t1);
            for (String track : queryTracks.values()) {
                execution.track(track).setBudgetStopped(true);
                execution.track(track).setLastError("SearchEvidenceGateway not configured");
                progressListener.trackFinished(execution.track(track));
            }
        }

        // ③ local-recall
        long t2 = System.currentTimeMillis();
        progressListener.stageStarted("local-recall");
        int localCount = recallLocalNews(instrument, evidences, evidenceKeys);
        publisher.publish(taskId, AttributionProgressEvent.stage("local-recall",
                "本地关联到 " + localCount + " 篇已抓文章"));
        agentRunRepository.record("attribution:local-recall", "SUCCESS", instrument.getCode(),
                "local=" + localCount, null, System.currentTimeMillis() - t2);

        // ④ chain-reason（产业链视角提示，纳入 prompt，不单独产出证据）
        progressListener.stageStarted("chain-reason");
        publisher.publish(taskId, AttributionProgressEvent.stage("chain-reason", "已分析产业链关联"));

        // ⑤ evidence-rank
        long t4 = System.currentTimeMillis();
        progressListener.stageStarted("evidence-rank");
        rankEvidences(evidences);
        publisher.publish(taskId, AttributionProgressEvent.stage("evidence-rank", "已整理 " + evidences.size() + " 条有效证据"));
        agentRunRepository.record("attribution:evidence-rank", "SUCCESS", null, "ranked=" + evidences.size(), null, System.currentTimeMillis() - t4);

        // ⑥ attribution-synth
        long t5 = System.currentTimeMillis();
        progressListener.stageStarted("attribution-synth");
        boolean synthesized = synthesize(report, instrument, changePct, evidences);
        report.setEvidences(evidences);
        agentRunRepository.record("attribution:attribution-synth", synthesized ? "SUCCESS" : "FALLBACK",
                instrument.getCode(), report.getSummary(), null, System.currentTimeMillis() - t5);
        return execution;
    }

    // ---- ① 研究问题拆解：按标的类型套模板 ----
    private List<String> planQuestions(Instrument instrument, Double changePct) {
        String name = StringUtils.firstNonBlank(instrument.getName(), instrument.getCode());
        String direction = changePct != null && changePct < 0 ? "下跌" : "上涨";
        List<String> questions = new ArrayList<>();
        String type = instrument.getType() == null ? "STOCK" : instrument.getType().toUpperCase(Locale.ROOT);
        if ("SECTOR".equals(type)) {
            questions.add(name + " 板块今日" + direction + " 政策消息");
            questions.add(name + " 板块 行业动态 龙头股");
        } else if ("FUND".equals(type)) {
            questions.add(name + " 基金 重仓行业 今日" + direction);
            questions.add(name + " 重仓股 最新消息");
        } else {
            questions.add(name + " 今日" + direction + " 原因 公司消息");
            questions.add(name + " 所属板块 行业 今日走势");
            questions.add(name + " 产业链 上下游 最新动态");
        }
        return questions;
    }

    // ---- ③ 本地新闻召回：按别名/名称在已抓文章中匹配 ----
    private int recallLocalNews(Instrument instrument, List<AttributionEvidence> evidences, Set<String> evidenceKeys) {
        List<String> aliases = new ArrayList<>();
        aliases.add(instrument.getCode());
        if (StringUtils.isNotBlank(instrument.getName())) {
            aliases.add(instrument.getName());
        }
        if (StringUtils.isNotBlank(instrument.getAliases())) {
            for (String a : instrument.getAliases().split(",")) {
                if (StringUtils.isNotBlank(a)) {
                    aliases.add(a.trim());
                }
            }
        }
        int count = 0;
        try {
            List<Article> articles = articleRepository.findAll();
            for (Article article : articles) {
                String haystack = (StringUtils.firstNonBlank(article.getTitle(), "") + " "
                        + StringUtils.firstNonBlank(article.getSummary(), "")).toLowerCase(Locale.ROOT);
                for (String alias : aliases) {
                    if (alias.length() >= 2 && haystack.contains(alias.toLowerCase(Locale.ROOT))) {
                        AttributionEvidence evidence = new AttributionEvidence();
                        evidence.setOrigin("LOCAL_NEWS");
                        evidence.setTitle(article.getTitle());
                        evidence.setUrl(article.getUrl());
                        evidence.setSnippet(shorten(StringUtils.firstNonBlank(
                                article.getSummary(), article.getBody(), ""), 160));
                        evidence.setSourceDomain(StringUtils.firstNonBlank(article.getSourceName(), "本地"));
                        evidence.setSourceTier("T2");
                        evidence.setRelevance(70);
                        if (addEvidenceIfAbsent(evidences, evidenceKeys, evidence)) {
                            count++;
                        }
                        break;
                    }
                }
                if (count >= 5) {
                break;
                }
            }
        } catch (Exception ex) {
            log.warn("本地新闻召回失败 code={} message={}", instrument.getCode(), ex.getMessage());
        }
        return count;
    }

    // ---- ⑤ 证据排序：按 tier + relevance ----
    private void rankEvidences(List<AttributionEvidence> evidences) {
        evidences.sort((a, b) -> {
            int ta = tierRank(a.getSourceTier());
            int tb = tierRank(b.getSourceTier());
            if (ta != tb) {
                return Integer.compare(ta, tb);
            }
            int ra = a.getRelevance() == null ? 0 : a.getRelevance();
            int rb = b.getRelevance() == null ? 0 : b.getRelevance();
            return Integer.compare(rb, ra);
        });
    }

    private int tierRank(String tier) {
        if ("T1".equals(tier)) {
            return 0;
        }
        if ("T2".equals(tier)) {
            return 1;
        }
        return 2;
    }

    private AttributionEvidence toEvidence(SearchEvidence hit, String track) {
        AttributionEvidence evidence = new AttributionEvidence();
        evidence.setOrigin("WEB_SEARCH");
        evidence.setTitle(hit.getTitle());
        evidence.setUrl(hit.getUrl());
        evidence.setSnippet(shorten(hit.getContent(), 200));
        evidence.setSourceDomain(hit.getSourceDomain());
        evidence.setSourceTier(hit.getSourceTier());
        evidence.setPublishedAt(hit.getPublishedAt());
        evidence.setRelevance(hit.getProviderScore() == null
                ? Math.max(50, Math.min(100, (int) Math.round(hit.getFusionScore() * 3000D)))
                : (int) Math.round(hit.getProviderScore() * 100));
        evidence.setEventType(StringUtils.firstNonBlank(track, "COMPANY"));
        evidence.setStance("COUNTER".equals(track) ? "COUNTER" : "SUPPORT");
        evidence.setDirectness("COMPANY".equals(track) ? "DIRECT" : "INDIRECT");
        return evidence;
    }

    private Map<String, String> legacyQueryTracks(Instrument instrument, Double changePct) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String query : planQuestions(instrument, changePct)) {
            result.put(query, "COMPANY");
        }
        return result;
    }

    private Map<String, String> plannedQueryTracks(AttributionResearchPlan plan) {
        Map<String, String> result = new LinkedHashMap<>();
        int remaining = plan.getBudget().getMaxQueries();
        for (AttributionResearchPlan.Track track : plan.getTracks()) {
            int trackRemaining = Math.min(track.getMaxQueries(), plan.getBudget().getMaxQueriesPerTrack());
            for (String query : track.getQueries()) {
                if (remaining <= 0 || trackRemaining <= 0) break;
                if (StringUtils.isNotBlank(query)) {
                    result.put(query, track.getCode());
                    remaining--;
                    trackRemaining--;
                }
            }
            if (remaining <= 0) break;
        }
        return result;
    }

    private boolean addEvidenceIfAbsent(List<AttributionEvidence> evidences,
                                        Set<String> evidenceKeys,
                                        AttributionEvidence evidence) {
        String key = evidenceKey(evidence);
        if (!evidenceKeys.add(key)) {
            return false;
        }
        evidences.add(evidence);
        return true;
    }

    private String evidenceKey(AttributionEvidence evidence) {
        String normalizedUrl = normalizeUrl(evidence.getUrl());
        if (StringUtils.isNotBlank(normalizedUrl)) {
            return "url:" + normalizedUrl;
        }
        return "title:" + StringUtils.firstNonBlank(evidence.getTitle(), "").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            String query = uri.getQuery();
            String retainedQuery = query == null ? "" : Arrays.stream(query.split("&"))
                    .filter(part -> {
                        String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                        return !(key.startsWith("utm_") || "gclid".equals(key) || "fbclid".equals(key));
                    })
                    .reduce((left, right) -> left + "&" + right)
                    .orElse("");
            return StringUtils.firstNonBlank(uri.getScheme(), "").toLowerCase(Locale.ROOT) + "://"
                    + StringUtils.firstNonBlank(uri.getHost(), "").toLowerCase(Locale.ROOT)
                    + StringUtils.firstNonBlank(uri.getPath(), "")
                    + (retainedQuery.isEmpty() ? "" : "?" + retainedQuery);
        } catch (IllegalArgumentException ex) {
            return url.trim().toLowerCase(Locale.ROOT);
        }
    }

    private String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }

    // ---- ⑥ 综合归因：优先 LLM，失败/无证据则诚实兜底 ----
    private boolean synthesize(AttributionReport report,
                               Instrument instrument,
                               Double changePct,
                               List<AttributionEvidence> evidences) {
        report.setDisclaimer("本分析基于当日公开信息综合，可能含未证实传闻，非投资建议。");
        if (llmChatClient != null && llmChatClient.isConfigured() && !evidences.isEmpty()) {
            try {
                String raw = llmChatClient.complete(synthSystemPrompt(), synthUserPrompt(instrument, changePct, evidences));
                if (parseSynthResult(report, raw)) {
                    ensureNarrative(report, instrument, changePct, evidences);
                    return true;
                }
            } catch (Exception ex) {
                log.warn("归因综合失败 code={} message={}", instrument.getCode(), ex.getMessage());
            }
        }
        fallbackSynthesize(report, instrument, changePct, evidences);
        ensureNarrative(report, instrument, changePct, evidences);
        return false;
    }

    private String synthSystemPrompt() {
        return "你是 FinScope 标的归因研究员。基于给定的行情与新闻证据，分析标的今日涨跌的可能原因。"
                + "要求：只依据证据，不编造；区分事实与传闻；传闻降低置信度；找不到明确原因时如实说明。"
                + "只返回 JSON，不做买卖建议。";
    }

    String synthUserPrompt(Instrument instrument, Double changePct, List<AttributionEvidence> evidences) {
        StringBuilder builder = new StringBuilder();
        builder.append("输出格式:{\"summary\":\"综合归因\",\"narrative\":{")
                .append("\"plainSummary\":\"2-3句白话核心结论\",\"event\":\"今天发生了什么\",")
                .append("\"instrumentLink\":\"为什么影响该标的\",\"whyToday\":\"为什么在今天集中反应\",")
                .append("\"causalSteps\":[\"因果节点\"],\"amplifiers\":[\"放大因素\"],")
                .append("\"dampeners\":[\"缓冲或反方因素\"]},\"drivers\":[{\"claim\":\"原因\",")
                .append("\"role\":\"TRIGGER|AMPLIFIER|BACKGROUND|COUNTER\",")
                .append("\"plainExplanation\":\"不用术语也能读懂的解释\",")
                .append("\"marketInterpretation\":\"市场为什么在意以及正在交易什么\",")
                .append("\"expectationShift\":\"原本预期 → 现在预期\",")
                .append("\"priceImpact\":\"预期变化如何影响价格\",")
                .append("\"explanatoryPower\":\"HIGH|MID|LOW\",")
                .append("\"explanatoryPowerReason\":\"解释力度的判断依据和边界\",")
                .append("\"impactLevel\":\"HIGH|MID|LOW\",\"confidence\":\"HIGH|MID|LOW\",\"detail\":\"详细解释\",")
                .append("\"facts\":[\"明确事实\"],\"transmissionPath\":\"事件到价格的传导链\",")
                .append("\"counterEvidence\":\"反证或局限\",\"observationWindow\":\"后续观察窗口\",")
                .append("\"evidenceUrls\":[\"证据URL\"]}],\"uncertainties\":[\"不确定性\"],")
                .append("\"observationWindows\":[\"整体观察项\"],\"disclaimer\":\"诚实说明\"}\n")
                .append("要求给出 4-6 个不重复的驱动因素，覆盖公司、行业、宏观/政策、市场联动和反证；证据不足必须降低置信度。\n")
                .append("先讲清：今天发生了什么 → 预期改变了什么 → 为什么影响该标的 → 为什么今天集中反应 → 价格结果。")
                .append("直接触发、放大因素、背景和反方必须分开；使用普通中文，术语出现时在同一句解释。\n")
                .append("facts 只写证据明确支持的事实；AI 解读不得重复事实原句。")
                .append("marketInterpretation 回答市场为什么在意；expectationShift 使用‘原本预期 → 现在预期’。")
                .append("priceImpact 必须落到盈利预期、估值倍数、风险溢价或资金行为中的至少一种。")
                .append("explanatoryPower 综合证据直接性、时间贴近度、价格方向一致性与反证。")
                .append("不得虚构数字、业务暴露或投资者行为；推断使用‘可能、意味着、市场倾向于’等边界措辞。\n")
                .append(typeTransmissionInstruction(instrument)).append("\n");
        builder.append("标的:").append(StringUtils.firstNonBlank(instrument.getName(), instrument.getCode()))
                .append("(").append(instrument.getCode()).append(")\n");
        builder.append("类型:").append(instrument.getType()).append("\n");
        builder.append("今日涨跌幅:").append(changePct == null ? "未知" : changePct + "%").append("\n");
        builder.append("证据列表:\n");
        int index = 1;
        for (AttributionEvidence e : evidences) {
            builder.append(index++).append(". [").append(e.getSourceTier()).append("] ")
                    .append(StringUtils.firstNonBlank(e.getTitle(), "")).append(" - ")
                    .append(StringUtils.firstNonBlank(e.getSnippet(), ""))
                    .append(" URL=").append(StringUtils.firstNonBlank(e.getUrl(), "无")).append("\n");
            if (index > 10) {
                break;
            }
        }
        return builder.toString();
    }

    boolean parseSynthResult(AttributionReport report, String raw) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(raw));
            String summary = root.path("summary").asText("");
            if (StringUtils.isBlank(summary)) {
                return false;
            }
            report.setSummary(summary.trim());
            if (StringUtils.isNotBlank(root.path("disclaimer").asText(""))) {
                report.setDisclaimer(root.path("disclaimer").asText().trim());
            }
            JsonNode narrativeNode = root.path("narrative");
            if (narrativeNode.isObject()) {
                AttributionNarrative narrative = new AttributionNarrative();
                narrative.setPlainSummary(narrativeNode.path("plainSummary").asText("").trim());
                narrative.setEvent(narrativeNode.path("event").asText("").trim());
                narrative.setInstrumentLink(narrativeNode.path("instrumentLink").asText("").trim());
                narrative.setWhyToday(narrativeNode.path("whyToday").asText("").trim());
                narrative.setCausalSteps(readStringArray(narrativeNode.path("causalSteps")));
                narrative.setAmplifiers(readStringArray(narrativeNode.path("amplifiers")));
                narrative.setDampeners(readStringArray(narrativeNode.path("dampeners")));
                report.setNarrative(narrative);
            }
            List<AttributionDriver> drivers = new ArrayList<>();
            JsonNode driverNodes = root.path("drivers");
            if (driverNodes.isArray()) {
                for (JsonNode node : driverNodes) {
                    String claim = node.path("claim").asText("");
                    if (StringUtils.isBlank(claim)) {
                        continue;
                    }
                    AttributionDriver driver = new AttributionDriver();
                    driver.setClaim(claim.trim());
                    driver.setRole(normRole(node.path("role").asText("BACKGROUND")));
                    driver.setPlainExplanation(node.path("plainExplanation").asText("").trim());
                    driver.setMarketInterpretation(node.path("marketInterpretation").asText("").trim());
                    driver.setExpectationShift(node.path("expectationShift").asText("").trim());
                    driver.setPriceImpact(node.path("priceImpact").asText("").trim());
                    String explanatoryPower = node.path("explanatoryPower").asText("").trim();
                    driver.setExplanatoryPower(StringUtils.isBlank(explanatoryPower)
                            ? "" : normLevel(explanatoryPower));
                    driver.setExplanatoryPowerReason(node.path("explanatoryPowerReason").asText("").trim());
                    driver.setImpactLevel(normLevel(node.path("impactLevel").asText("MID")));
                    driver.setConfidence(normLevel(node.path("confidence").asText("MID")));
                    driver.setDetail(node.path("detail").asText("").trim());
                    driver.setFacts(readStringArray(node.path("facts")));
                    driver.setTransmissionPath(node.path("transmissionPath").asText("").trim());
                    driver.setCounterEvidence(node.path("counterEvidence").asText("").trim());
                    driver.setObservationWindow(node.path("observationWindow").asText("").trim());
                    driver.setEvidenceUrls(readStringArray(node.path("evidenceUrls")));
                    drivers.add(driver);
                }
            }
            report.setDrivers(drivers);
            report.setUncertainties(readStringArray(root.path("uncertainties")));
            report.setObservationWindows(readStringArray(root.path("observationWindows")));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void fallbackSynthesize(AttributionReport report,
                                    Instrument instrument,
                                    Double changePct,
                                    List<AttributionEvidence> evidences) {
        if (evidences.isEmpty()) {
            report.setSummary("今日未检索到明显消息面驱动，涨跌可能源于板块联动或市场情绪。");
            report.setDrivers(new ArrayList<>());
            return;
        }
        String direction = changePct != null && changePct < 0 ? "下跌" : "上涨";
        report.setSummary("检索到 " + evidences.size() + " 条相关信息，" + direction
                + "可能与近期相关消息及板块情绪有关，具体见驱动因素。");
        List<AttributionDriver> drivers = new ArrayList<>();
        int limit = Math.min(3, evidences.size());
        for (int i = 0; i < limit; i++) {
            AttributionEvidence e = evidences.get(i);
            AttributionDriver driver = new AttributionDriver();
            driver.setClaim(StringUtils.firstNonBlank(e.getTitle(), "相关消息"));
            driver.setRole(i == 0 ? "TRIGGER" : "BACKGROUND");
            driver.setImpactLevel(i == 0 ? "MID" : "LOW");
            driver.setConfidence("T1".equals(e.getSourceTier()) ? "MID" : "LOW");
            driver.setDetail(StringUtils.firstNonBlank(e.getSnippet(), ""));
            driver.setPlainExplanation(StringUtils.firstNonBlank(e.getSnippet(), e.getTitle(), "该线索可能影响市场预期。"));
            drivers.add(driver);
        }
        report.setDrivers(drivers);
    }

    void ensureNarrative(AttributionReport report,
                         Instrument instrument,
                         Double changePct,
                         List<AttributionEvidence> evidences) {
        AttributionNarrative narrative = report.getNarrative();
        if (narrative == null) {
            narrative = new AttributionNarrative();
            report.setNarrative(narrative);
        }
        if (StringUtils.isBlank(narrative.getPlainSummary())) {
            narrative.setPlainSummary(report.getSummary());
        }
        List<AttributionEvidence> currentEvidence = new ArrayList<>();
        if (evidences != null) {
            for (AttributionEvidence evidence : evidences) {
                if (evidence != null && !evidence.isHistoricalContext()) currentEvidence.add(evidence);
            }
        }
        AttributionEvidence firstCurrent = currentEvidence.isEmpty() ? null : currentEvidence.get(0);
        if (StringUtils.isBlank(narrative.getEvent())) {
            narrative.setEvent(firstCurrent == null
                    ? "当日未检索到可确认的直接触发信息"
                    : StringUtils.firstNonBlank(firstCurrent.getTitle(), firstCurrent.getSnippet(), "当日公开线索"));
        }
        AttributionDriver primary = report.getDrivers() == null || report.getDrivers().isEmpty()
                ? null : report.getDrivers().get(0);
        if (StringUtils.isBlank(narrative.getInstrumentLink())) {
            String primaryExplanation = primary == null ? null
                    : StringUtils.firstNonBlank(primary.getPlainExplanation(), primary.getDetail());
            narrative.setInstrumentLink(StringUtils.firstNonBlank(primaryExplanation,
                    fallbackInstrumentLink(instrument)));
        }
        if (StringUtils.isBlank(narrative.getWhyToday())) {
            narrative.setWhyToday(firstCurrent == null
                    ? "当前公开信息不足以确认行情在当日集中反应的具体触发点。"
                    : "该公开线索与当日价格异动同时出现，具体时点仍需结合公告时间、板块走势和成交数据继续确认。");
        }
        if (narrative.getCausalSteps() == null || narrative.getCausalSteps().isEmpty()) {
            List<String> steps = new ArrayList<>();
            addStep(steps, narrative.getEvent());
            if (primary != null && StringUtils.isNotBlank(primary.getTransmissionPath())) {
                for (String step : primary.getTransmissionPath().split("\\s*→\\s*")) addStep(steps, step);
            } else if (primary != null) {
                addStep(steps, StringUtils.firstNonBlank(primary.getPlainExplanation(), primary.getClaim()));
            }
            addStep(steps, priceResult(changePct));
            narrative.setCausalSteps(steps);
        }
        if (narrative.getAmplifiers() == null) narrative.setAmplifiers(new ArrayList<String>());
        if (narrative.getDampeners() == null) narrative.setDampeners(new ArrayList<String>());
    }

    private String typeTransmissionInstruction(Instrument instrument) {
        String type = instrument.getType() == null ? "STOCK" : instrument.getType().toUpperCase(Locale.ROOT);
        if ("FUND".equals(type)) {
            return "基金传导必须说明：行业或核心持仓变化 → 组合暴露 → 净值或交易价格；不要把基金写成经营主体。";
        }
        if ("SECTOR".equals(type)) {
            return "板块传导必须说明：政策或需求变化 → 龙头股反应 → 成分股扩散 → 板块涨跌。";
        }
        return "股票传导必须说明：事件 → 盈利或风险预期 → 公司暴露 → 板块或资金行为 → 股价。";
    }

    private String fallbackInstrumentLink(Instrument instrument) {
        String type = instrument.getType() == null ? "STOCK" : instrument.getType().toUpperCase(Locale.ROOT);
        if ("FUND".equals(type)) return "该基金会通过相关行业或核心持仓的价格变化受到影响，具体组合暴露仍需核验。";
        if ("SECTOR".equals(type)) return "该事件可能通过龙头股和成分股扩散影响板块表现，当前扩散范围仍需核验。";
        return "当前证据显示该标的与上述事件相关，但具体业务暴露仍需公开资料进一步确认。";
    }

    private String priceResult(Double changePct) {
        if (changePct == null) return "价格出现异动";
        if (changePct < 0) return "卖出压力集中反映为股价或净值下跌";
        if (changePct > 0) return "买入力量集中反映为股价或净值上涨";
        return "多空力量接近平衡，价格变化有限";
    }

    private void addStep(List<String> steps, String value) {
        if (steps.size() >= 4 || StringUtils.isBlank(value)) return;
        String normalized = value.trim();
        if (!steps.contains(normalized)) steps.add(normalized);
    }

    private String normRole(String value) {
        String role = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("TRIGGER".equals(role) || "AMPLIFIER".equals(role)
                || "BACKGROUND".equals(role) || "COUNTER".equals(role)) {
            return role;
        }
        return "BACKGROUND";
    }

    private String normLevel(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("HIGH".equals(v) || "MID".equals(v) || "LOW".equals(v)) {
            return v;
        }
        return "MID";
    }

    private List<String> readStringArray(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode value : node) {
                if (StringUtils.isNotBlank(value.asText())) result.add(value.asText().trim());
            }
        }
        return result;
    }

    private String extractJson(String raw) {
        String value = StringUtils.firstNonBlank(raw, "").trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }
}
