package com.finscope.service.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialInterpretationAgentTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsValidPrimaryOutputWithoutRepair() {
        ProgrammableClient llm = new ProgrammableClient(true, validJson());
        FinancialInterpretation result = agent(llm).interpret(packet());

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("LLM", result.getGenerationMode());
        assertEquals(1, llm.calls);
        assertEquals("IMPROVING", result.getResult().getOperatingState());
        assertEquals(2, result.getResult().getPeriodChanges().size());
        assertEquals(2, result.getResult().getCrossStatementInsights().size());
        assertEquals(2, result.getResult().getDimensions().get(0).getDetails().size());
    }

    @Test
    void repairsInvalidPrimaryOutputOnce() {
        ProgrammableClient llm = new ProgrammableClient(true, "{}", validJson());
        FinancialInterpretation result = agent(llm).interpret(packet());

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("REPAIRED", result.getGenerationMode());
        assertEquals(2, llm.calls);
        assertFalse(result.getValidationErrors().isEmpty());
    }

    @Test
    void fallsBackAfterTwoRejectedOutputs() {
        ProgrammableClient llm = new ProgrammableClient(true, "{}", "still invalid");
        FinancialInterpretation result = agent(llm).interpret(packet());

        assertEquals("FALLBACK", result.getStatus());
        assertEquals("DETERMINISTIC_FALLBACK", result.getGenerationMode());
        assertEquals("OUTPUT_REJECTED_BY_GATE", result.getFailureCode());
        assertEquals(2, llm.calls);
        assertEquals(6, result.getResult().getDimensions().size());
    }

    @Test
    void fallsBackWhenLlmIsNotConfiguredOrTimesOut() {
        ProgrammableClient disabled = new ProgrammableClient(false);
        FinancialInterpretation unavailable = agent(disabled).interpret(packet());
        assertEquals("LLM_NOT_CONFIGURED", unavailable.getFailureCode());
        assertEquals(0, disabled.calls);

        ProgrammableClient timeout = new ProgrammableClient(true, new SocketTimeoutException("slow"));
        FinancialInterpretation timedOut = agent(timeout).interpret(packet());
        assertEquals("LLM_TIMEOUT", timedOut.getFailureCode());
        assertEquals("FALLBACK", timedOut.getStatus());
    }

    @Test
    void honorsTheConfiguredClientTimeoutForPrimaryAndRepairCalls() {
        ProgrammableClient llm = new ProgrammableClient(true, "{}", validJson());

        FinancialInterpretation result = agent(llm).interpret(packet());

        assertEquals("SUCCESS", result.getStatus());
        assertEquals(Arrays.asList((Integer) null, null), llm.timeouts);
    }

    @Test
    void declaresCollectionAndClaimShapesInThePrimaryPrompt() {
        ProgrammableClient llm = new ProgrammableClient(true, validJson());

        agent(llm).interpret(packet());

        String prompt = llm.systemPrompts.get(0);
        assertTrue(prompt.contains("executiveSummary必须是数组"));
        assertTrue(prompt.contains("positiveSignals、risks、turningPoints、watchpoints必须是数组"));
        assertTrue(prompt.contains("claim、claimType、refs"));
        assertTrue(prompt.contains("operatingState只能是IMPROVING、STABLE、UNDER_PRESSURE、INSUFFICIENT_EVIDENCE之一"));
        assertTrue(prompt.contains("assessment只能是POSITIVE、NEUTRAL、NEGATIVE、INSUFFICIENT_EVIDENCE之一"));
        assertTrue(prompt.contains("periodChanges"));
        assertTrue(prompt.contains("crossStatementInsights"));
        assertTrue(prompt.contains("details"));
        assertTrue(prompt.contains("页面会通过refs展示精确数值"));
        assertTrue(prompt.contains("不得出现evidence未原样提供的数字"));
        assertTrue(prompt.contains("details为1至2条Claim"));
    }

    @Test
    void sendsOnlyTheCompactModelPayloadToPrimaryAndRepairCalls() {
        ProgrammableClient llm = new ProgrammableClient(true, "{}", validJson());
        FinancialEvidencePacket packet = packet();
        packet.setPayloadJson("{\"fullEvidence\":true}");
        packet.setModelPayloadJson("{\"compactEvidence\":true}");

        FinancialInterpretation result = agent(llm).interpret(packet);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("{\"compactEvidence\":true}", llm.userPrompts.get(0));
        assertTrue(llm.userPrompts.get(1).contains("compactEvidence"));
        assertFalse(llm.userPrompts.get(1).contains("fullEvidence"));
    }

    private FinancialInterpretationAgent agent(LlmChatClient llm) {
        FinancialInterpretationResponseParser parser = new FinancialInterpretationResponseParser(json);
        FinancialInterpretationGate gate = new FinancialInterpretationGate(json);
        return new FinancialInterpretationAgent(llm, json, parser, gate,
                new FinancialInterpretationFallbackBuilder());
    }

    private FinancialEvidencePacket packet() {
        FinancialEvidence evidence = new FinancialEvidence();
        evidence.setId("M_REVENUE_YOY");
        evidence.setType("METRIC");
        evidence.setLabel("营业收入同比");
        evidence.setValue("12.30");
        evidence.setUnit("%");
        FinancialEvidencePacket packet = new FinancialEvidencePacket();
        packet.setReportId(9L);
        packet.setPromptVersion("financial-interpret-v1");
        packet.setInputHash("input-hash");
        packet.setQualityCeiling("MEDIUM");
        packet.setPayloadJson("{\"qualityCeiling\":\"MEDIUM\"}");
        packet.setModelPayloadJson("{\"qualityCeiling\":\"MEDIUM\",\"compact\":true}");
        packet.setEvidence(Arrays.asList(evidence));
        LinkedHashMap<String, FinancialEvidence> index = new LinkedHashMap<String, FinancialEvidence>();
        index.put(evidence.getId(), evidence);
        packet.setEvidenceIndex(index);
        packet.setAllowedNumbers(new LinkedHashSet<String>(Arrays.asList("12.30", "12.3", "2025")));
        return packet;
    }

    private String validJson() {
        return "```json\n{" +
                "\"operatingState\":\"IMPROVING\",\"confidence\":\"MEDIUM\"," +
                "\"executiveSummary\":[{\"claim\":\"营业收入同比12.30%\",\"claimType\":\"FACT\",\"refs\":[\"M_REVENUE_YOY\"]}]," +
                "\"periodChanges\":[" + claim() + "," + claim() + "]," +
                "\"crossStatementInsights\":[" + claim() + "," + claim() + "]," +
                "\"dimensions\":[" + dimension("GROWTH") + "," + dimension("PROFITABILITY") + "," +
                dimension("EARNINGS_QUALITY") + "," + dimension("CASH_QUALITY") + "," +
                dimension("ASSET_QUALITY") + "," + dimension("SOLVENCY_CAPITAL_DISCIPLINE") + "]," +
                "\"positiveSignals\":[],\"risks\":[],\"turningPoints\":[],\"watchpoints\":[]," +
                "\"limitations\":[\"仅基于2025年结构化财报\"]," +
                "\"disclaimer\":\"仅用于研究，不构成投资建议。\"}\n```";
    }

    private String dimension(String code) {
        return "{\"code\":\"" + code + "\",\"assessment\":\"NEUTRAL\"," +
                "\"summary\":\"营业收入同比12.30%\",\"refs\":[\"M_REVENUE_YOY\"]," +
                "\"details\":[" + claim() + "," + claim() + "]}";
    }

    private String claim() {
        return "{\"claim\":\"营业收入同比12.30%\",\"claimType\":\"FACT\"," +
                "\"refs\":[\"M_REVENUE_YOY\"]}";
    }

    private static final class ProgrammableClient implements LlmChatClient {
        private final boolean configured;
        private final List<Object> values = new ArrayList<Object>();
        private final List<Integer> timeouts = new ArrayList<Integer>();
        private final List<String> systemPrompts = new ArrayList<String>();
        private final List<String> userPrompts = new ArrayList<String>();
        private int calls;

        private ProgrammableClient(boolean configured, Object... values) {
            this.configured = configured;
            this.values.addAll(Arrays.asList(values));
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String modelName() {
            return "test-model";
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) throws Exception {
            systemPrompts.add(systemPrompt);
            userPrompts.add(userPrompt);
            timeouts.add(null);
            return nextValue();
        }

        @Override
        public String complete(String systemPrompt, String userPrompt, int timeoutMs) throws Exception {
            systemPrompts.add(systemPrompt);
            userPrompts.add(userPrompt);
            timeouts.add(timeoutMs);
            return nextValue();
        }

        private String nextValue() throws Exception {
            Object value = values.get(calls++);
            if (value instanceof Exception) throw (Exception) value;
            return String.valueOf(value);
        }
    }
}
