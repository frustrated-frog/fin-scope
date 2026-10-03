package com.finscope.service.attribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.attribution.NewsImpactDirection;
import com.finscope.common.enums.attribution.NewsInterpretationType;
import com.finscope.domain.attribution.AttributionReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttributionNewsAnalysisParserTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void preservesMixedInterpretationAndOriginalReportWithUnknownOptionalValues() throws Exception {
        AttributionReport report = new AttributionReport();
        assertTrue(new AttributionAgent().parseSynthResult(report, """
                {"summary":"订单利好与毛利压力并存","narrative":{"causalSteps":["订单增长","收入预期提高"],
                "interactionAnalysis":"订单增加与原材料涨价部分抵消","priceNewsDivergence":"收入利好未必覆盖成本压力"},
                "drivers":[{"claim":"新订单","marketInterpretation":"关注实际交付而不是签约额", "newsAnalysis":{
                "types":["ORDER","PRICE_CHANGE","ORDER","NEW_TYPE",42],"direction":"MIXED",
                "keyChange":"订单确认但分期交付","businessImpacts":[{"label":"收入确认","analysis":"收入随交付确认"},
                {"label":"缺少内容"},false],"mediumTermImpact":"交付影响收入与回款","longTermImpact":{"invalid":true}}},
                {"claim":"既有业务","newsAnalysis":"malformed optional field"}]}
                """));
        var analysis = report.getDrivers().get(0).getNewsAnalysis();
        assertEquals(List.of(NewsInterpretationType.ORDER, NewsInterpretationType.PRICE_CHANGE), analysis.getTypes());
        assertEquals(NewsImpactDirection.MIXED, analysis.getDirection());
        assertEquals(1, analysis.getBusinessImpacts().size());
        assertEquals("", analysis.getLongTermImpact());
        assertEquals("交付影响收入与回款", analysis.getMediumTermImpact());
        assertEquals("关注实际交付而不是签约额", report.getDrivers().get(0).getMarketInterpretation());
        assertNull(report.getDrivers().get(1).getNewsAnalysis());
        assertEquals("订单增加与原材料涨价部分抵消", report.getNarrative().getInteractionAnalysis());
        assertEquals("收入利好未必覆盖成本压力", report.getNarrative().getPriceNewsDivergence());
        assertEquals(2, report.getNarrative().getCausalSteps().size());
    }

    @Test
    void omitsEmptyOrLegacyAnalysisWithoutInventingNeutralDirection() throws Exception {
        for (String input : List.of("null", "[]", "{}", "{\"types\":[\"ORDER\"],\"direction\":\"POSITIVE\"}")) {
            assertNull(AttributionNewsAnalysisParser.parse(json.readTree(input)));
        }
        var result = AttributionNewsAnalysisParser.parse(json.readTree("{\"keyChange\":\"交付进展\",\"direction\":\"bad\"}"));
        assertNull(result.getDirection());
        assertEquals("交付进展", result.getKeyChange());
    }

    @Test
    void boundsOversizedOptionalOutput() {
        var node = json.createObjectNode();
        node.put("keyChange", "a".repeat(400));
        node.putArray("types").add("ORDER").add("EARNINGS").add("TRADING").add("POLICY");
        var points = node.putArray("businessImpacts");
        for (int i = 0; i < 8; i++) {
            points.addObject().put("label", "订单兑现").put("analysis", "b".repeat(800));
        }
        var result = AttributionNewsAnalysisParser.parse(node);
        assertEquals(3, result.getTypes().size());
        assertEquals(300, result.getKeyChange().length());
        assertEquals(4, result.getBusinessImpacts().size());
        assertEquals(600, result.getBusinessImpacts().get(0).getAnalysis().length());
    }
}
