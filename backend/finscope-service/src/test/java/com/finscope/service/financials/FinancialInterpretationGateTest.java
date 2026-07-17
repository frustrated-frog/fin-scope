package com.finscope.service.financials;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialInterpretation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinancialInterpretationGateTest {
    private final ObjectMapper json = new ObjectMapper();
    private FinancialInterpretationGate gate;
    private FinancialEvidencePacket packet;

    @BeforeEach
    void setUp() {
        gate = new FinancialInterpretationGate(json);
        packet = new FinancialEvidencePacket();
        packet.setQualityCeiling("MEDIUM");
        packet.setAllowedNumbers(new LinkedHashSet<String>(Arrays.asList("12.30", "12.3", "2025")));
        FinancialEvidence evidence = new FinancialEvidence();
        evidence.setId("M_REVENUE_YOY");
        evidence.setType("METRIC");
        evidence.setLabel("营业收入同比");
        evidence.setValue("12.30");
        LinkedHashMap<String, FinancialEvidence> index = new LinkedHashMap<String, FinancialEvidence>();
        index.put(evidence.getId(), evidence);
        packet.setEvidenceIndex(index);
    }

    @Test
    void acceptsCompleteEvidenceBoundOutput() throws Exception {
        FinancialInterpretation.Result result = gate.apply(json.readTree(validJson()), packet);

        assertEquals("IMPROVING", result.getOperatingState());
        assertEquals("MEDIUM", result.getConfidence());
        assertEquals(6, result.getDimensions().size());
        assertEquals(2, result.getPeriodChanges().size());
        assertEquals(2, result.getCrossStatementInsights().size());
        assertEquals(2, result.getDimensions().get(0).getDetails().size());
        assertEquals("M_REVENUE_YOY", result.getExecutiveSummary().get(0).getRefs().get(0));
    }

    @Test
    void rejectsUnknownEvidenceReference() throws Exception {
        assertRejected(validJson().replace("M_REVENUE_YOY", "M_UNKNOWN"), "引用不存在");
    }

    @Test
    void rejectsNumberOutsideEvidenceWhitelist() throws Exception {
        assertRejected(validJson().replace("12.30%", "99.90%"), "证据包外数字");
    }

    @Test
    void rejectsUnknownReferenceInsideDimensionDetails() throws Exception {
        JsonNode root = json.readTree(validJson());
        ((com.fasterxml.jackson.databind.node.ArrayNode) root.path("dimensions").get(0)
                .path("details").get(0).path("refs")).set(0, json.getNodeFactory().textNode("M_UNKNOWN"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> gate.apply(root, packet));
        org.junit.jupiter.api.Assertions.assertTrue(
                error.getMessage().contains("引用不存在"), error.getMessage());
    }

    @Test
    void rejectsConfidenceAboveQualityCeiling() throws Exception {
        assertRejected(validJson().replace("\"confidence\":\"MEDIUM\"", "\"confidence\":\"HIGH\""),
                "置信度超过");
    }

    @Test
    void rejectsInvestmentAdviceAndMissingDimension() throws Exception {
        assertRejected(validJson().replace("营业收入同比12.30%", "建议买入，营业收入同比12.30%"),
                "投资建议");
        JsonNode root = json.readTree(validJson());
        ((com.fasterxml.jackson.databind.node.ArrayNode) root.get("dimensions")).remove(5);
        assertThrows(IllegalArgumentException.class, () -> gate.apply(root, packet));
    }

    @Test
    void acceptsExplicitlyNegatedInvestmentAdviceInTheDisclaimer() throws Exception {
        String disclaimer = "不建议买入或卖出，不提供目标价，也不保证收益或作出收益承诺。";
        String raw = validJson().replace("仅用于研究，不构成投资建议。", disclaimer);

        FinancialInterpretation.Result result = gate.apply(json.readTree(raw), packet);

        assertEquals(disclaimer, result.getDisclaimer());
    }

    private void assertRejected(String raw, String reason) throws Exception {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> gate.apply(json.readTree(raw), packet));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains(reason), error.getMessage());
    }

    private String validJson() {
        return "{" +
                "\"operatingState\":\"IMPROVING\"," +
                "\"confidence\":\"MEDIUM\"," +
                "\"executiveSummary\":[{\"claim\":\"营业收入同比12.30%\",\"claimType\":\"FACT\",\"refs\":[\"M_REVENUE_YOY\"]}]," +
                "\"periodChanges\":[" + claim() + "," + claim() + "]," +
                "\"crossStatementInsights\":[" + claim() + "," + claim() + "]," +
                "\"dimensions\":[" +
                dimension("GROWTH") + "," + dimension("PROFITABILITY") + "," +
                dimension("EARNINGS_QUALITY") + "," + dimension("CASH_QUALITY") + "," +
                dimension("ASSET_QUALITY") + "," + dimension("SOLVENCY_CAPITAL_DISCIPLINE") + "]," +
                "\"positiveSignals\":[],\"risks\":[],\"turningPoints\":[],\"watchpoints\":[]," +
                "\"limitations\":[\"仅基于2025年结构化财报\"]," +
                "\"disclaimer\":\"仅用于研究，不构成投资建议。\"}";
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
}
