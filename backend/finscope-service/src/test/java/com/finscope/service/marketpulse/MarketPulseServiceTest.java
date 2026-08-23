package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.dao.quant.StockDiscoveryRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.marketpulse.MarketEventConfirmation;
import com.finscope.domain.marketpulse.DailyMarketReview;
import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketPulseCandidate;
import com.finscope.domain.marketpulse.MarketPulseRefreshResult;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.MarketRegimeSnapshot;
import com.finscope.domain.marketpulse.SectorRotationItem;
import com.finscope.domain.quant.discovery.StockDiscoveryCandidate;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
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
        when(sectors.calculate(latestTradingDate)).thenReturn(Collections.emptyList());
        when(breadthService.calculate(latestTradingDate)).thenReturn(breadth(latestTradingDate));
        when(sectors.dispersion(Collections.emptyList())).thenReturn(0D);
        when(features.calculate(latestTradingDate, 0D, 0.6D)).thenReturn(regime);
        when(radarRepository.findEventsSince(any(), anyInt())).thenReturn(Collections.emptyList());
        when(confirmations.confirm(Collections.emptyList(), Collections.emptyList()))
                .thenReturn(Collections.emptyList());
        when(discoveryRepository.findLatestSuccess()).thenReturn(Optional.empty());

        MarketPulseRefreshResult result = service.refresh();

        assertEquals(latestTradingDate, result.getBusinessDate());
        verify(sectors).calculate(latestTradingDate);
    }

    @Test
    void refreshesOneFrozenWorkspaceAndReusesVerifiedDiscoveryCandidates() {
        LocalDate date = LocalDate.of(2026, 8, 21);
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
        when(sectors.calculate(date)).thenReturn(Collections.singletonList(sector));
        when(breadthService.calculate(date)).thenReturn(breadth(date));
        when(sectors.dispersion(Collections.singletonList(sector))).thenReturn(0.02D);
        when(features.calculate(date, 0.02D, 0.6D)).thenReturn(regime);
        when(radarRepository.findEventsSince(any(), anyInt())).thenReturn(Collections.singletonList(event));
        when(confirmations.confirm(Collections.singletonList(event), Collections.singletonList(sector)))
                .thenReturn(Collections.singletonList(confirmation));
        when(discoveryRepository.findLatestSuccess()).thenReturn(Optional.of(run));
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
        MarketRegimeSnapshot regime = new MarketRegimeSnapshot();
        regime.setBusinessDate(date);
        regime.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        when(sectors.calculate(date)).thenReturn(Collections.emptyList());
        when(breadthService.calculate(date)).thenReturn(breadth(date));
        when(sectors.dispersion(Collections.emptyList())).thenReturn(0D);
        when(features.calculate(date, 0D, 0.6D)).thenReturn(regime);
        when(radarRepository.findEventsSince(any(), anyInt())).thenReturn(Collections.emptyList());
        when(confirmations.confirm(Collections.emptyList(), Collections.emptyList()))
                .thenReturn(Collections.emptyList());
        when(discoveryRepository.findLatestSuccess()).thenReturn(Optional.empty());

        MarketPulseRefreshResult result = service.refresh(date);

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(0, result.getCandidateCount());
        verify(repository).saveWorkspace(any(MarketPulseWorkspace.class));
    }

    private MarketBreadthSnapshot breadth(LocalDate date) {
        MarketBreadthSnapshot value = new MarketBreadthSnapshot();
        value.setBusinessDate(date);
        value.setQualityStatus("FRESH_PRIMARY");
        value.setAdvanceRatio(0.6D);
        return value;
    }
}
