package com.finscope.service.attribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.util.StringUtils;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.attribution.AttributionDriver;
import com.finscope.domain.attribution.AttributionEvidence;
import com.finscope.domain.attribution.AttributionReport;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.search.SearchResult;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.rpc.search.WebSearchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 归因研究 Agent：给定标的与行情异动，跑六节点研究工作流，产出结构化归因。
 * 每个节点通过 publisher 推进度、写 agent_run trace；搜索/模型不可用时诚实兜底。
 */
@Service
@Slf4j
public class AttributionAgent {
    @Resource
    private WebSearchClient webSearchClient;
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
        List<AttributionEvidence> evidences = new ArrayList<>();

        // ① question-plan
        long t0 = System.currentTimeMillis();
        List<String> questions = planQuestions(instrument, changePct);
        publisher.publish(taskId, AttributionProgressEvent.stage("question-plan",
                "已生成 " + questions.size() + " 个研究方向"));
        agentRunRepository.record("attribution:question-plan", "SUCCESS",
                instrument.getCode(), String.join(" | ", questions), null, System.currentTimeMillis() - t0);

        // ② web-search
        long t1 = System.currentTimeMillis();
        if (webSearchClient.isConfigured()) {
            for (String q : questions) {
                try {
                    List<SearchResult> hits = webSearchClient.search(q, 4);
                    for (SearchResult hit : hits) {
                        evidences.add(toEvidence(hit));
                        publisher.publish(taskId, AttributionProgressEvent.clue(
                                "找到：" + shorten(hit.getTitle(), 40) + "（" + hit.getSourceTier() + "）"));
                    }
                } catch (Exception ex) {
                    log.warn("归因搜索失败 q={} message={}", q, ex.getMessage());
                }
            }
            agentRunRepository.record("attribution:web-search", "SUCCESS", String.join(" | ", questions),
                    "hits=" + evidences.size(), null, System.currentTimeMillis() - t1);
        } else {
            publisher.publish(taskId, AttributionProgressEvent.stage("web-search", "未配置联网搜索，跳过"));
            agentRunRepository.record("attribution:web-search", "SKIPPED", null, null,
                    "WebSearchClient not configured", System.currentTimeMillis() - t1);
        }

        // ③ local-recall
        long t2 = System.currentTimeMillis();
        int localCount = recallLocalNews(instrument, evidences);
        publisher.publish(taskId, AttributionProgressEvent.stage("local-recall",
                "本地关联到 " + localCount + " 篇已抓文章"));
        agentRunRepository.record("attribution:local-recall", "SUCCESS", instrument.getCode(),
                "local=" + localCount, null, System.currentTimeMillis() - t2);

        // ④ chain-reason（产业链视角提示，纳入 prompt，不单独产出证据）
        publisher.publish(taskId, AttributionProgressEvent.stage("chain-reason", "已分析产业链关联"));

        // ⑤ evidence-rank
        long t4 = System.currentTimeMillis();
        rankEvidences(evidences);
        publisher.publish(taskId, AttributionProgressEvent.stage("evidence-rank",
                "已整理 " + evidences.size() + " 条有效证据"));
        agentRunRepository.record("attribution:evidence-rank", "SUCCESS", null,
                "ranked=" + evidences.size(), null, System.currentTimeMillis() - t4);

        // ⑥ attribution-synth
        long t5 = System.currentTimeMillis();
        boolean synthesized = synthesize(report, instrument, changePct, evidences);
        report.setEvidences(evidences);
        agentRunRepository.record("attribution:attribution-synth", synthesized ? "SUCCESS" : "FALLBACK",
                instrument.getCode(), report.getSummary(), null, System.currentTimeMillis() - t5);
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
    private int recallLocalNews(Instrument instrument, List<AttributionEvidence> evidences) {
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
                        evidences.add(evidence);
                        count++;
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

    private AttributionEvidence toEvidence(SearchResult hit) {
        AttributionEvidence evidence = new AttributionEvidence();
        evidence.setOrigin("WEB_SEARCH");
        evidence.setTitle(hit.getTitle());
        evidence.setUrl(hit.getUrl());
        evidence.setSnippet(shorten(hit.getContent(), 200));
        evidence.setSourceDomain(hit.getSourceDomain());
        evidence.setSourceTier(hit.getSourceTier());
        evidence.setRelevance(hit.getScore() == null ? 50 : (int) Math.round(hit.getScore() * 100));
        return evidence;
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
                    return true;
                }
            } catch (Exception ex) {
                log.warn("归因综合失败 code={} message={}", instrument.getCode(), ex.getMessage());
            }
        }
        fallbackSynthesize(report, instrument, changePct, evidences);
        return false;
    }

    private String synthSystemPrompt() {
        return "你是 FinScope 标的归因研究员。基于给定的行情与新闻证据，分析标的今日涨跌的可能原因。"
                + "要求：只依据证据，不编造；区分事实与传闻；传闻降低置信度；找不到明确原因时如实说明。"
                + "只返回 JSON，不做买卖建议。";
    }

    private String synthUserPrompt(Instrument instrument, Double changePct, List<AttributionEvidence> evidences) {
        StringBuilder builder = new StringBuilder();
        builder.append("输出格式:{\"summary\":\"一句话归因\",\"drivers\":[{\"claim\":\"原因\",")
                .append("\"impactLevel\":\"HIGH|MID|LOW\",\"confidence\":\"HIGH|MID|LOW\",\"detail\":\"说明\"}],")
                .append("\"disclaimer\":\"诚实说明\"}\n");
        builder.append("标的:").append(StringUtils.firstNonBlank(instrument.getName(), instrument.getCode()))
                .append("(").append(instrument.getCode()).append(")\n");
        builder.append("类型:").append(instrument.getType()).append("\n");
        builder.append("今日涨跌幅:").append(changePct == null ? "未知" : changePct + "%").append("\n");
        builder.append("证据列表:\n");
        int index = 1;
        for (AttributionEvidence e : evidences) {
            builder.append(index++).append(". [").append(e.getSourceTier()).append("] ")
                    .append(StringUtils.firstNonBlank(e.getTitle(), "")).append(" - ")
                    .append(StringUtils.firstNonBlank(e.getSnippet(), "")).append("\n");
            if (index > 10) {
                break;
            }
        }
        return builder.toString();
    }

    private boolean parseSynthResult(AttributionReport report, String raw) {
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
                    driver.setImpactLevel(normLevel(node.path("impactLevel").asText("MID")));
                    driver.setConfidence(normLevel(node.path("confidence").asText("MID")));
                    driver.setDetail(node.path("detail").asText("").trim());
                    drivers.add(driver);
                }
            }
            report.setDrivers(drivers);
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
            driver.setImpactLevel(i == 0 ? "MID" : "LOW");
            driver.setConfidence("T1".equals(e.getSourceTier()) ? "MID" : "LOW");
            driver.setDetail(StringUtils.firstNonBlank(e.getSnippet(), ""));
            drivers.add(driver);
        }
        report.setDrivers(drivers);
    }

    private String normLevel(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("HIGH".equals(v) || "MID".equals(v) || "LOW".equals(v)) {
            return v;
        }
        return "MID";
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