package com.finscope.service.instrument;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.service.marketdata.MarketDataGateway;
import com.finscope.service.marketdata.SectorCatalogGatewayResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
    private final SectorMarketService service = service();

    @Test
    void ranksIndustrySnapshotByTonghuashunMainNetInflowWithoutOverlap() {
        when(gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, true)).thenReturn(result(
                MarketDataQualityStatus.FRESH_PRIMARY,
                entry("881001", "甲", 4.0, 100.0),
                entry("881002", "乙", -3.0, -90.0),
                entry("881003", "丙", 1.0, 120.0),
                entry("881004", "丁", -2.0, -80.0)));

        SectorMarketOverview overview = service.overview(SectorCategory.INDUSTRY, 2, true);

        assertEquals(Arrays.asList("881003", "881001"), codes(overview.getLeaders()));
        assertEquals(Arrays.asList("881002", "881004"), codes(overview.getLaggards()));
        assertEquals(MarketDataQualityStatus.FRESH_PRIMARY, overview.getQualityStatus());
        verify(gateway).fetchSectorCatalog(SectorCategory.INDUSTRY, true);
    }

    @Test
    void exposesTheCompleteIndustrySnapshotForMarketPulse() {
        when(gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, true)).thenReturn(result(
                MarketDataQualityStatus.FRESH_PRIMARY,
                entry("881001", "甲", 4.0, 100.0),
                entry("881002", "乙", -3.0, -90.0),
                entry("881003", "丙", 1.0, 120.0)));

        List<SectorMarketEntry> entries = service.listEntries(SectorCategory.INDUSTRY, true);

        assertEquals(Arrays.asList("881001", "881002", "881003"), codes(entries));
    }

    @Test
    void preservesGatewayDegradationMetadata() {
        SectorCatalogGatewayResult gatewayResult = new SectorCatalogGatewayResult(
                snapshot(entry("881001", "甲", 1.0, 100.0)),
                MarketDataQualityStatus.STALE_FALLBACK, "PYTHON_TONGHUASHUN_SECTOR",
                LocalDateTime.of(2026, 7, 14, 9, 58), LocalDateTime.of(2026, 7, 14, 9, 58, 5),
                125L, "正在显示最近一次成功目录", "refresh-sector");
        when(gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, false)).thenReturn(gatewayResult);

        SectorMarketOverview overview = service.overview(SectorCategory.INDUSTRY, 5, false);

        assertEquals(MarketDataQualityStatus.STALE_FALLBACK, overview.getQualityStatus());
        assertEquals("PYTHON_TONGHUASHUN_SECTOR", overview.getSourceCode());
        assertEquals(125L, overview.getStaleAgeSeconds());
        assertEquals("refresh-sector", overview.getRefreshId());
    }

    @Test
    void doesNotRepeatProviderWarningAlreadyIncludedByGateway() {
        String providerWarning = "同花顺目录有 1 条记录缺少代码";
        SectorMarketSnapshot snapshot = new SectorMarketSnapshot(SectorCategory.INDUSTRY,
                "PYTHON_TONGHUASHUN_SECTOR", LocalDateTime.of(2026, 7, 14, 10, 0), "hash",
                Collections.singletonList(entry("881121", "半导体", 2.0, 100.0)),
                Collections.singletonList(providerWarning));
        when(gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, true)).thenReturn(
                new SectorCatalogGatewayResult(snapshot, MarketDataQualityStatus.FRESH_FALLBACK,
                        "PYTHON_TONGHUASHUN_SECTOR", snapshot.getRetrievedAt(), snapshot.getRetrievedAt(), null,
                        "主数据源不可用；" + providerWarning, "refresh-sector"));

        SectorMarketOverview overview = service.overview(SectorCategory.INDUSTRY, 5, true);

        assertEquals("主数据源不可用；" + providerWarning, overview.getWarning());
    }

    @Test
    void searchesExactCodeAndNameMatchesInPriorityOrder() {
        when(gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, false)).thenReturn(result(
                MarketDataQualityStatus.FRESH_PRIMARY,
                entry("881121", "半导体", 2.0, 100.0),
                entry("881201", "半导体设备", 3.0, 80.0),
                entry("881301", "先进半导体材料", 4.0, 70.0)));

        assertEquals(Collections.singletonList("881121"),
                codes(service.search("881121", SectorCategory.INDUSTRY, 10).getItems()));
        assertEquals(Arrays.asList("881121", "881201", "881301"),
                codes(service.search("半导体", SectorCategory.INDUSTRY, 10).getItems()));
    }

    @Test
    void marksCrossCategorySearchPartialWhenFreshAndStaleCatalogsAreMixed() {
        when(gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, false)).thenReturn(result(
                MarketDataQualityStatus.FRESH_PRIMARY,
                entry("881121", "半导体", 2.0, 100.0)));
        when(gateway.fetchSectorCatalog(SectorCategory.CONCEPT, false)).thenReturn(result(
                MarketDataQualityStatus.STALE_FALLBACK,
                entry("301558", "半导体设备", 3.0, 80.0)));

        SectorMarketSearchResult search = service.search("半导体", null, 10);

        assertEquals(MarketDataQualityStatus.PARTIAL_FRESH, search.getQualityStatus());
    }

    @Test
    void resolvesFollowedCodesAcrossIndustryAndConceptCatalogs() {
        when(gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, true)).thenReturn(result(
                MarketDataQualityStatus.FRESH_PRIMARY,
                entry("881121", "半导体", 2.0, 100.0)));
        when(gateway.fetchSectorCatalog(SectorCategory.CONCEPT, true)).thenReturn(result(
                MarketDataQualityStatus.FRESH_PRIMARY,
                entry("301558", "阿里巴巴概念", 1.0, 0.0)));

        assertEquals(Arrays.asList("881121", "301558"), new java.util.ArrayList<String>(
                service.findByCodes(Arrays.asList("881121", "301558"), true).keySet()));
    }

    private SectorCatalogGatewayResult result(MarketDataQualityStatus status, SectorMarketEntry... entries) {
        LocalDateTime retrievedAt = LocalDateTime.of(2026, 7, 14, 10, 0);
        return new SectorCatalogGatewayResult(snapshot(entries), status, "PYTHON_TONGHUASHUN_SECTOR",
                retrievedAt, retrievedAt, null, null, "refresh-sector");
    }

    private SectorMarketSnapshot snapshot(SectorMarketEntry... entries) {
        SectorCategory category = entries.length == 0 || entries[0].getCategory() == null
                ? SectorCategory.INDUSTRY : entries[0].getCategory();
        return new SectorMarketSnapshot(category, "PYTHON_TONGHUASHUN_SECTOR",
                LocalDateTime.of(2026, 7, 14, 10, 0), "hash", Arrays.asList(entries),
                Collections.<String>emptyList());
    }

    private SectorMarketEntry entry(String code, String name, double changePct, double mainNetInflow) {
        SectorMarketEntry value = new SectorMarketEntry();
        value.setCode(code);
        value.setName(name);
        value.setCategory(code.startsWith("88") ? SectorCategory.INDUSTRY : SectorCategory.CONCEPT);
        value.setChangePct(changePct);
        value.setMainNetInflow(mainNetInflow);
        return value;
    }

    private SectorMarketService service() {
        SectorMarketService value = new SectorMarketService();
        ReflectionTestUtils.setField(value, "gateway", gateway);
        return value;
    }

    private List<String> codes(List<SectorMarketEntry> values) {
        return values.stream().map(SectorMarketEntry::getCode).collect(Collectors.toList());
    }
}
