package com.finscope.service.marketintel;

import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.marketintel.CapitalBehaviorEvaluationRepository;
import com.finscope.dao.marketintel.CapitalBehaviorSnapshotRepository;
import com.finscope.domain.marketintel.CapitalBehaviorEvaluation;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.service.marketintel.factor.CapitalFactorEngine;
import com.finscope.service.marketintel.factor.CapitalFactorRegistry;
import com.finscope.service.quant.factor.TimeSeriesFactorOperators;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketIntelCapitalServiceTest {
    @Test
    void appliesRequestedTradingDayWindowToDailyTrend() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        CapitalBehaviorSnapshotRepository snapshots = mock(CapitalBehaviorSnapshotRepository.class);
        CapitalBehaviorEvaluationRepository evaluations = mock(CapitalBehaviorEvaluationRepository.class);
        Instrument stock = new Instrument(); stock.setId(7L); stock.setType("STOCK"); stock.setMarket("SH"); stock.setCode("600519");
        when(instruments.findById(7L)).thenReturn(Optional.of(stock));

        List<CapitalFlowPoint> facts = new ArrayList<CapitalFlowPoint>();
        LocalDate first = LocalDate.of(2026, 6, 1);
        for (int index = 0; index < 25; index++) {
            CapitalFlowPoint point = new CapitalFlowPoint();
            point.setGranularity("DAY_1"); point.setDataDate(first.plusDays(index));
            point.setObservedAt(first.plusDays(index).atTime(15, 0)); point.setQualityStatus("COMPLETE");
            facts.add(point);
        }
        CapitalBehaviorSnapshot snapshot = CapitalBehaviorSnapshot.of(7L, first.plusDays(24).atTime(15, 0), facts,
                Collections.emptyList(), "fingerprint");
        when(snapshots.findLatest(7L)).thenReturn(Optional.of(snapshot));
        CapitalBehaviorEvaluation evaluation = new CapitalBehaviorEvaluation();
        evaluation.setSnapshotId(snapshot.getId());
        evaluation.setStatus("INSUFFICIENT_DATA");
        when(evaluations.findBySnapshotId(snapshot.getId())).thenReturn(Optional.of(evaluation));
        CapitalFactorEngine factors = new CapitalFactorEngine(new CapitalFactorRegistry(), new TimeSeriesFactorOperators());
        MarketIntelCapitalService service = new MarketIntelCapitalService(instruments, snapshots, evaluations,
                new CapitalFlowAggregationService(), new CapitalRuleExplanationService(), new CapitalBehaviorMetricsService(),
                factors, new CapitalBehaviorSignalService(CapitalSignalPolicy.v2(), factors));

        MarketIntelCapitalView view = service.view(7L, "20d", "5m");

        assertEquals(20, view.getDailyTrend().size());
        assertEquals(first.plusDays(5), view.getDailyTrend().get(0).getDataDate());
        assertEquals(first.plusDays(24), view.getDailyTrend().get(19).getDataDate());
        assertEquals("capital-factor-v1", view.getFactorVersion());
        assertEquals("capital-signal-v2", view.getSignalVersion());
        assertEquals(evaluation, view.getHistoricalEvaluation());
    }
}
