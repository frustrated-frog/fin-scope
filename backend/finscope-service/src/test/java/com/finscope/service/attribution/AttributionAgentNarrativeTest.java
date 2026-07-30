package com.finscope.service.attribution;

import com.finscope.domain.attribution.AttributionDriver;
import com.finscope.domain.attribution.AttributionEvidence;
import com.finscope.domain.attribution.AttributionReport;
import com.finscope.domain.instrument.Instrument;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributionAgentNarrativeTest {
    private final AttributionAgent agent = new AttributionAgent();

    @Test
    void parsesPlainLanguageNarrativeAndDriverRole() {
        AttributionReport report = new AttributionReport();
        boolean parsed = agent.parseSynthResult(report, "{\"summary\":\"综合结论\",\"narrative\":{"
                + "\"plainSummary\":\"先讲清主因\",\"event\":\"事件\",\"instrumentLink\":\"标的关联\","
                + "\"whyToday\":\"今日共振\",\"causalSteps\":[\"事件\",\"预期\",\"价格\"],"
                + "\"amplifiers\":[\"板块走弱\"],\"dampeners\":[\"尚无订单确认\"]},"
                + "\"drivers\":[{\"claim\":\"触发因素\",\"role\":\"TRIGGER\","
                + "\"plainExplanation\":\"市场担心需求后移\","
                + "\"marketInterpretation\":\"市场在交易订单预期落空\","
                + "\"expectationShift\":\"原本预期切入商业航天 → 现在确认仍以传统业务为主\","
                + "\"priceImpact\":\"成长想象空间收缩，提高风险溢价并压低估值\","
                + "\"explanatoryPower\":\"HIGH\","
                + "\"explanatoryPowerReason\":\"公告直接否定核心题材，且与当日下跌方向一致\","
                + "\"evidenceUrls\":[]}]}" );

        assertTrue(parsed);
        assertEquals("今日共振", report.getNarrative().getWhyToday());
        assertEquals(Arrays.asList("事件", "预期", "价格"), report.getNarrative().getCausalSteps());
        assertEquals("TRIGGER", report.getDrivers().get(0).getRole());
        assertEquals("市场担心需求后移", report.getDrivers().get(0).getPlainExplanation());
        AttributionDriver driver = report.getDrivers().get(0);
        assertEquals("市场在交易订单预期落空", driver.getMarketInterpretation());
        assertEquals("原本预期切入商业航天 → 现在确认仍以传统业务为主", driver.getExpectationShift());
        assertEquals("成长想象空间收缩，提高风险溢价并压低估值", driver.getPriceImpact());
        assertEquals("HIGH", driver.getExplanatoryPower());
        assertEquals("公告直接否定核心题材，且与当日下跌方向一致", driver.getExplanatoryPowerReason());
    }

    @Test
    void asksForBoundedMarketInterpretationInsteadOfRepeatingFacts() {
        String prompt = agent.synthUserPrompt(instrument("STOCK"), -4.2D,
                Collections.<AttributionEvidence>emptyList());

        assertTrue(prompt.contains("市场为什么在意"));
        assertTrue(prompt.contains("原本预期 → 现在预期"));
        assertTrue(prompt.contains("盈利预期、估值倍数、风险溢价或资金行为"));
        assertTrue(prompt.contains("不得虚构数字、业务暴露或投资者行为"));
    }

    @Test
    void givesEachInstrumentTypeItsOwnPriceTransmissionInstruction() {
        String stockPrompt = agent.synthUserPrompt(instrument("STOCK"), -2.1D, Collections.<AttributionEvidence>emptyList());
        String fundPrompt = agent.synthUserPrompt(instrument("FUND"), -2.1D, Collections.<AttributionEvidence>emptyList());
        String sectorPrompt = agent.synthUserPrompt(instrument("SECTOR"), -2.1D, Collections.<AttributionEvidence>emptyList());

        assertTrue(stockPrompt.contains("公司暴露"));
        assertTrue(fundPrompt.contains("组合暴露"));
        assertTrue(sectorPrompt.contains("成分股扩散"));
    }

    @Test
    void fallbackNarrativeNeverUsesHistoricalEvidenceAsTodaysEvent() {
        AttributionEvidence historical = evidence("昨日旧消息", true);
        AttributionEvidence current = evidence("今日直接线索", false);
        AttributionReport report = new AttributionReport();
        report.setSummary("今日出现下跌");

        agent.ensureNarrative(report, instrument("STOCK"), -2.1D, Arrays.asList(historical, current));

        assertEquals("今日直接线索", report.getNarrative().getEvent());
        assertFalse(report.getNarrative().getCausalSteps().contains("昨日旧消息"));
    }

    private Instrument instrument(String type) {
        Instrument instrument = new Instrument();
        instrument.setCode("603618");
        instrument.setName("测试标的");
        instrument.setType(type);
        return instrument;
    }

    private AttributionEvidence evidence(String title, boolean historical) {
        AttributionEvidence evidence = new AttributionEvidence();
        evidence.setTitle(title);
        evidence.setSnippet(title + "的说明");
        evidence.setHistoricalContext(historical);
        return evidence;
    }
}
