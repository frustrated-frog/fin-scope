package com.finscope.service.investmentrecognition;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.investmentrecognition.InvestmentRecognitionCandidateRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.agent.AgentRun;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.investmentrecognition.InvestmentRecognitionCandidate;
import com.finscope.domain.investmentrecognition.InvestmentRecognitionRun;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.instrument.WatchlistItemView;
import com.finscope.service.instrument.WatchlistService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InvestmentRecognitionAgentService {
    private static final double MATERIAL_MOVE_PCT = 1.5D;
    private static final int TIMEOUT_MS = 20_000;
    private static final int MAX_OUTPUT_TOKENS = 700;
    private final WatchlistService watchlist;
    private final InvestmentRecognitionCandidateRepository candidates;
    private final AgentRunRepository runs;
    private final RadarRepository radar;
    private final LlmChatClient llm;
    private final ObjectMapper json;

    public InvestmentRecognitionAgentService(WatchlistService watchlist,
                                             InvestmentRecognitionCandidateRepository candidates,
                                             AgentRunRepository runs,
                                             RadarRepository radar,
                                             LlmChatClient llm,
                                             ObjectMapper json) {
        this.watchlist = watchlist;
        this.candidates = candidates;
        this.runs = runs;
        this.radar = radar;
        this.llm = llm;
        this.json = json.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public InvestmentRecognitionRun run() {
        List<WatchlistItemView> universe = watchlist.listInvestmentItemsWithQuotes(false);
        List<RadarEvent> triggers = radar.findRanked("ALL", true, 30);
        List<InvestmentRecognitionCandidate> generated = new ArrayList<InvestmentRecognitionCandidate>();
        for (WatchlistItemView view : universe) {
            InvestmentRecognitionCandidate value = inspect(view, triggers);
            if (value != null) generated.add(candidates.saveOrRefresh(value));
        }
        InvestmentRecognitionRun result = new InvestmentRecognitionRun();
        result.setCheckedObjects(universe.size());
        result.setCandidates(generated);
        result.setCandidateCount(count(generated, "CANDIDATE"));
        result.setNeedsEvidenceCount(count(generated, "NEEDS_EVIDENCE"));
        result.setGeneratedAt(LocalDateTime.now());
        return result;
    }

    private InvestmentRecognitionCandidate inspect(WatchlistItemView view, List<RadarEvent> triggers) {
        WatchlistItem item = view.getItem();
        Quote quote = view.getQuote();
        RadarEvent trigger = matchTrigger(item, triggers);
        Double changePct = effectiveChangePct(quote);
        if (quote == null || !quote.isValid() || changePct == null) {
            InvestmentRecognitionCandidate missing = missingEvidence(item, quote);
            applyTrigger(missing, trigger);
            trace(missing, structuredInput(item, quote, trigger), "FALLBACK", "MARKET_DATA_MISSING", 0L);
            return missing;
        }
        if (Math.abs(changePct) < MATERIAL_MOVE_PCT) return null;

        InvestmentRecognitionCandidate fallback = deterministicCandidate(item, quote, changePct);
        applyTrigger(fallback, trigger);
        String input = structuredInput(item, quote, trigger);
        if (llm == null || !llm.isConfigured()) {
            trace(fallback, input, "FALLBACK", "MODEL_DISABLED", 0L);
            return fallback;
        }
        long started = System.currentTimeMillis();
        try {
            String raw = llm.complete(systemPrompt(), input, TIMEOUT_MS, MAX_OUTPUT_TOKENS);
            Draft draft = json.readValue(extractJson(raw), Draft.class);
            validate(draft);
            fallback.setThesis(draft.thesis.trim());
            fallback.setMechanism(draft.mechanism.trim());
            fallback.setCounterData(trimmed(draft.counterData));
            fallback.setValidationMetrics(trimmed(draft.validationMetrics));
            fallback.setInvalidationConditions(joined(draft.invalidationConditions));
            fallback.setHorizon(draft.horizon.trim());
            fallback.setConfidence(draft.confidence.trim().toUpperCase(Locale.ROOT));
            trace(fallback, input, "SUCCESS", null, System.currentTimeMillis() - started);
            return fallback;
        } catch (Exception error) {
            trace(fallback, input, "FALLBACK", error.getClass().getSimpleName(),
                    System.currentTimeMillis() - started);
            return fallback;
        }
    }

    private InvestmentRecognitionCandidate deterministicCandidate(WatchlistItem item, Quote quote, double changePct) {
        String direction = changePct >= 0D ? "上涨" : "下跌";
        InvestmentRecognitionCandidate value = base(item, quote, changePct);
        value.setStatus("CANDIDATE");
        value.setThesis(item.getName() + "的" + direction + "是否反映盈利、估值或风险预期变化，值得继续验证");
        if (quote.getPrice() != null) {
            value.setObservedChange(String.format(Locale.ROOT, "最新价格 %.2f，当期%s %.2f%%",
                    quote.getPrice(), direction, Math.abs(changePct)));
        } else {
            value.setObservedChange(String.format(Locale.ROOT, "确认单位净值 %.4f（%s），当期%s %.2f%%",
                    quote.getConfirmedNav(), safe(quote.getConfirmedNavDate(), "日期待确认"),
                    direction, Math.abs(changePct)));
        }
        value.setMechanism("若后续基本面或资金数据与价格方向一致，变化可能由预期重估驱动；否则更可能是短期交易波动。");
        List<String> support = new ArrayList<String>();
        support.add(String.format(Locale.ROOT, "涨跌幅 %+.2f%%", changePct));
        if (quote.getTurnover() != null) support.add(String.format(Locale.ROOT, "成交/换手指标 %.2f", quote.getTurnover()));
        if (quote.getAmplitude() != null) support.add(String.format(Locale.ROOT, "振幅 %.2f%%", quote.getAmplitude()));
        value.setSupportingData(support);
        value.setCounterData(Arrays.asList("单期价格变化可能由情绪或流动性驱动", "当前尚未取得同步的盈利预期变化"));
        value.setValidationMetrics(Arrays.asList("未来五个交易日价格与成交持续性", "下一期财务指标或基金净值确认", "后续资金流方向"));
        value.setInvalidationConditions("价格变化快速反转，且后续盈利、估值和资金数据均未出现同向改善");
        value.setHorizon("未来 5 个交易日到下一次正式财务或净值更新");
        value.setConfidence("MEDIUM");
        value.setEvidenceCompleteness("SUFFICIENT");
        return value;
    }

    private InvestmentRecognitionCandidate missingEvidence(WatchlistItem item, Quote quote) {
        InvestmentRecognitionCandidate value = base(item, quote, null);
        value.setStatus("NEEDS_EVIDENCE");
        value.setThesis(item.getName() + "当前缺少有效行情，暂不能形成投资认识");
        value.setObservedChange("未取得可验证的最新涨跌数据");
        value.setMechanism("缺少结构化行情时，不对盈利、估值或风险方向作推断。");
        value.setSupportingData(new ArrayList<String>());
        value.setCounterData(Arrays.asList("行情无效或尚未更新"));
        value.setValidationMetrics(Arrays.asList("取得有效最新价、涨跌幅和数据时间"));
        value.setInvalidationConditions("取得有效行情后重新运行 Agent");
        value.setHorizon("下一次行情更新");
        value.setConfidence("LOW");
        value.setEvidenceCompleteness("MISSING");
        return value;
    }

    private InvestmentRecognitionCandidate base(WatchlistItem item, Quote quote, Double changePct) {
        String asOf = quote == null ? null : String.valueOf(quote.getAsOf() == null ? quote.getQuoteTime() : quote.getAsOf());
        String date = asOf == null || "null".equals(asOf) ? LocalDate.now().toString() : asOf.substring(0, 10);
        InvestmentRecognitionCandidate value = new InvestmentRecognitionCandidate();
        value.setFingerprint(item.getType() + ":" + item.getCode() + ":" + date + ":" + movementBand(changePct));
        value.setSubjectType(item.getType());
        value.setSubjectCode(item.getCode());
        value.setSubjectName(item.getName() == null ? item.getCode() : item.getName());
        value.setDataAsOf(asOf);
        return value;
    }

    private String structuredInput(WatchlistItem item, Quote quote, RadarEvent trigger) {
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("subjectType", item.getType());
            payload.put("subjectCode", item.getCode());
            payload.put("subjectName", item.getName());
            Map<String, Object> market = new LinkedHashMap<String, Object>();
            market.put("price", quote == null ? null : quote.getPrice());
            market.put("confirmedNav", quote == null ? null : quote.getConfirmedNav());
            market.put("changePct", effectiveChangePct(quote));
            market.put("turnover", quote == null ? null : quote.getTurnover());
            market.put("volume", quote == null ? null : quote.getVolume());
            market.put("amplitude", quote == null ? null : quote.getAmplitude());
            market.put("asOf", quote == null || quote.getAsOf() == null ? null : quote.getAsOf().toString());
            market.put("qualityStatus", quote == null ? null : quote.getQualityStatus());
            market.put("sourceCode", quote == null ? null : quote.getSourceCode());
            payload.put("marketObservation", market);
            if (trigger != null) {
                Map<String, Object> triggerValue = new LinkedHashMap<String, Object>();
                triggerValue.put("role", "TRIGGER_ONLY_NOT_EVIDENCE");
                triggerValue.put("title", trigger.getCanonicalTitle());
                triggerValue.put("lastSeenAt", trigger.getLastSeenAt() == null ? null : trigger.getLastSeenAt().toString());
                payload.put("quickNewsTrigger", triggerValue);
            }
            return json.writeValueAsString(payload);
        } catch (Exception error) {
            throw new IllegalArgumentException("无法组装投资数据快照", error);
        }
    }

    private String systemPrompt() {
        return "你是投资认识 Agent，只能依据用户消息中的结构化行情数据提出可检验的投资命题。"
                + "不得检索或引用文章、新闻正文、摘要和文章知识库；不得补造财务、资金或宏观事实。"
                + "quickNewsTrigger 只解释为何现在检查，不得写入支持数据或作为结论证据。"
                + "必须围绕给定股票、基金、ETF、指数、行业或宏观财富变量，说明盈利、估值或风险机制。"
                + "不得把输入中没有的订单、财务、资金、重组、政策或市场传闻写成已知事实；只能把它们表述为待验证假设或验证指标。"
                + "输出单个纯 JSON，只允许 thesis、mechanism、counterData、validationMetrics、"
                + "invalidationConditions、horizon、confidence；counterData 与 validationMetrics 必须是字符串数组，"
                + "例如 \"counterData\":[\"反证项\"],\"validationMetrics\":[\"验证指标\"],"
                + "\"invalidationConditions\":\"失效条件\"；"
                + "confidence 仅允许 LOW、MEDIUM、HIGH。";
    }

    private void validate(Draft draft) {
        List<String> counterData = draft == null ? new ArrayList<String>() : trimmed(draft.counterData);
        List<String> validationMetrics = draft == null ? new ArrayList<String>() : trimmed(draft.validationMetrics);
        if (draft == null || blank(draft.thesis) || blank(draft.mechanism)
                || blank(joined(draft.invalidationConditions)) || blank(draft.horizon)
                || counterData.isEmpty() || validationMetrics.isEmpty()
                || !("LOW".equalsIgnoreCase(draft.confidence) || "MEDIUM".equalsIgnoreCase(draft.confidence)
                || "HIGH".equalsIgnoreCase(draft.confidence))) {
            throw new IllegalArgumentException("Agent 输出缺少投资认识必需字段");
        }
    }

    private void trace(InvestmentRecognitionCandidate value, String input, String status,
                       String fallbackReason, long durationMs) {
        AgentRun run = new AgentRun();
        run.setNodeName("investment-recognition-agent");
        run.setStatus(status);
        run.setInput(input);
        run.setOutput(value.getStatus() + ": " + value.getThesis());
        run.setDurationMs(durationMs);
        run.setFallbackUsed("FALLBACK".equals(status));
        run.setFallbackReason(fallbackReason);
        run.setSubjectType("INVESTMENT_RECOGNITION");
        run.setSubjectId(value.getId());
        runs.record(run);
    }

    private Double effectiveChangePct(Quote quote) {
        if (quote == null) return null;
        return quote.getChangePct() == null ? quote.getConfirmedNavChangePct() : quote.getChangePct();
    }
    private int count(List<InvestmentRecognitionCandidate> values, String status) {
        int count = 0;
        for (InvestmentRecognitionCandidate value : values) if (status.equals(value.getStatus())) count++;
        return count;
    }
    private List<String> trimmed(List<String> values) {
        List<String> result = new ArrayList<String>();
        if (values == null) return result;
        for (String value : values) if (!blank(value)) result.add(value.trim());
        return result;
    }
    private String joined(List<String> values) {
        return String.join("；", trimmed(values));
    }
    private RadarEvent matchTrigger(WatchlistItem item, List<RadarEvent> events) {
        if (events == null) return null;
        String code = safe(item.getCode(), "").toLowerCase(Locale.ROOT);
        String name = safe(item.getName(), "").toLowerCase(Locale.ROOT);
        for (RadarEvent event : events) {
            String text = (safe(event.getCanonicalTitle(), "") + " " + safe(event.getSummary(), "") + " "
                    + safe(event.getWatchlistExplanation(), "")).toLowerCase(Locale.ROOT);
            if ((!code.isEmpty() && text.contains(code)) || (!name.isEmpty() && text.contains(name))) return event;
        }
        return null;
    }
    private void applyTrigger(InvestmentRecognitionCandidate value, RadarEvent trigger) {
        if (trigger != null) value.setTriggerSummary(trigger.getCanonicalTitle());
    }
    private String movementBand(Double changePct) {
        if (changePct == null) return "MISSING";
        double absolute = Math.abs(changePct);
        String magnitude = absolute >= 5D ? "5_PLUS" : absolute >= 3D ? "3_TO_5" : "1_5_TO_3";
        return (changePct >= 0D ? "UP_" : "DOWN_") + magnitude;
    }
    private String safe(String value, String fallback) { return blank(value) ? fallback : value; }
    private String extractJson(String raw) {
        if (raw == null) return "";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end >= start ? raw.substring(start, end + 1) : raw.trim();
    }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private static final class Draft {
        public String thesis;
        public String mechanism;
        public List<String> counterData;
        public List<String> validationMetrics;
        public List<String> invalidationConditions;
        public String horizon;
        public String confidence;
    }
}
