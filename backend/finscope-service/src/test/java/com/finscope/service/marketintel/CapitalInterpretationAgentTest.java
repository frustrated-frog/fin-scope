package com.finscope.service.marketintel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketintel.CapitalAgentEvidencePacket;
import com.finscope.domain.marketintel.CapitalBehaviorEvaluation;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalHypothesis;
import com.finscope.domain.marketintel.CapitalInterpretation;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import com.finscope.domain.marketintel.CapitalSignalEvaluation;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.marketintel.factor.CapitalFactorEngine;
import com.finscope.service.marketintel.factor.CapitalFactorRegistry;
import com.finscope.service.quant.factor.TimeSeriesFactorOperators;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalInterpretationAgentTest {
    @Test
    void capsHiddenFlowAtLowAndDropsUnknownMetricReferences() {
        CapitalHypothesis hidden = hypothesis("HIDDEN_FLOW", "HIGH", "flow:101:mainNetInflow");
        CapitalHypothesis invented = hypothesis("DISTRIBUTION", "MID", "flow:999:mainNetInflow");
        assertEquals(1, new CapitalHypothesisGate().apply(snapshot(), Arrays.asList(hidden, invented)).size());
        CapitalHypothesis accepted = new CapitalHypothesisGate().apply(snapshot(), Arrays.asList(hidden, invented)).get(0);
        assertEquals("LOW", accepted.getConfidence());
        assertTrue(accepted.getCounterEvidence().stream().anyMatch(v -> v.contains("Level-2")));
    }

    @Test
    void parsesJsonInsideMarkdownAndRejectsUntraceableObservation() {
        CapitalAgentEvidencePacket packet = packet(richSnapshot());
        String metricRef = packet.getRawMetrics().get(0).getRef();
        String output = "分析如下：\n```json\n{" +
                "\"marketState\":\"MIXED\",\"executiveSummary\":\"量价资金表现分化\"," +
                "\"observations\":[" + validObservations(packet) + "," +
                observation("FLOW", "引用不存在", "factor:unknown", metricRef) + "]," +
                "\"hypotheses\":[],\"counterEvidence\":[\"缺少逐笔成交\"]," +
                "\"watchConditionRefs\":[\"" + packet.getWatchConditions().get(0).getId() + "\"]," +
                "\"dataGaps\":[],\"confidence\":\"MID\",\"disclaimer\":\"不构成投资建议\"}\n```";
        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("MIXED", result.getMarketState());
        assertEquals(3, result.getObservations().size());
        assertEquals(1, result.getRejectedOutputCount());
        assertFalse(result.getEvidenceRefs().isEmpty());
        assertEquals("capital-factor-v1", result.getFactorVersion());
        assertEquals("capital-signal-v2", result.getSignalVersion());
    }

    @Test
    void rejectsObservationContainingNumberOutsideEvidencePacket() {
        CapitalAgentEvidencePacket packet = packet(richSnapshot());
        String factorRef = packet.getFactorObservations().get(0).factorRef();
        String metricRef = packet.getRawMetrics().get(0).getRef();
        String traceableValue = packet.getFactorObservations().get(0).getValue().stripTrailingZeros().toPlainString();
        String output = "{\"marketState\":\"MIXED\",\"executiveSummary\":\"量价资金表现分化\"," +
                "\"observations\":[" + observation("VOLUME", "量能因子值为" + traceableValue, factorRef, metricRef) + "," +
                observationForCategory(packet, "FLOW", "资金方向反复") + "," +
                observationForCategory(packet, "ORDER_STRUCTURE", "订单结构分化") + "," +
                observationForCategory(packet, "INTRADAY", "主力净流入达到999.99亿元") + "]," +
                "\"hypotheses\":[],\"counterEvidence\":[],\"watchConditionRefs\":[]," +
                "\"dataGaps\":[],\"confidence\":\"MID\",\"disclaimer\":\"不构成投资建议\"}";

        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(3, result.getObservations().size());
        assertTrue(result.getRejectionReasons().stream().anyMatch(value -> value.contains("数字")));
    }

    @Test
    void replacesUnsafeExecutiveSummaryWithoutRetryingTheWholeInterpretation() {
        CapitalAgentEvidencePacket packet = packet(richSnapshot());
        String output = validOutput(packet).replace("资金分化", "主力资金达到999.99亿元");
        AtomicInteger calls = new AtomicInteger();
        LlmChatClient llm = new LlmChatClient() {
            public boolean isConfigured() { return true; }
            public String modelName() { return "test-model"; }
            public String complete(String system, String user, int timeoutMs) {
                calls.incrementAndGet();
                return output;
            }
            public String complete(String system, String user) { throw new AssertionError("must use explicit timeout"); }
        };

        CapitalInterpretation result = agent(llm).interpret(packet, rules());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("规则摘要", result.getExecutiveSummary());
        assertEquals(1, calls.get());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(value -> value.contains("摘要") && value.contains("999.99")));
    }

    @Test
    void replacesUnsafeDisclaimerWithTheServerOwnedDisclaimer() {
        CapitalAgentEvidencePacket packet = packet(richSnapshot());
        String output = validOutput(packet).replace("不构成投资建议", "风险等级为999级");

        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("模型仅组织公开数据和已登记因子，仅用于研究，不构成投资建议。", result.getDisclaimer());
        assertTrue(result.getRejectionReasons().stream()
                .anyMatch(value -> value.contains("免责声明") && value.contains("999")));
    }

    @Test
    void replacesDisclaimerThatBorrowsAnUnreferencedHistoricalNumber() {
        CapitalSignalEvaluation history = historicalEvaluation();
        CapitalBehaviorSnapshot snapshot = richSnapshot();
        CapitalBehaviorEvaluation evaluation = CapitalBehaviorEvaluation.of(7L, snapshot.getId(), snapshot.getAsOf(),
                snapshot.getAsOf().toLocalDate().minusDays(20), snapshot.getAsOf().toLocalDate(),
                "capital-factor-v1", "capital-signal-v2", "historical-input", "AVAILABLE",
                20, 8, BigDecimal.ONE, BigDecimal.ZERO,
                Collections.singletonList(history), Collections.emptyList());
        CapitalAgentEvidencePacket packet = packet(snapshot, evaluation);
        String output = validOutput(packet).replace("不构成投资建议", "历史样本8次，不构成投资建议");

        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("模型仅组织公开数据和已登记因子，仅用于研究，不构成投资建议。", result.getDisclaimer());
        assertTrue(result.getRejectionReasons().stream().anyMatch(value -> value.contains("免责声明")
                && value.contains("历史统计")));
    }

    @Test
    void fallsBackWhenAllObservationsContainNumbersOutsideEvidencePacket() {
        CapitalAgentEvidencePacket packet = packet(richSnapshot());
        String output = "{\"marketState\":\"MIXED\",\"executiveSummary\":\"量价资金表现分化\"," +
                "\"observations\":[" + observationForCategory(packet, "FLOW", "主力净流入达到999.99亿元") + "]," +
                "\"hypotheses\":[],\"counterEvidence\":[],\"watchConditionRefs\":[]," +
                "\"dataGaps\":[],\"confidence\":\"MID\",\"disclaimer\":\"不构成投资建议\"}";

        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertEquals("FALLBACK", result.getStatus());
        assertEquals("OUTPUT_REJECTED_BY_GATE", result.getFallbackReason());
    }

    @Test
    void fallsBackWhenModelReturnsFewerThanThreeEvidenceDimensions() {
        CapitalAgentEvidencePacket packet = packet(richSnapshot());
        String output = "{\"marketState\":\"MIXED\",\"executiveSummary\":\"量价资金表现分化\"," +
                "\"observations\":[" + observationForCategory(packet, "FLOW", "资金方向反复") + "]," +
                "\"hypotheses\":[],\"counterEvidence\":[],\"watchConditionRefs\":[]," +
                "\"dataGaps\":[],\"confidence\":\"MID\",\"disclaimer\":\"不构成投资建议\"}";

        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertEquals("FALLBACK", result.getStatus());
        assertEquals("OUTPUT_REJECTED_BY_GATE", result.getFallbackReason());
        assertTrue(result.getRejectionReasons().stream().anyMatch(value -> value.contains("至少3个可用分析维度")));
    }

    @Test
    void acceptsOneDimensionAndForcesLowConfidenceWhenOnlyIntradayEvidenceIsAvailable() {
        CapitalAgentEvidencePacket packet = packet(intradaySnapshot());
        String output = "{\"marketState\":\"INTRADAY_REVERSAL\",\"executiveSummary\":\"日内资金反复\"," +
                "\"observations\":[" + observationForCategory(packet, "INTRADAY", "日内资金方向反复") + "]," +
                "\"hypotheses\":[],\"counterEvidence\":[],\"watchConditionRefs\":[]," +
                "\"dataGaps\":[],\"confidence\":\"MID\",\"disclaimer\":\"不构成投资建议\"}";

        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertTrue(packet.isSufficientCoverage());
        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(1, result.getObservations().size());
        assertEquals("LOW", result.getConfidence());
    }

    @Test
    void forcesLowConfidenceWhenSnapshotContainsFallbackData() {
        CapitalBehaviorSnapshot snapshot = richSnapshot();
        snapshot.setQualityStatus("PARTIAL");
        CapitalAgentEvidencePacket packet = packet(snapshot);

        CapitalInterpretation result = agent(llm(true, validOutput(packet))).interpret(packet, rules());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("LOW", result.getConfidence());
    }

    @Test
    void repairsInvalidJsonOnce() {
        CapitalAgentEvidencePacket packet = packet(richSnapshot());
        String valid = validOutput(packet);
        AtomicInteger calls = new AtomicInteger();
        LlmChatClient llm = new LlmChatClient() {
            public boolean isConfigured() { return true; }
            public String modelName() { return "test-model"; }
            public String complete(String a, String b) { return calls.incrementAndGet() == 1 ? "not-json" : valid; }
        };
        CapitalInterpretation result = agent(llm).interpret(packet, rules());
        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(2, calls.get());
    }

    @Test
    void repairsJsonThatParsesButViolatesTheAgentContract() {
        CapitalAgentEvidencePacket packet = packet(richSnapshot());
        String invalidContract = validOutput(packet).replace("\"marketState\":\"MIXED\"",
                "\"marketState\":\"INTRADAY_SESSION\"");
        String valid = validOutput(packet);
        AtomicInteger calls = new AtomicInteger();
        List<String> systemPrompts = new ArrayList<String>();
        List<String> userPrompts = new ArrayList<String>();
        LlmChatClient llm = new LlmChatClient() {
            public boolean isConfigured() { return true; }
            public String modelName() { return "test-model"; }
            public String complete(String system, String user, int timeoutMs) {
                systemPrompts.add(system);
                userPrompts.add(user);
                return calls.incrementAndGet() == 1 ? invalidContract : valid;
            }
            public String complete(String system, String user) { throw new AssertionError("must use explicit timeout"); }
        };

        CapitalInterpretation result = agent(llm).interpret(packet, rules());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(2, calls.get());
        assertTrue(systemPrompts.get(1).contains("INTRADAY_REVERSAL"));
        assertTrue(userPrompts.get(0).contains("\"historicalStatisticsOnly\":true"));
        assertTrue(userPrompts.get(1).contains("unknown market state"));
        assertTrue(userPrompts.get(1).contains("evidencePacket"));
    }

    @Test
    void recordsValidationStagesWhenContractRepairStillFails() {
        CapitalAgentEvidencePacket packet = packet(richSnapshot());
        String invalidContract = validOutput(packet).replace("\"marketState\":\"MIXED\"",
                "\"marketState\":\"INTRADAY_SESSION\"");
        AtomicInteger calls = new AtomicInteger();
        LlmChatClient llm = new LlmChatClient() {
            public boolean isConfigured() { return true; }
            public String modelName() { return "test-model"; }
            public String complete(String system, String user, int timeoutMs) {
                calls.incrementAndGet();
                return invalidContract;
            }
            public String complete(String system, String user) { throw new AssertionError("must use explicit timeout"); }
        };

        CapitalInterpretation result = agent(llm).interpret(packet, rules());

        assertEquals("FALLBACK", result.getStatus());
        assertEquals("INVALID_MODEL_OUTPUT", result.getFallbackReason());
        assertEquals(2, calls.get());
        assertEquals(2, result.getRejectedOutputCount());
        assertTrue(result.getRejectionReasons().get(0).contains("首次输出"));
        assertTrue(result.getRejectionReasons().get(1).contains("修复输出"));
        assertTrue(result.getOutputHash() != null && !result.getOutputHash().isEmpty());
    }

    @Test
    void returnsExplicitStatusForInsufficientDataAndTimeout() {
        CapitalAgentEvidencePacket insufficient = packet(snapshot());
        LlmChatClient mustNotCall = new LlmChatClient() {
            public boolean isConfigured() { return true; }
            public String modelName() { return "test-model"; }
            public String complete(String a, String b) { throw new AssertionError("must not call LLM"); }
        };
        CapitalInterpretation missing = agent(mustNotCall).interpret(insufficient, rules());
        assertEquals("INSUFFICIENT_DATA", missing.getStatus());
        assertEquals("INSUFFICIENT_FACTOR_COVERAGE", missing.getFallbackReason());

        LlmChatClient timeout = new LlmChatClient() {
            public boolean isConfigured() { return true; }
            public String modelName() { return "test-model"; }
            public String complete(String a, String b) throws Exception { throw new SocketTimeoutException("timeout"); }
        };
        CapitalInterpretation timedOut = agent(timeout).interpret(packet(richSnapshot()), rules());
        assertEquals("FALLBACK", timedOut.getStatus());
        assertEquals("LLM_TIMEOUT", timedOut.getFallbackReason());
    }

    @Test
    void givesPrimaryInterpretationMoreTimeThanJsonRepair() {
        CapitalAgentEvidencePacket packet = packet(richSnapshot());
        String valid = validOutput(packet);
        List<Integer> requestedTimeouts = new ArrayList<Integer>();
        AtomicInteger calls = new AtomicInteger();
        LlmChatClient llm = new LlmChatClient() {
            public boolean isConfigured() { return true; }
            public String modelName() { return "test-model"; }
            public String complete(String a, String b) { throw new AssertionError("must use explicit timeout"); }
            public String complete(String a, String b, int timeoutMs) {
                requestedTimeouts.add(timeoutMs);
                return calls.incrementAndGet() == 1 ? "not-json" : valid;
            }
        };

        CapitalInterpretation result = agent(llm).interpret(packet, rules());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(Arrays.asList(60000, 30000), requestedTimeouts);
    }

    @Test
    void returnsHonestFallbackWhenLlmIsNotConfigured() {
        LlmChatClient llm = llm(false, "");
        CapitalInterpretation result = agent(llm).interpret(packet(richSnapshot()), rules());
        assertEquals("FALLBACK", result.getStatus());
        assertEquals("LLM_NOT_CONFIGURED", result.getFallbackReason());
        assertEquals("capital-rules-v2", result.getRuleVersion());
        assertFalse(result.getFacts().isEmpty());
    }

    @Test
    void acceptsPublishableHistoricalStatisticsOnlyThroughTheirStableReference() {
        CapitalSignalEvaluation history = new CapitalSignalEvaluation();
        history.setSignalType("AMOUNT_EXPANSION_WITH_INFLOW");
        history.setSignalLabel("放量流入");
        history.setHorizonDays(3);
        history.setSampleCount(8);
        history.setAverageReturn(new BigDecimal("0.012500"));
        history.setMedianReturn(new BigDecimal("0.010000"));
        history.setPositiveRate(new BigDecimal("0.625000"));
        history.setAverageMfe(new BigDecimal("0.020000"));
        history.setAverageMae(new BigDecimal("-0.008000"));
        history.setStabilityStatus("INSUFFICIENT_SAMPLE");
        history.setEvaluationStatus("EXPLORATORY");
        CapitalBehaviorSnapshot snapshot = richSnapshot();
        CapitalBehaviorEvaluation evaluation = CapitalBehaviorEvaluation.of(7L, snapshot.getId(), snapshot.getAsOf(),
                snapshot.getAsOf().toLocalDate().minusDays(20), snapshot.getAsOf().toLocalDate(),
                "capital-factor-v1", "capital-signal-v2", "historical-input", "AVAILABLE",
                20, 8, BigDecimal.ONE, BigDecimal.ZERO,
                Collections.singletonList(history), Collections.emptyList());
        CapitalAgentEvidencePacket packet = packet(snapshot, evaluation);
        String historicalObservation = observationForCategory(packet, "FLOW", "历史样本平均收益为1.25%")
                .replace("}", ",\"evaluationRefs\":[\"" + history.evaluationRef() + "\"]}");
        String output = "{\"marketState\":\"MIXED\",\"executiveSummary\":\"资金分化\"," +
                "\"observations\":[" + observationForCategory(packet, "VOLUME", "量能活跃") + "," +
                historicalObservation + "," + observationForCategory(packet, "ORDER_STRUCTURE", "订单结构分化") + "]," +
                "\"hypotheses\":[],\"counterEvidence\":[],\"watchConditionRefs\":[]," +
                "\"dataGaps\":[],\"confidence\":\"MID\",\"disclaimer\":\"不构成投资建议\"}";

        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(Collections.singletonList(history.evaluationRef()),
                result.getObservations().get(1).getEvaluationRefs());
    }

    @Test
    void rejectsHistoricalStatisticsWhenTheObservationOmitsItsEvaluationReference() {
        CapitalSignalEvaluation history = historicalEvaluation();
        CapitalBehaviorSnapshot snapshot = richSnapshot();
        CapitalBehaviorEvaluation evaluation = CapitalBehaviorEvaluation.of(7L, snapshot.getId(), snapshot.getAsOf(),
                snapshot.getAsOf().toLocalDate().minusDays(20), snapshot.getAsOf().toLocalDate(),
                "capital-factor-v1", "capital-signal-v2", "historical-input", "AVAILABLE",
                20, 8, BigDecimal.ONE, BigDecimal.ZERO,
                Collections.singletonList(history), Collections.emptyList());
        CapitalAgentEvidencePacket packet = packet(snapshot, evaluation);
        String output = "{\"marketState\":\"MIXED\",\"executiveSummary\":\"资金分化\"," +
                "\"observations\":[" + observationForCategory(packet, "VOLUME", "量能活跃") + "," +
                observationForCategory(packet, "FLOW", "历史样本平均收益为0.0125") + "," +
                observationForCategory(packet, "ORDER_STRUCTURE", "订单结构分化") + "]," +
                "\"hypotheses\":[],\"counterEvidence\":[],\"watchConditionRefs\":[]," +
                "\"dataGaps\":[],\"confidence\":\"MID\",\"disclaimer\":\"不构成投资建议\"}";

        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertEquals("FALLBACK", result.getStatus());
        assertTrue(result.getRejectionReasons().stream().anyMatch(value -> value.contains("历史评价引用")));
    }

    @Test
    void rejectsUncitedHistoricalSampleCountEvenWhenTheSameNumberExistsInOtherEvidence() {
        CapitalSignalEvaluation history = historicalEvaluation();
        CapitalBehaviorSnapshot snapshot = richSnapshot();
        CapitalBehaviorEvaluation evaluation = CapitalBehaviorEvaluation.of(7L, snapshot.getId(), snapshot.getAsOf(),
                snapshot.getAsOf().toLocalDate().minusDays(20), snapshot.getAsOf().toLocalDate(),
                "capital-factor-v1", "capital-signal-v2", "historical-input", "AVAILABLE",
                20, 8, BigDecimal.ONE, BigDecimal.ZERO,
                Collections.singletonList(history), Collections.emptyList());
        CapitalAgentEvidencePacket packet = packet(snapshot, evaluation);
        String output = "{\"marketState\":\"MIXED\",\"executiveSummary\":\"资金分化\"," +
                "\"observations\":[" + observationForCategory(packet, "VOLUME", "量能活跃") + "," +
                observationForCategory(packet, "FLOW", "历史有效样本为8次") + "," +
                observationForCategory(packet, "ORDER_STRUCTURE", "订单结构分化") + "]," +
                "\"hypotheses\":[],\"counterEvidence\":[],\"watchConditionRefs\":[]," +
                "\"dataGaps\":[],\"confidence\":\"MID\",\"disclaimer\":\"不构成投资建议\"}";

        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertEquals("FALLBACK", result.getStatus());
        assertTrue(result.getRejectionReasons().stream().anyMatch(value -> value.contains("历史评价引用")));
    }

    @Test
    void replacesSummaryThatUsesHistoricalNumbersWithoutAnAuditableReference() {
        CapitalSignalEvaluation history = historicalEvaluation();
        CapitalBehaviorSnapshot snapshot = richSnapshot();
        CapitalBehaviorEvaluation evaluation = CapitalBehaviorEvaluation.of(7L, snapshot.getId(), snapshot.getAsOf(),
                snapshot.getAsOf().toLocalDate().minusDays(20), snapshot.getAsOf().toLocalDate(),
                "capital-factor-v1", "capital-signal-v2", "historical-input", "AVAILABLE",
                20, 8, BigDecimal.ONE, BigDecimal.ZERO,
                Collections.singletonList(history), Collections.emptyList());
        CapitalAgentEvidencePacket packet = packet(snapshot, evaluation);
        String output = validOutput(packet).replace("资金分化", "历史有效样本为8次");

        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("规则摘要", result.getExecutiveSummary());
        assertTrue(result.getRejectionReasons().stream().anyMatch(value -> value.contains("摘要")
                && value.contains("历史统计")));
    }

    @Test
    void rejectsHistoricalNumbersFromHypothesesAndFreeTextWithoutEvaluationReferences() {
        CapitalSignalEvaluation history = historicalEvaluation();
        CapitalBehaviorSnapshot snapshot = richSnapshot();
        CapitalBehaviorEvaluation evaluation = CapitalBehaviorEvaluation.of(7L, snapshot.getId(), snapshot.getAsOf(),
                snapshot.getAsOf().toLocalDate().minusDays(20), snapshot.getAsOf().toLocalDate(),
                "capital-factor-v1", "capital-signal-v2", "historical-input", "AVAILABLE",
                20, 8, BigDecimal.ONE, BigDecimal.ZERO,
                Collections.singletonList(history), Collections.emptyList());
        CapitalAgentEvidencePacket packet = packet(snapshot, evaluation);
        String metricRef = packet.getRawMetrics().get(0).getRef();
        String historyHypothesis = "{\"type\":\"ACCUMULATION\",\"claim\":\"历史样本8次支持该假设\"," +
                "\"confidence\":\"MID\",\"supportingMetricRefs\":[\"" + metricRef + "\"]," +
                "\"counterEvidence\":[],\"dataGaps\":[]}";
        String output = validOutput(packet)
                .replace("\"hypotheses\":[]", "\"hypotheses\":[" + historyHypothesis + "]")
                .replace("\"counterEvidence\":[]", "\"counterEvidence\":[\"历史有效样本为8次\"]");

        CapitalInterpretation result = agent(llm(true, output)).interpret(packet, rules());

        assertEquals("SUCCEEDED", result.getStatus());
        assertTrue(result.getHypotheses().isEmpty());
        assertTrue(result.getCounterEvidence().isEmpty());
        assertTrue(result.getRejectionReasons().stream().filter(value -> value.contains("历史统计数字")).count() >= 2);
    }

    private CapitalSignalEvaluation historicalEvaluation() {
        CapitalSignalEvaluation history = new CapitalSignalEvaluation();
        history.setSignalType("AMOUNT_EXPANSION_WITH_INFLOW");
        history.setSignalLabel("放量流入");
        history.setHorizonDays(3);
        history.setSampleCount(8);
        history.setAverageReturn(new BigDecimal("0.012500"));
        history.setMedianReturn(new BigDecimal("0.010000"));
        history.setPositiveRate(new BigDecimal("0.625000"));
        history.setAverageMfe(new BigDecimal("0.020000"));
        history.setAverageMae(new BigDecimal("-0.008000"));
        history.setStabilityStatus("INSUFFICIENT_SAMPLE");
        history.setEvaluationStatus("EXPLORATORY");
        return history;
    }

    private CapitalInterpretationAgent agent(LlmChatClient llm) {
        ObjectMapper mapper = new ObjectMapper();
        return new CapitalInterpretationAgent(llm, mapper, new CapitalAgentResponseParser(mapper),
                new CapitalInterpretationGate());
    }

    private CapitalAgentEvidencePacket packet(CapitalBehaviorSnapshot snapshot) {
        return packet(snapshot, null);
    }

    private CapitalAgentEvidencePacket packet(CapitalBehaviorSnapshot snapshot,
                                               CapitalBehaviorEvaluation evaluation) {
        CapitalFactorEngine factors = new CapitalFactorEngine(new CapitalFactorRegistry(), new TimeSeriesFactorOperators());
        return new CapitalAgentEvidenceAssembler(factors,
                new CapitalBehaviorSignalService(CapitalSignalPolicy.v2(), factors),
                new CapitalMetricCatalog()).assemble(snapshot, rules(), evaluation);
    }

    private String validOutput(CapitalAgentEvidencePacket packet) {
        return "{\"marketState\":\"MIXED\",\"executiveSummary\":\"资金分化\"," +
                "\"observations\":[" + validObservations(packet) + "]," +
                "\"hypotheses\":[],\"counterEvidence\":[],\"watchConditionRefs\":[]," +
                "\"dataGaps\":[],\"confidence\":\"MID\",\"disclaimer\":\"不构成投资建议\"}";
    }

    private String validObservations(CapitalAgentEvidencePacket packet) {
        return observationForCategory(packet, "VOLUME", "量能活跃") + "," +
                observationForCategory(packet, "FLOW", "资金方向反复") + "," +
                observationForCategory(packet, "ORDER_STRUCTURE", "订单结构分化");
    }

    private String observationForCategory(CapitalAgentEvidencePacket packet, String category, String claim) {
        String factorRef = packet.getFactorObservations().stream()
                .filter(item -> category.equals(item.getCategory()))
                .findFirst().orElseThrow(() -> new AssertionError("missing factor category " + category))
                .factorRef();
        return observation(category, claim, factorRef, packet.getRawMetrics().get(0).getRef());
    }

    private String observation(String dimension, String claim, String factorRef, String metricRef) {
        return "{\"dimension\":\"" + dimension + "\",\"claim\":\"" + claim +
                "\",\"factorRefs\":[\"" + factorRef + "\"],\"metricRefs\":[\"" + metricRef + "\"]}";
    }

    private LlmChatClient llm(boolean configured, String output) {
        return new LlmChatClient() {
            public boolean isConfigured() { return configured; }
            public String modelName() { return configured ? "test-model" : ""; }
            public String complete(String a, String b) { return output; }
        };
    }

    private CapitalBehaviorSnapshot richSnapshot() {
        List<CapitalFlowPoint> facts = new ArrayList<CapitalFlowPoint>();
        for (int i = 0; i < 6; i++) {
            CapitalFlowPoint point = flow(200 + i, "DAY_1", LocalDateTime.of(2026, 7, 7 + i, 15, 0),
                    String.valueOf(100 + i * 15), String.valueOf(i % 2 == 0 ? 18 : -8));
            point.setTurnoverRate(new BigDecimal(String.valueOf(i + 1)));
            point.setVolumeRatio(new BigDecimal("1.5"));
            point.setSuperLargeNetInflow(new BigDecimal("20"));
            point.setLargeNetInflow(new BigDecimal("10"));
            point.setMediumNetInflow(new BigDecimal("-5"));
            point.setSmallNetInflow(new BigDecimal("-8"));
            facts.add(point);
        }
        facts.add(flow(300, "MINUTE_5", LocalDateTime.of(2026, 7, 14, 9, 30), "30", "8"));
        facts.add(flow(301, "MINUTE_5", LocalDateTime.of(2026, 7, 14, 10, 0), "50", "-3"));
        facts.add(flow(302, "MINUTE_5", LocalDateTime.of(2026, 7, 14, 10, 30), "60", "12"));
        CapitalBehaviorSnapshot value = CapitalBehaviorSnapshot.of(7L, LocalDateTime.of(2026, 7, 14, 10, 30),
                facts, Collections.emptyList(), "rich-fingerprint");
        value.setId(77L);
        value.setQualityStatus("COMPLETE");
        return value;
    }

    private CapitalBehaviorSnapshot snapshot() {
        CapitalFlowPoint p = flow(101, "MINUTE_1", LocalDateTime.of(2026, 7, 14, 10, 30), "120000000", "18000000");
        return CapitalBehaviorSnapshot.of(7L, p.getObservedAt(), Collections.singletonList(p),
                Collections.emptyList(), "fingerprint");
    }

    private CapitalBehaviorSnapshot intradaySnapshot() {
        List<CapitalFlowPoint> facts = new ArrayList<CapitalFlowPoint>();
        for (int i = 0; i < 8; i++) {
            facts.add(flow(400 + i, "MINUTE_1", LocalDateTime.of(2026, 7, 15, 9, 31 + i),
                    String.valueOf(100 + i * 10), String.valueOf(i % 2 == 0 ? 30 : -20)));
        }
        CapitalBehaviorSnapshot value = CapitalBehaviorSnapshot.of(7L,
                LocalDateTime.of(2026, 7, 15, 9, 38), facts, Collections.emptyList(), "intraday-only");
        value.setId(78L);
        value.setQualityStatus("PARTIAL");
        return value;
    }

    private CapitalFlowPoint flow(long id, String granularity, LocalDateTime at, String amount, String net) {
        CapitalFlowPoint p = new CapitalFlowPoint();
        p.setId(id);
        p.setInstrumentId(7L);
        p.setGranularity(granularity);
        p.setObservedAt(at);
        p.setDataDate(at.toLocalDate());
        p.setMainNetInflow(new BigDecimal(net));
        p.setIntervalTradeAmount(new BigDecimal(amount));
        p.setTradeVolume(new BigDecimal("1000"));
        p.setPrice(new BigDecimal("1480.50"));
        p.setQualityStatus("COMPLETE");
        return p;
    }

    private CapitalRuleExplanation rules() {
        CapitalRuleExplanation value = new CapitalRuleExplanation();
        value.setRuleVersion("capital-rules-v2");
        value.setSummary("规则摘要");
        value.setItems(Collections.emptyList());
        value.setDataGaps(Collections.singletonList("缺少 Level-2"));
        return value;
    }

    private CapitalHypothesis hypothesis(String type, String confidence, String ref) {
        CapitalHypothesis value = new CapitalHypothesis();
        value.setType(type);
        value.setClaim(type);
        value.setConfidence(confidence);
        value.setSupportingMetricRefs(Collections.singletonList(ref));
        return value;
    }
}
