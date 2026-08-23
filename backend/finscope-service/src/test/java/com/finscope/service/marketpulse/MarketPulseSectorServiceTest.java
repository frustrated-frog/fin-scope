package com.finscope.service.marketpulse;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.marketpulse.SectorHistoryItem;
import com.finscope.domain.marketpulse.SectorHistorySnapshot;
import com.finscope.domain.marketpulse.MarketPulseSectorResult;
import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.rpc.marketpulse.SectorHistorySource;
import com.finscope.service.instrument.SectorMarketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class MarketPulseSectorServiceTest {
    private MarketPulseSectorService service;
    private SectorMarketService market;
    private SectorHistorySource history;
    private MarketPulseRepository repository;

    @BeforeEach
    void setUp() {
        service = new MarketPulseSectorService();
        market = mock(SectorMarketService.class);
        history = mock(SectorHistorySource.class);
        repository = mock(MarketPulseRepository.class);
        ReflectionTestUtils.setField(service, "sectorMarketService", market);
        ReflectionTestUtils.setField(service, "historySource", history);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "scoringService", new SectorRotationScoringService());
    }

    @Test
    void scoresAllIndustriesFromProviderHistoryOnTheFirstRefresh() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        when(market.listEntries(SectorCategory.INDUSTRY, true)).thenReturn(Arrays.asList(
                market("881121", "半导体", 0.8D, 1_200_000_000D, 1, 0.75D),
                market("881273", "白酒", -1.1D, -350_000_000D, 2, 0.25D)));
        when(history.fetch(date, 60)).thenReturn(history(date,
                item("881121", "半导体", 0.8D, 3.2D, 6.5D, 4),
                item("881273", "白酒", -1.1D, -2.4D, -4.5D, 1)));

        List<SectorRotationItem> result = service.calculate(date);

        assertEquals(2, result.size());
        SectorRotationItem semiconductor = find(result, "881121");
        SectorRotationItem liquor = find(result, "881273");
        assertEquals(3.2D, semiconductor.getReturn5d());
        assertEquals(6.5D, semiconductor.getReturn20d());
        assertEquals(4, semiconductor.getPersistenceDays());
        assertNotEquals(25, semiconductor.getRotationScore());
        assertEquals(-2.4D, liquor.getReturn5d());
        verify(repository).findRecentDates(1, date.minusDays(1));
    }

    @Test
    void stillUsesHistoryWhenTheCurrentMoneyFlowCatalogIsUnavailable() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        when(market.listEntries(SectorCategory.INDUSTRY, true)).thenReturn(Collections.emptyList());
        when(history.fetch(date, 60)).thenReturn(history(date,
                item("881121", "半导体", 0.8D, 3.2D, 6.5D, 4)));

        SectorRotationItem result = service.calculate(date).get(0);

        assertEquals("半导体", result.getSectorName());
        assertEquals(3.2D, result.getReturn5d());
        assertEquals(null, result.getMainNetInflow());
    }

    @Test
    void propagatesPartialHistoryQualityAndWarnings() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        SectorHistorySnapshot snapshot = history(date,
                item("881121", "半导体", 0.8D, 3.2D, 6.5D, 4));
        snapshot.setQualityStatus("PARTIAL_FRESH");
        snapshot.setWarnings(Collections.singletonList("白酒行业历史不可用"));
        when(market.listEntries(SectorCategory.INDUSTRY, true)).thenReturn(Collections.emptyList());
        when(history.fetch(date, 60)).thenReturn(snapshot);

        MarketPulseSectorResult result = service.calculateResult(date);

        assertEquals("PARTIAL", result.getQualityStatus().name());
        assertTrue(result.getWarnings().get(0).contains("白酒"));
    }

    private SectorMarketEntry market(String code, String name, double change, double flow,
                                     int rank, double breadth) {
        SectorMarketEntry value = new SectorMarketEntry();
        value.setCode(code);
        value.setName(name);
        value.setCategory(SectorCategory.INDUSTRY);
        value.setChangePct(change);
        value.setMainNetInflow(flow);
        value.setSourceRank(rank);
        value.setBreadthRatio(breadth);
        return value;
    }

    private SectorHistoryItem item(String code, String name, double one, double five,
                                   double twenty, int positiveDays) {
        SectorHistoryItem value = new SectorHistoryItem();
        value.setSectorCode(code);
        value.setSectorName(name);
        value.setLastTradeDate(LocalDate.of(2026, 8, 21));
        value.setCoverageDays(60);
        value.setReturn1d(one);
        value.setReturn5d(five);
        value.setReturn20d(twenty);
        value.setPositiveDays5(positiveDays);
        return value;
    }

    private SectorHistorySnapshot history(LocalDate date, SectorHistoryItem... items) {
        SectorHistorySnapshot value = new SectorHistorySnapshot();
        value.setBusinessDate(date);
        value.setSourceCode("AKSHARE_TONGHUASHUN_SECTOR_HISTORY");
        value.setSourceFamily("TONGHUASHUN");
        value.setQualityStatus("FRESH_PRIMARY");
        value.setRetrievedAt(LocalDateTime.of(2026, 8, 23, 18, 0));
        value.setRequestedWindow(60);
        value.setEntries(Arrays.asList(items));
        return value;
    }

    private SectorRotationItem find(List<SectorRotationItem> values, String code) {
        return values.stream().filter(value -> code.equals(value.getSectorCode()))
                .findFirst().orElseThrow(AssertionError::new);
    }
}
