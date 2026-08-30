package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.dao.quant.StockDiscoveryRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.marketpulse.MarketEventConfirmation;
import com.finscope.domain.marketpulse.DailyMarketReview;
import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketIndexPerformance;
import com.finscope.domain.marketpulse.MarketPulseCandidate;
import com.finscope.domain.marketpulse.MarketPulseBackfillResult;
import com.finscope.domain.marketpulse.MarketPulseRefreshResult;
import com.finscope.domain.marketpulse.MarketPulseSectorResult;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.MarketRegimeSnapshot;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.domain.quant.discovery.StockDiscoveryCandidate;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketPulseServiceTest {
    private MarketPulseService service;
    private MarketPulseFeatureService features;
    private MarketBreadthService breadthService;
    private MarketPulseSectorService sectors;
    private RadarRepository radarRepository;
    private MarketEventConfirmationService confirmations;
    private StockDiscoveryRepository discoveryRepository;
    private MarketPulseCandidateService candidates;
    private MarketPulseRepository repository;
    private DailyMarketReviewService reviewService;

    @BeforeEach
    void setUp() {
        service = new MarketPulseService();
        features = mock(MarketPulseFeatureService.class);
        breadthService = mock(MarketBreadthService.class);
        sectors = mock(MarketPulseSectorService.class);
        radarRepository = mock(RadarRepository.class);
        confirmations = mock(MarketEventConfirmationService.class);
        discoveryRepository = mock(StockDiscoveryRepository.class);
        candidates = mock(MarketPulseCandidateService.class);
        repository = mock(MarketPulseRepository.class);
        reviewService = mock(DailyMarketReviewService.class);
        ReflectionTestUtils.setField(service, "featureService", features);
        ReflectionTestUtils.setField(service, "breadthService", breadthService);
        ReflectionTestUtils.setField(service, "sectorService", sectors);
        ReflectionTestUtils.setField(service, "radarRepository", radarRepository);
        ReflectionTestUtils.setField(service, "confirmationService", confirmations);
        ReflectionTestUtils.setField(service, "discoveryRepository", discoveryRepository);
        ReflectionTestUtils.setField(service, "candidateService", candidates);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "reviewService", reviewService);
        when(reviewService.generate(any(MarketPulseWorkspace.class))).thenReturn(new DailyMarketReview());
    }

    @Test
    void refreshesTheLatestTradingDateInsteadOfTheNaturalDate() {
        LocalDate latestTradingDate = LocalDate.of(2026, 8, 21);
        MarketRegimeSnapshot regime = new MarketRegimeSnapshot();
        regime.setBusinessDate(latestTradingDate);
        regime.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        when(features.latestBusinessDate()).thenReturn(latestTradingDate);
        when(sectors.calculateResult(latestTradingDate)).thenReturn(sectorResult(Collections.emptyList()));
        when(breadthService.calculate(latestTradingDate)).thenReturn(breadth(latestTradingDate));
        when(sectors.dispersion(Collections.emptyList())).thenReturn(0D);
        when(features.calculate(latestTradingDate, 0D, 0.6D)).thenReturn(regime);
        when(radarRepository.findEventsBetween(any(), any(), anyInt())).thenReturn(Collections.emptyList());
        when(confirmations.confirm(Collections.emptyList(), Collections.emptyList()))
                .thenReturn(Collections.emptyList());
        when(discoveryRepository.findLatestSuccessOnOrBefore(latestTradingDate)).thenReturn(Optional.empty());

        MarketPulseRefreshResult result = service.refresh();

        assertEquals(latestTradingDate, result.getBusinessDate());
        verify(sectors).calculateResult(latestTradingDate);
    }

    @Test
    void refreshesOneFrozenWorkspaceAndReusesVerifiedDiscoveryCandidates() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        when(features.latestBusinessDate()).thenReturn(date);
        MarketRegimeSnapshot regime = new MarketRegimeSnapshot();
        regime.setBusinessDate(date);
        regime.setMarketStage(MarketStage.RANGE_ROTATION);
        regime.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        SectorRotationItem sector = new SectorRotationItem();
        RadarEvent event = new RadarEvent();
        MarketEventConfirmation confirmation = new MarketEventConfirmation();
        StockDiscoveryRun run = new StockDiscoveryRun();
        run.setId(9L);
        MarketPulseCandidate candidate = new MarketPulseCandidate();
        when(sectors.calculateResult(date)).thenReturn(sectorResult(Collections.singletonList(sector)));
        when(breadthService.calculate(date)).thenReturn(breadth(date));
        when(sectors.dispersion(Collections.singletonList(sector))).thenReturn(0.02D);
        when(features.calculate(date, 0.02D, 0.6D)).thenReturn(regime);
        when(radarRepository.findEventsBetween(any(), any(), anyInt())).thenReturn(Collections.singletonList(event));
        when(confirmations.confirm(Collections.singletonList(event), Collections.singletonList(sector)))
                .thenReturn(Collections.singletonList(confirmation));
        when(discoveryRepository.findLatestSuccessOnOrBefore(date)).thenReturn(Optional.of(run));
        List<StockDiscoveryCandidate> frozen = Collections.singletonList(new StockDiscoveryCandidate());
        when(discoveryRepository.findCandidatesByRunId(9L)).thenReturn(frozen);
        when(candidates.assemble(run, frozen, Collections.singletonList(sector)))
                .thenReturn(Collections.singletonList(candidate));

        MarketPulseRefreshResult result = service.refresh(date);

        assertEquals(1, result.getSectorCount());
        assertEquals(1, result.getEventConfirmationCount());
        assertEquals(1, result.getCandidateCount());
        verify(repository).saveWorkspace(any(MarketPulseWorkspace.class));
        verify(reviewService).generate(any(MarketPulseWorkspace.class));
    }

    @Test
    void keepsZeroCandidatesAsSuccessfulResearchResult() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        when(features.latestBusinessDate()).thenReturn(date);
        MarketRegimeSnapshot regime = new MarketRegimeSnapshot();
        regime.setBusinessDate(date);
        regime.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        when(sectors.calculateResult(date)).thenReturn(sectorResult(Collections.emptyList()));
        when(breadthService.calculate(date)).thenReturn(breadth(date));
        when(sectors.dispersion(Collections.emptyList())).thenReturn(0D);
        when(features.calculate(date, 0D, 0.6D)).thenReturn(regime);
        when(radarRepository.findEventsBetween(any(), any(), anyInt())).thenReturn(Collections.emptyList());
        when(confirmations.confirm(Collections.emptyList(), Collections.emptyList()))
                .thenReturn(Collections.emptyList());
        when(discoveryRepository.findLatestSuccessOnOrBefore(date)).thenReturn(Optional.empty());

        MarketPulseRefreshResult result = service.refresh(date);

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(0, result.getCandidateCount());
        verify(repository).saveWorkspace(any(MarketPulseWorkspace.class));
    }

    @Test
    void rejectsRefreshingAnOldDateInsteadOfMixingFutureInputsIntoHistory() {
        when(features.latestBusinessDate()).thenReturn(LocalDate.of(2026, 8, 21));

        assertThrows(IllegalArgumentException.class,
                () -> service.refresh(LocalDate.of(2026, 8, 20)));
    }

    @Test
    void scheduledRefreshOnlyRunsWhenTheExpectedTradingDateIsAvailable() {
        LocalDate expectedDate = LocalDate.of(2026, 8, 24);
        when(features.latestBusinessDate()).thenReturn(LocalDate.of(2026, 8, 21));

        Optional<MarketPulseRefreshResult> result = service.refreshScheduled(expectedDate);

        assertTrue(!result.isPresent());
        verify(sectors, never()).calculateResult(any(LocalDate.class));
        verify(repository, never()).saveWorkspace(any(MarketPulseWorkspace.class));
    }

    @Test
    void recoverySkipsAnExistingFrozenWorkspace() {
        LocalDate latestTradingDate = LocalDate.of(2026, 8, 21);
        when(features.latestBusinessDate()).thenReturn(latestTradingDate);
        MarketPulseWorkspace existing = workspace(latestTradingDate, breadth(latestTradingDate));
        existing.setGeneratedAt(LocalDateTime.of(2026, 8, 21, 15, 31));
        when(repository.findWorkspace(latestTradingDate)).thenReturn(Optional.of(existing));

        Optional<MarketPulseRefreshResult> result = service.recoverMissing();

        assertTrue(!result.isPresent());
        verify(sectors, never()).calculateResult(any(LocalDate.class));
        verify(repository, never()).saveWorkspace(any(MarketPulseWorkspace.class));
    }

    @Test
    void recoveryReplacesAnIntradaySnapshotWithAnAfterCloseSnapshot() {
        LocalDate latestTradingDate = LocalDate.of(2026, 8, 21);
        prepareEmptyRefresh(latestTradingDate);
        MarketPulseWorkspace intraday = workspace(latestTradingDate, breadth(latestTradingDate));
        intraday.setGeneratedAt(LocalDateTime.of(2026, 8, 21, 11, 30));
        when(repository.findWorkspace(latestTradingDate)).thenReturn(Optional.of(intraday));

        Optional<MarketPulseRefreshResult> result = service.recoverMissing();

        assertTrue(result.isPresent());
        verify(repository).saveWorkspace(any(MarketPulseWorkspace.class));
    }

    @Test
    void recoveryBuildsTheLatestTradingDateWhenItsSnapshotIsMissing() {
        LocalDate latestTradingDate = LocalDate.of(2026, 8, 21);
        prepareEmptyRefresh(latestTradingDate);
        when(repository.findWorkspace(latestTradingDate)).thenReturn(Optional.empty());

        Optional<MarketPulseRefreshResult> result = service.recoverMissing();

        assertTrue(result.isPresent());
        assertEquals(latestTradingDate, result.get().getBusinessDate());
        verify(repository).saveWorkspace(any(MarketPulseWorkspace.class));
    }

    @Test
    void manualAndAutomaticRefreshesShareOneExecutionGuard() {
        ReflectionTestUtils.setField(service, "refreshRunning", new AtomicBoolean(true));

        IllegalStateException error = assertThrows(IllegalStateException.class, service::refresh);

        assertEquals("市场机会判断正在刷新，请稍后重试", error.getMessage());
        assertTrue(!service.refreshScheduled(LocalDate.of(2026, 8, 21)).isPresent());
        assertTrue(!service.recoverMissing().isPresent());
        verify(features, never()).latestBusinessDate();
    }

    @Test
    void lowersWorkspaceQualityWhenIndustryHistoryIsPartial() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        when(features.latestBusinessDate()).thenReturn(date);
        MarketPulseSectorResult sectorResult = sectorResult(Collections.emptyList());
        sectorResult.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        sectorResult.setWarnings(Collections.singletonList("一个行业历史不可用"));
        when(sectors.calculateResult(date)).thenReturn(sectorResult);
        MarketBreadthSnapshot breadth = breadth(date);
        breadth.setIndices(java.util.Arrays.asList(index(), index(), index(), index(), index()));
        when(breadthService.calculate(date)).thenReturn(breadth);
        when(sectors.dispersion(Collections.emptyList())).thenReturn(0D);
        MarketRegimeSnapshot regime = new MarketRegimeSnapshot();
        regime.setQualityStatus(MarketPulseQualityStatus.READY);
        when(features.calculate(date, 0D, 0.6D)).thenReturn(regime);
        when(radarRepository.findEventsBetween(any(), any(), anyInt())).thenReturn(Collections.emptyList());
        when(confirmations.confirm(Collections.emptyList(), Collections.emptyList()))
                .thenReturn(Collections.emptyList());
        when(discoveryRepository.findLatestSuccessOnOrBefore(date)).thenReturn(Optional.empty());

        service.refresh(date);

        ArgumentCaptor<MarketPulseWorkspace> captor = ArgumentCaptor.forClass(MarketPulseWorkspace.class);
        verify(repository).saveWorkspace(captor.capture());
        assertEquals(MarketPulseQualityStatus.PARTIAL, captor.getValue().getQualityStatus());
        assertTrue(captor.getValue().getWarnings().contains("一个行业历史不可用"));
    }

    @Test
    void backfillsFiveBusinessDatesInOrderAndKeepsTodayRefreshGuarded() {
        LocalDate start = LocalDate.of(2026, 8, 17);
        LocalDate end = LocalDate.of(2026, 8, 21);
        List<LocalDate> dates = Arrays.asList(start, start.plusDays(1), start.plusDays(2),
                start.plusDays(3), end);
        when(features.latestBusinessDate()).thenReturn(end);
        when(features.businessDates(start, end)).thenReturn(dates);
        when(sectors.calculateHistoricalResult(any(LocalDate.class)))
                .thenReturn(sectorResult(Collections.emptyList()));
        when(breadthService.calculate(any(LocalDate.class))).thenAnswer(invocation ->
                breadth(invocation.getArgument(0)));
        when(sectors.dispersion(anyList())).thenReturn(0D);
        when(features.calculate(any(LocalDate.class), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> regime(invocation.getArgument(0)));
        when(radarRepository.findEventsBetween(any(), any(), anyInt())).thenReturn(Collections.emptyList());
        when(confirmations.confirm(anyList(), anyList())).thenReturn(Collections.emptyList());
        when(discoveryRepository.findLatestSuccessOnOrBefore(any(LocalDate.class))).thenReturn(Optional.empty());

        MarketPulseBackfillResult result = service.backfill(start, end);

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(5, result.getResults().size());
        assertEquals(start, result.getResults().get(0).getBusinessDate());
        assertEquals(end, result.getResults().get(4).getBusinessDate());
        verify(repository, org.mockito.Mockito.times(5)).saveWorkspace(any(MarketPulseWorkspace.class));
        assertThrows(IllegalArgumentException.class, () -> service.refresh(start));
    }

    @Test
    void rejectsBackfillRangesThatCanReachFutureData() {
        LocalDate latest = LocalDate.of(2026, 8, 21);
        when(features.latestBusinessDate()).thenReturn(latest);

        assertThrows(IllegalArgumentException.class, () -> service.backfill(
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 24)));
        assertThrows(IllegalArgumentException.class, () -> service.backfill(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 21)));
    }

    @Test
    void refreshRepairsRecentTradingDayBreadthButSkipsInvalidCalendarSnapshots() {
        LocalDate latest = LocalDate.of(2026, 8, 28);
        LocalDate historical = LocalDate.of(2026, 8, 26);
        LocalDate weekend = LocalDate.of(2026, 8, 23);
        when(features.latestBusinessDate()).thenReturn(latest);
        when(features.businessDates(latest.minusDays(60), latest))
                .thenReturn(Arrays.asList(historical, latest));
        when(sectors.calculateResult(latest)).thenReturn(sectorResult(Collections.emptyList()));
        when(sectors.dispersion(Collections.emptyList())).thenReturn(0D);
        when(features.calculate(latest, 0D, 0.6D)).thenReturn(regime(latest));
        when(radarRepository.findEventsBetween(any(), any(), anyInt())).thenReturn(Collections.emptyList());
        when(confirmations.confirm(anyList(), anyList())).thenReturn(Collections.emptyList());
        when(discoveryRepository.findLatestSuccessOnOrBefore(latest)).thenReturn(Optional.empty());
        when(breadthService.calculate(latest)).thenReturn(breadth(latest));

        MarketPulseWorkspace missing = workspace(historical, unavailableBreadth(historical));
        missing.getWarnings().add("全A市场宽度不可用：采集请求超时");
        MarketPulseWorkspace invalid = workspace(weekend, unavailableBreadth(weekend));
        when(repository.findRecentWorkspaces(20, latest)).thenReturn(Arrays.asList(missing, invalid));
        MarketBreadthSnapshot recovered = breadth(historical);
        recovered.setAdvanceCount(2946);
        recovered.setValidCount(5546);
        recovered.setTotalAmount(1_821_418_342_968D);
        recovered.setMedianChangePct(0.22D);
        when(breadthService.calculate(historical)).thenReturn(recovered);

        service.refresh();

        ArgumentCaptor<MarketPulseWorkspace> captor = ArgumentCaptor.forClass(MarketPulseWorkspace.class);
        verify(repository, org.mockito.Mockito.times(2)).saveWorkspace(captor.capture());
        MarketPulseWorkspace repaired = captor.getAllValues().get(1);
        assertEquals(historical, repaired.getBusinessDate());
        assertEquals(2946, repaired.getBreadth().getAdvanceCount());
        assertTrue(repaired.getWarnings().stream().noneMatch(value -> value.startsWith("全A市场宽度不可用")));
        verify(breadthService, org.mockito.Mockito.never()).calculate(weekend);
    }

    @Test
    void historyAndDatePickerExcludeSnapshotsThatAreNotTradingDays() {
        LocalDate latest = LocalDate.of(2026, 8, 28);
        LocalDate friday = LocalDate.of(2026, 8, 21);
        LocalDate weekend = LocalDate.of(2026, 8, 23);
        MarketPulseWorkspace current = workspace(latest, breadth(latest));
        MarketPulseWorkspace invalid = workspace(weekend, unavailableBreadth(weekend));
        MarketPulseWorkspace previous = workspace(friday, breadth(friday));
        when(features.latestBusinessDate()).thenReturn(latest);
        when(features.businessDates(latest.minusDays(60), latest))
                .thenReturn(Arrays.asList(friday, latest));
        when(repository.findLatestWorkspace(latest)).thenReturn(Optional.of(current));
        when(repository.findRecentWorkspaces(20, latest)).thenReturn(Arrays.asList(current, invalid, previous));
        when(repository.findRecentDates(100, latest)).thenReturn(Arrays.asList(latest, weekend, friday));

        MarketPulseWorkspace result = service.latest();

        assertEquals(Arrays.asList(latest, friday), service.dates(20));
        assertEquals(2, result.getHistoryPoints().size());
        assertEquals(latest, result.getHistoryPoints().get(0).getBusinessDate());
        assertEquals(friday, result.getHistoryPoints().get(1).getBusinessDate());
    }

    private MarketBreadthSnapshot breadth(LocalDate date) {
        MarketBreadthSnapshot value = new MarketBreadthSnapshot();
        value.setBusinessDate(date);
        value.setQualityStatus("FRESH_PRIMARY");
        value.setAdvanceRatio(0.6D);
        return value;
    }

    private void prepareEmptyRefresh(LocalDate date) {
        when(features.latestBusinessDate()).thenReturn(date);
        when(sectors.calculateResult(date)).thenReturn(sectorResult(Collections.emptyList()));
        when(breadthService.calculate(date)).thenReturn(breadth(date));
        when(sectors.dispersion(Collections.emptyList())).thenReturn(0D);
        when(features.calculate(date, 0D, 0.6D)).thenReturn(regime(date));
        when(radarRepository.findEventsBetween(any(), any(), anyInt())).thenReturn(Collections.emptyList());
        when(confirmations.confirm(Collections.emptyList(), Collections.emptyList()))
                .thenReturn(Collections.emptyList());
        when(discoveryRepository.findLatestSuccessOnOrBefore(date)).thenReturn(Optional.empty());
        when(repository.findRecentWorkspaces(20, date)).thenReturn(Collections.emptyList());
    }

    private MarketBreadthSnapshot unavailableBreadth(LocalDate date) {
        MarketBreadthSnapshot value = new MarketBreadthSnapshot();
        value.setBusinessDate(date);
        value.setQualityStatus("UNAVAILABLE");
        value.setSourceCode("UNAVAILABLE");
        return value;
    }

    private MarketPulseWorkspace workspace(LocalDate date, MarketBreadthSnapshot breadth) {
        MarketPulseWorkspace value = new MarketPulseWorkspace();
        value.setBusinessDate(date);
        value.setBreadth(breadth);
        value.setRegime(regime(date));
        value.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        return value;
    }

    private MarketPulseSectorResult sectorResult(List<SectorRotationItem> values) {
        MarketPulseSectorResult result = new MarketPulseSectorResult();
        result.setSectors(values);
        result.setQualityStatus(MarketPulseQualityStatus.READY);
        return result;
    }

    private MarketRegimeSnapshot regime(LocalDate date) {
        MarketRegimeSnapshot value = new MarketRegimeSnapshot();
        value.setBusinessDate(date);
        value.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        return value;
    }

    private MarketIndexPerformance index() {
        MarketIndexPerformance value = new MarketIndexPerformance();
        value.setReturn1d(0D);
        return value;
    }
}
