package com.finscope.service.instrument;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.domain.instrument.FundHoldingDisclosure;
import com.finscope.domain.instrument.FundStockHolding;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.rpc.quote.FundHoldingProvider;
import com.finscope.service.marketdata.MarketDataGateway;
import com.finscope.service.marketdata.QuoteGatewayResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundHoldingDetailServiceTest {
    private WatchlistRepository watchlistRepository;
    private FundHoldingProvider holdingProvider;
    private MarketDataGateway marketDataGateway;
    private FundHoldingDetailService service;

    @BeforeEach
    void setUp() {
        watchlistRepository = mock(WatchlistRepository.class);
        holdingProvider = mock(FundHoldingProvider.class);
        marketDataGateway = mock(MarketDataGateway.class);
        service = new FundHoldingDetailService(
                watchlistRepository, holdingProvider, marketDataGateway);
        when(watchlistRepository.findByCodeAndType("021894", "FUND"))
                .thenReturn(Optional.of(fundItem("021894", "易方达半导体设备ETF联接C")));
    }

    @Test
    void calculatesDisclosureWeightedContributionsWithOneBatchQuoteRequest() {
        when(holdingProvider.fetch("021894")).thenReturn(disclosure(
                holding(1, "688012", "中微公司", 8.0d),
                holding(2, "688120", "华海清科", 4.0d)));
        when(marketDataGateway.fetchQuotes("STOCK",
                Arrays.asList("688012", "688120"), true))
                .thenReturn(batch(MarketDataQualityStatus.FRESH_PRIMARY,
                        quote("688012", 468.50d, 2.0d, MarketDataQualityStatus.FRESH_PRIMARY),
                        quote("688120", 322.00d, -1.0d, MarketDataQualityStatus.FRESH_PRIMARY)));

        FundHoldingDetail result = service.load("021894", true);

        assertEquals(12.0d, result.getTopHoldingsWeightPct(), 0.000001d);
        assertEquals(0.16d, result.getPositions().get(0).getEstimatedContributionPct(), 0.000001d);
        assertEquals(-0.04d, result.getPositions().get(1).getEstimatedContributionPct(), 0.000001d);
        assertEquals(0.12d, result.getEstimatedContributionPct(), 0.000001d);
        assertEquals(2, result.getEstimatedHoldingCount());
        assertEquals(2, result.getTotalHoldingCount());
        verify(marketDataGateway).fetchQuotes("STOCK",
                Arrays.asList("688012", "688120"), true);
    }

    @Test
    void excludesStaleAndInvalidQuotesFromContribution() {
        when(holdingProvider.fetch("021894")).thenReturn(disclosure(
                holding(1, "688012", "中微公司", 8.0d),
                holding(2, "688120", "华海清科", 4.0d)));
        Quote invalid = quote("688120", null, 3.0d, MarketDataQualityStatus.FRESH_PRIMARY);
        invalid.setValid(false);
        when(marketDataGateway.fetchQuotes(eq("STOCK"), anyList(), eq(true)))
                .thenReturn(batch(MarketDataQualityStatus.PARTIAL_FRESH,
                        quote("688012", 468.50d, 2.0d, MarketDataQualityStatus.FRESH_PRIMARY),
                        invalid));

        FundHoldingDetail result = service.load("021894", true);

        assertNotNull(result.getPositions().get(0).getEstimatedContributionPct());
        assertNull(result.getPositions().get(1).getEstimatedContributionPct());
        assertFalse(result.getPositions().get(1).isQuoteValid());
        assertEquals(1, result.getEstimatedHoldingCount());
        assertEquals(0.16d, result.getEstimatedContributionPct(), 0.000001d);
    }

    @Test
    void suppressesEveryContributionWhenTheBatchFallsBackToStaleQuotes() {
        when(holdingProvider.fetch("021894")).thenReturn(disclosure(
                holding(1, "688012", "中微公司", 8.0d)));
        when(marketDataGateway.fetchQuotes(eq("STOCK"), anyList(), eq(true)))
                .thenReturn(batch(MarketDataQualityStatus.STALE_FALLBACK,
                        quote("688012", 468.50d, 2.0d, MarketDataQualityStatus.STALE_FALLBACK)));

        FundHoldingDetail result = service.load("021894", true);

        assertNull(result.getEstimatedContributionPct());
        assertNull(result.getPositions().get(0).getEstimatedContributionPct());
        assertEquals(0, result.getEstimatedHoldingCount());
    }

    @Test
    void skipsQuoteGatewayForAnEmptyDisclosureAndExplainsEtfLinkBoundary() {
        when(holdingProvider.fetch("021894")).thenReturn(disclosure());

        FundHoldingDetail result = service.load("021894", true);

        assertTrue(result.getPositions().isEmpty());
        assertNull(result.getEstimatedContributionPct());
        assertFalse(result.isLookThrough());
        assertTrue(result.getNote().contains("目标 ETF"));
        verify(marketDataGateway, never()).fetchQuotes(eq("STOCK"), anyList(), eq(true));
    }

    @Test
    void rejectsARequestedCodeThatIsNotAWatchlistFund() {
        when(watchlistRepository.findByCodeAndType("000001", "FUND"))
                .thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.load("000001", true));

        assertTrue(error.getMessage().contains("自选"));
        verify(holdingProvider, never()).fetch("000001");
    }

    private FundHoldingDisclosure disclosure(FundStockHolding... holdings) {
        return new FundHoldingDisclosure("021894", "易方达半导体设备ETF联接C",
                LocalDate.of(2026, 6, 30), LocalDateTime.of(2026, 8, 10, 14, 30),
                Arrays.asList(holdings));
    }

    private FundStockHolding holding(int rank, String code, String name, double weightPct) {
        return new FundStockHolding(rank, code, name, weightPct, 1.0d, 100.0d);
    }

    private Quote quote(String code, Double price, Double changePct,
                        MarketDataQualityStatus qualityStatus) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        quote.setName(code);
        quote.setPrice(price);
        quote.setChangePct(changePct);
        quote.setValid(true);
        quote.setQualityStatus(qualityStatus);
        quote.setSourceCode("TENCENT_STOCK");
        quote.setQuoteTime(LocalDateTime.of(2026, 8, 10, 14, 29, 58));
        return quote;
    }

    private QuoteGatewayResult batch(MarketDataQualityStatus qualityStatus, Quote... quotes) {
        return new QuoteGatewayResult(Arrays.asList(quotes), qualityStatus,
                "TENCENT_STOCK", LocalDateTime.of(2026, 8, 10, 14, 29, 58),
                LocalDateTime.of(2026, 8, 10, 14, 30), null, null, "refresh-1");
    }

    private WatchlistItem fundItem(String code, String name) {
        WatchlistItem item = new WatchlistItem();
        item.setId(1L);
        item.setCode(code);
        item.setType("FUND");
        item.setName(name);
        return item;
    }
}
