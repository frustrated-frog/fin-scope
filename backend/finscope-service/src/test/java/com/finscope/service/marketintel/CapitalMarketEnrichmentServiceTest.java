package com.finscope.service.marketintel;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.tencent.TencentCapitalMarketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CapitalMarketEnrichmentServiceTest {
    private final TencentCapitalMarketClient market = mock(TencentCapitalMarketClient.class);
    private final CapitalMarketEnrichmentService service = new CapitalMarketEnrichmentService();
    private final Instrument stock = new Instrument();

    @BeforeEach
    void setUp() {
        stock.setId(40L);
        stock.setMarket("SH");
        stock.setCode("605058");
        ReflectionTestUtils.setField(service, "market", market);
    }

    @Test
    void fillsMatchingHistoricalGapsWithoutChangingFundFactsOrInventingNewDays() {
        CapitalFlowPoint original = point("DAY_1", "2026-11-10T15:00:00");
        original.setId(1L);
        original.setMainNetInflow(new BigDecimal("-12345"));
        CapitalFlowPoint bar = point("DAY_1", "2026-11-10T15:00:00");
        bar.setPrice(new BigDecimal("50.1"));
        bar.setIntervalTradeAmount(new BigDecimal("123456000"));
        bar.setTradeVolume(new BigDecimal("2464191"));
        bar.setTurnoverRate(new BigDecimal("8.1"));
        when(market.fetchDailyHistory(stock)).thenReturn(List.of(bar, point("DAY_1", "2026-11-11T15:00:00")));
        when(market.fetchQuote(stock)).thenReturn(quote());

        List<CapitalFlowPoint> result = service.enrich(stock, List.of(original));
        CapitalFlowPoint filled = result.get(0);
        assertEquals(2, result.size());
        assertEquals(bar.getIntervalTradeAmount(), filled.getIntervalTradeAmount());
        assertEquals(original.getMainNetInflow(), filled.getMainNetInflow());
        assertEquals(original.getObservedAt(), filled.getObservedAt());
        assertEquals(original.getProviderCode(), filled.getProviderCode());
        assertNotEquals(original.getPayloadHash(), filled.getPayloadHash());
        assertNull(filled.getId());
        assertNull(original.getIntervalTradeAmount());
        assertNull(filled.getVolumeRatio());
        assertEquals("COMPLETE", filled.getQualityStatus());
        assertEquals(filled.getPayloadHash(), service.enrich(stock, result).get(0).getPayloadHash());
        assertEquals(original.getObservedAt(), new CapitalBehaviorSnapshotFactory()
                .create(40L, result, List.of()).getAsOf());
    }

    @Test
    void freshQuoteUpdatesMarketMetricsWithoutFresheningOldFundsOrRecalculatingTheirRatio() {
        CapitalBehaviorMetrics metrics = new CapitalBehaviorMetrics();
        CapitalBehaviorMetrics.Latest latest = new CapitalBehaviorMetrics.Latest();
        latest.setObservedAt(LocalDateTime.parse("2026-11-11T15:00:00"));
        latest.setMainNetInflow(new BigDecimal("100"));
        latest.setMainNetInflowSharePct(new BigDecimal("2"));
        metrics.setLatest(latest);
        CapitalMarketEnrichmentService.applyMarketQuote(metrics, List.of(quote()));
        assertEquals(new BigDecimal("0.99"), latest.getVolumeRatio());
        assertEquals(new BigDecimal("9.88"), latest.getTurnoverRate());
        assertEquals("2026-11-11T15:00", latest.getObservedAt().toString());
        assertEquals("2026-11-12T13:50", latest.getMarketObservedAt().toString());
        assertEquals(new BigDecimal("100"), latest.getMainNetInflow());
        assertEquals(new BigDecimal("2"), latest.getMainNetInflowSharePct());
    }

    @Test
    void eachSourceCanFailIndependentlyAndPreviousQuoteSurvivesAnOutage() {
        CapitalFlowPoint original = point("DAY_1", "2026-11-11T15:00:00");
        when(market.fetchDailyHistory(stock)).thenThrow(new IllegalStateException("history unavailable"));
        when(market.fetchQuote(stock)).thenReturn(quote());
        assertEquals(2, service.enrich(stock, List.of(original)).size());
        when(market.fetchQuote(stock)).thenThrow(new IllegalStateException("quote unavailable"));
        List<CapitalFlowPoint> result = service.enrich(stock, List.of(original, quote()));
        assertEquals(2, result.size());
        assertNull(result.get(0).getIntervalTradeAmount());
        assertEquals("2026-11-12T13:50", CapitalMarketEnrichmentService.latestMarketQuote(result).getObservedAt().toString());
    }

    private CapitalFlowPoint quote() {
        CapitalFlowPoint quote = point("QUOTE", "2026-11-12T13:50:00");
        quote.setCumulativeTradeAmount(new BigDecimal("745500000"));
        quote.setTradeVolume(new BigDecimal("14115300"));
        quote.setTurnoverRate(new BigDecimal("9.88"));
        quote.setVolumeRatio(new BigDecimal("0.99"));
        return quote;
    }

    private CapitalFlowPoint point(String granularity, String time) {
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setInstrumentId(40L);
        point.setGranularity(granularity);
        point.setObservedAt(LocalDateTime.parse(time));
        point.setDataDate(point.getObservedAt().toLocalDate());
        point.setRetrievedAt(LocalDateTime.parse("2026-11-12T13:51:00"));
        point.setProviderCode("ORIGINAL_SOURCE");
        point.setPayloadHash(granularity + time);
        point.setCalculationVersion("fixture-v1");
        point.setQualityStatus("PARTIAL");
        return point;
    }
}
