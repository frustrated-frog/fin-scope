package com.finscope.service.instrument;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.marketdata.MarketDataGateway;
import com.finscope.service.marketdata.SectorCatalogGatewayResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SectorMarketServiceTest {
    private final MarketDataGateway gateway = mock(MarketDataGateway.class);
    private final SectorMarketService service = new SectorMarketService(gateway);

    @Test
    void ranksOneGatewaySnapshotDeterministicallyWithoutOverlap() {
        when(gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, true)).thenReturn(result(
                MarketDataQualityStatus.FRESH_PRIMARY,
                entry("BK0001", "甲", 4.0, 100.0),
                entry("BK0002", "乙", -3.0, 90.0),
                entry("BK0003", "丙", 4.0, 120.0),
                entry("BK0004", "丁", -2.0, 80.0)));

        SectorMarketOverview overview = service.overview(SectorCategory.INDUSTRY, 2, true);

        assertEquals(Arrays.asList("BK0003", "BK0001"), codes(overview.getLeaders()));
        assertEquals(Arrays.asList("BK0002", "BK0004"), codes(overview.getLaggards()));
        assertEquals(MarketDataQualityStatus.FRESH_PRIMARY, overview.getQualityStatus());
        verify(gateway).fetchSectorCatalog(SectorCategory.INDUSTRY, true);
    }

    @Test
    void preservesGatewayDegradationMetadata() {
        SectorCatalogGatewayResult gatewayResult = new SectorCatalogGatewayResult(
                snapshot(entry("BK0001", "甲", 1.0, 100.0)),
                MarketDataQualityStatus.STALE_FALLBACK, "EASTMONEY_SECTOR",
                LocalDateTime.of(2026, 7, 14, 9, 58), LocalDateTime.of(2026, 7, 14, 9, 58, 5),
                125L, "正在显示最近一次成功目录", "refresh-sector");
        when(gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, false)).thenReturn(gatewayResult);

        SectorMarketOverview overview = service.overview(SectorCategory.INDUSTRY, 5, false);

        assertEquals(MarketDataQualityStatus.STALE_FALLBACK, overview.getQualityStatus());
        assertEquals("EASTMONEY_SECTOR", overview.getSourceCode());
        assertEquals(125L, overview.getStaleAgeSeconds());
        assertEquals("refresh-sector", overview.getRefreshId());
    }

    @Test
    void searchesExactCodeAndNameMatchesInPriorityOrder() {
        when(gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, false)).thenReturn(result(
                MarketDataQualityStatus.FRESH_PRIMARY,
                entry("BK1036", "半导体", 2.0, 100.0),
                entry("BK2000", "半导体设备", 3.0, 80.0),
                entry("BK3000", "先进半导体材料", 4.0, 70.0)));

        assertEquals(Collections.singletonList("BK1036"),
                codes(service.search("bk1036", SectorCategory.INDUSTRY, 10).getItems()));
        assertEquals(Arrays.asList("BK1036", "BK2000", "BK3000"),
                codes(service.search("半导体", SectorCategory.INDUSTRY, 10).getItems()));
    }

    private SectorCatalogGatewayResult result(MarketDataQualityStatus status, SectorMarketEntry... entries) {
        LocalDateTime retrievedAt = LocalDateTime.of(2026, 7, 14, 10, 0);
        return new SectorCatalogGatewayResult(snapshot(entries), status, "EASTMONEY_SECTOR",
                retrievedAt, retrievedAt, null, null, "refresh-sector");
    }

    private SectorMarketSnapshot snapshot(SectorMarketEntry... entries) {
        return new SectorMarketSnapshot(SectorCategory.INDUSTRY, "EASTMONEY_SECTOR",
                LocalDateTime.of(2026, 7, 14, 10, 0), "hash", Arrays.asList(entries),
                Collections.<String>emptyList());
    }

    private SectorMarketEntry entry(String code, String name, double changePct, double turnover) {
        SectorMarketEntry value = new SectorMarketEntry();
        value.setCode(code);
        value.setName(name);
        value.setCategory(SectorCategory.INDUSTRY);
        value.setChangePct(changePct);
        value.setTurnover(turnover);
        return value;
    }

    private List<String> codes(List<SectorMarketEntry> values) {
        return values.stream().map(SectorMarketEntry::getCode).collect(Collectors.toList());
    }
}
