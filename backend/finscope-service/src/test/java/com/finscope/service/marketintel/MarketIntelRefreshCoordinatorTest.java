package com.finscope.service.marketintel;

import com.finscope.dao.marketintel.CapitalBehaviorEvaluationRepository;
import com.finscope.dao.marketintel.CapitalBehaviorSnapshotRepository;
import com.finscope.dao.marketintel.CapitalFlowRepository;
import com.finscope.dao.marketintel.CapitalInterpretationRepository;
import com.finscope.dao.marketintel.DragonTigerRepository;
import com.finscope.dao.marketintel.MarketIntelRefreshRunRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketintel.CapitalBehaviorEvaluation;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import com.finscope.domain.marketintel.DragonTigerRecord;
import com.finscope.domain.marketintel.MarketIntelRefreshRun;
import com.finscope.domain.marketintel.MarketIntelRefreshStep;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.rpc.marketintel.DragonTigerData;
import com.finscope.service.marketdata.CapitalFlowGatewayResult;
import com.finscope.service.marketdata.DragonTigerGatewayResult;
import com.finscope.service.marketdata.MarketDataGateway;
import com.finscope.service.marketdata.QuoteGatewayResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketIntelRefreshCoordinatorTest {
    private final MarketIntelCapitalService capital = mock(MarketIntelCapitalService.class);
    private final MarketDataGateway gateway = mock(MarketDataGateway.class);
    private final CapitalFlowRepository flows = mock(CapitalFlowRepository.class);
    private final CapitalBehaviorSignalService signalService = mock(CapitalBehaviorSignalService.class);
    private final CapitalBehaviorSnapshotFactory snapshotFactory = mock(CapitalBehaviorSnapshotFactory.class);
    private final CapitalBehaviorSnapshotRepository snapshots = mock(CapitalBehaviorSnapshotRepository.class);
    private final CapitalFactorEvaluationService evaluationService = mock(CapitalFactorEvaluationService.class);
    private final CapitalBehaviorEvaluationRepository evaluations = mock(CapitalBehaviorEvaluationRepository.class);
    private final DragonTigerRepository dragonTiger = mock(DragonTigerRepository.class);
    private final CapitalRuleExplanationService ruleService = mock(CapitalRuleExplanationService.class);
    private final CapitalInterpretationRepository interpretations = mock(CapitalInterpretationRepository.class);
    private final CapitalFactAssembler facts = mock(CapitalFactAssembler.class);
    private final MarketIntelRefreshRunRepository runs = mock(MarketIntelRefreshRunRepository.class);
    private final MarketIntelRefreshCoordinator coordinator = new MarketIntelRefreshCoordinator(
            capital, gateway, flows, signalService, snapshotFactory, snapshots,
            ruleService, interpretations, facts, runs, evaluationService, evaluations, dragonTiger);
    private Instrument instrument;
    private MarketIntelRefreshRun run;

    @BeforeEach
    void setUp() {
        instrument = new Instrument();
        instrument.setId(7L);
        instrument.setType("STOCK");
        instrument.setMarket("SH");
        instrument.setCode("600519");
        run = new MarketIntelRefreshRun();
        run.setId(11L);
        run.setInstrumentId(7L);
        MarketIntelRefreshStep step = new MarketIntelRefreshStep();
        step.setId(19L);
        when(runs.createStep(eq(11L), eq("CAPITAL_FLOW"), any(String.class), eq(1))).thenReturn(step);
        MarketIntelRefreshStep dragonTigerStep = new MarketIntelRefreshStep();
        dragonTigerStep.setId(20L);
        when(runs.createStep(eq(11L), eq("DRAGON_TIGER"), any(String.class), eq(1)))
                .thenReturn(dragonTigerStep);
        when(gateway.fetchDragonTiger(eq(instrument), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(freshDragonTiger(Collections.<DragonTigerRecord>emptyList(), "dt-empty"));
        when(evaluationService.evaluate(any(CapitalBehaviorSnapshot.class)))
                .thenReturn(new CapitalBehaviorEvaluation());
    }

    @Test
    void allLiveProvidersFailButExistingSnapshotMakesRefreshPartialNotFailed() {
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class))).thenReturn(
                CapitalFlowGatewayResult.unavailable("EASTMONEY_CAPITAL_FLOW",
                        "资金源均不可用，保留上一份资金快照", "r-9"));
        when(snapshots.findLatest(7L)).thenReturn(Optional.of(new CapitalBehaviorSnapshot()));

        coordinator.refresh(run, instrument);

        verify(runs).finishRun(11L, MarketIntelRefreshRun.Status.PARTIAL, 1, 0);
        verify(runs).updateStep(19L, MarketIntelRefreshStep.Status.SKIPPED, 0,
                "STALE_FALLBACK", "资金源均不可用，保留上一份资金快照");
        verify(flows, never()).saveAll(anyList());
    }

    @Test
    void coordinatorDelegatesProviderSelectionToGateway() {
        CapitalFlowData empty = new CapitalFlowData(Collections.emptyList(), Collections.emptyList(),
                BigDecimal.ZERO, BigDecimal.ZERO, Collections.emptyList(), "EASTMONEY_CAPITAL_FLOW");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary(
                        "EASTMONEY_CAPITAL_FLOW", empty, null, "r-10"));

        coordinator.refresh(run, instrument);

        verify(gateway).fetchCapitalFlow(eq(instrument), any(LocalDate.class));
        verify(runs).updateStep(19L, MarketIntelRefreshStep.Status.EMPTY, 0, null, null);
        verify(runs, never()).finishRun(anyLong(), eq(MarketIntelRefreshRun.Status.FAILED), anyInt(), anyInt());
    }

    @Test
    void reusesPersistedDailyFactsWhenFreshHistoricalFlowIsUnavailable() {
        CapitalFlowPoint minute = point(301L, "MINUTE_1", LocalDateTime.of(2026, 7, 15, 10, 0));
        CapitalFlowPoint staleDaily = point(201L, "DAY_1", LocalDateTime.of(2026, 7, 14, 15, 0));
        CapitalFlowData partial = new CapitalFlowData(Collections.singletonList(minute), Collections.emptyList(),
                null, null, Arrays.asList("HISTORICAL_FUND_FLOW_UNAVAILABLE:CONNECTION_ERROR",
                "DAILY_MARKET_UNAVAILABLE:CONNECTION_ERROR"), "EASTMONEY_CAPITAL_FLOW");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary("EASTMONEY_CAPITAL_FLOW", partial, null, "r-11"));
        when(flows.findLatestByGranularity(7L, "DAY_1", 320))
                .thenReturn(Collections.singletonList(staleDaily));
        CapitalBehaviorSnapshot saved = CapitalBehaviorSnapshot.of(7L, minute.getObservedAt(),
                Arrays.asList(staleDaily, minute), Collections.emptyList(), "merged");
        saved.setId(31L);
        when(snapshotFactory.create(eq(7L), anyList(), anyList(), anyList())).thenReturn(saved);
        when(snapshots.save(saved)).thenReturn(saved);
        when(ruleService.explain(anyList(), anyList())).thenReturn(new CapitalRuleExplanation());

        coordinator.refresh(run, instrument);

        verify(flows).saveAll(org.mockito.ArgumentMatchers.argThat(values -> values.stream()
                .anyMatch(value -> "DAY_1".equals(value.getGranularity()))));
        verify(runs).updateStep(eq(19L), eq(MarketIntelRefreshStep.Status.SUCCEEDED), eq(2),
                eq("PARTIAL_DATA"), org.mockito.ArgumentMatchers.contains("历史资金流刷新失败，已使用最近成功数据"));
    }

    @Test
    void mergesShortFreshHistoryWithThePersistedReservoirWithoutDuplicatingTradingDays() {
        List<CapitalFlowPoint> stored = new ArrayList<CapitalFlowPoint>();
        LocalDate start = LocalDate.of(2025, 8, 1);
        for (int index = 0; index < 270; index++) {
            CapitalFlowPoint point = point(1000L + index, "DAY_1", start.plusDays(index).atTime(15, 0));
            point.setRetrievedAt(start.plusDays(index).atTime(16, 0));
            stored.add(point);
        }
        Collections.reverse(stored); // 仓储契约按 observed_at DESC 返回最近记录。
        List<CapitalFlowPoint> fresh = new ArrayList<CapitalFlowPoint>();
        for (int index = 260; index < 270; index++) {
            CapitalFlowPoint point = point(2000L + index, "DAY_1", start.plusDays(index).atTime(15, 0));
            point.setRetrievedAt(LocalDateTime.of(2026, 7, 15, 16, 0));
            point.setMainNetInflow(new BigDecimal("99"));
            if (index == 265) point.setQualityStatus("PARTIAL");
            fresh.add(point);
        }
        CapitalFlowData shortFresh = new CapitalFlowData(Collections.emptyList(), fresh,
                null, null, Collections.emptyList(), "EASTMONEY_CAPITAL_FLOW");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary(
                        "EASTMONEY_CAPITAL_FLOW", shortFresh, null, "r-history"));
        when(flows.findLatestByGranularity(7L, "DAY_1", 320)).thenReturn(stored);
        CapitalBehaviorSnapshot saved = CapitalBehaviorSnapshot.of(7L,
                fresh.get(fresh.size() - 1).getObservedAt(), fresh, Collections.emptyList(), "history-merged");
        saved.setId(35L);
        when(snapshotFactory.create(eq(7L), anyList(), anyList(), anyList())).thenReturn(saved);
        when(snapshots.save(saved)).thenReturn(saved);
        when(ruleService.explain(anyList(), anyList())).thenReturn(new CapitalRuleExplanation());

        coordinator.refresh(run, instrument);

        verify(flows).saveAll(argThat(values -> {
            List<CapitalFlowPoint> daily = values.stream()
                    .filter(value -> "DAY_1".equals(value.getGranularity()))
                    .collect(java.util.stream.Collectors.toList());
            long uniqueDates = daily.stream().map(CapitalFlowPoint::getDataDate).distinct().count();
            CapitalFlowPoint partialOverlap = daily.stream()
                    .filter(value -> start.plusDays(265).equals(value.getDataDate()))
                    .findFirst().orElse(null);
            CapitalFlowPoint completeOverlap = daily.stream()
                    .filter(value -> start.plusDays(266).equals(value.getDataDate()))
                    .findFirst().orElse(null);
            return uniqueDates == 250
                    && start.plusDays(20).equals(daily.get(0).getDataDate())
                    && partialOverlap != null && new BigDecimal("10").equals(partialOverlap.getMainNetInflow())
                    && completeOverlap != null && new BigDecimal("99").equals(completeOverlap.getMainNetInflow());
        }));
        assertTrue(fresh.size() < 60, "test must exercise a short online response");
    }

    @Test
    void fillsCurrentQuoteFromTheExistingQuoteGatewayAndClearsRawProviderWarning() {
        CapitalFlowPoint minute = point(301L, "MINUTE_1", LocalDateTime.of(2026, 7, 15, 10, 0));
        minute.setPrice(null);
        minute.setTradeVolume(null);
        minute.setCumulativeTradeAmount(null);
        CapitalFlowData partial = new CapitalFlowData(Collections.singletonList(minute), Collections.emptyList(),
                null, null, Collections.singletonList("QUOTE_UNAVAILABLE:CONNECTION_ERROR"),
                "EASTMONEY_CAPITAL_FLOW");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary("EASTMONEY_CAPITAL_FLOW", partial, null, "r-12"));
        Quote quote = new Quote();
        quote.setInstrumentCode("600519"); quote.setValid(true); quote.setPrice(12.34);
        quote.setVolume(9876.0); quote.setTurnover(1234567.0);
        quote.setAsOf(LocalDateTime.of(2026, 7, 15, 10, 0)); quote.setSourceCode("TENCENT_STOCK");
        when(gateway.fetchQuotes("STOCK", Collections.singletonList("600519"), true))
                .thenReturn(new QuoteGatewayResult(Collections.singletonList(quote),
                        com.finscope.domain.marketdata.MarketDataQualityStatus.FRESH_FALLBACK,
                        "TENCENT_STOCK", quote.getAsOf(), quote.getAsOf(), null, null, "q-1"));
        when(flows.findLatestByGranularity(7L, "DAY_1", 320)).thenReturn(Collections.emptyList());
        CapitalBehaviorSnapshot saved = CapitalBehaviorSnapshot.of(7L, minute.getObservedAt(),
                Collections.singletonList(minute), Collections.emptyList(), "quote-merged");
        saved.setId(32L);
        when(snapshotFactory.create(eq(7L), anyList(), anyList(), anyList())).thenReturn(saved);
        when(snapshots.save(saved)).thenReturn(saved);
        when(ruleService.explain(anyList(), anyList())).thenReturn(new CapitalRuleExplanation());

        coordinator.refresh(run, instrument);

        assertEquals(new BigDecimal("12.34"), minute.getPrice());
        assertEquals(new BigDecimal("9876.0"), minute.getTradeVolume());
        assertEquals(new BigDecimal("1234567.0"), minute.getCumulativeTradeAmount());
        verify(runs).updateStep(eq(19L), eq(MarketIntelRefreshStep.Status.SUCCEEDED), eq(1),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void keepsTheProviderWarningWhenTheQuoteGatewayOnlyHasStaleData() {
        CapitalFlowPoint minute = point(302L, "MINUTE_1", LocalDateTime.of(2026, 7, 15, 10, 0));
        minute.setPrice(null);
        CapitalFlowData partial = new CapitalFlowData(Collections.singletonList(minute), Collections.emptyList(),
                null, null, Collections.singletonList("QUOTE_UNAVAILABLE:CONNECTION_ERROR"),
                "EASTMONEY_CAPITAL_FLOW");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary("EASTMONEY_CAPITAL_FLOW", partial, null, "r-13"));
        Quote quote = new Quote();
        quote.setInstrumentCode("600519"); quote.setValid(true); quote.setPrice(12.34);
        quote.setAsOf(LocalDateTime.of(2026, 7, 15, 10, 0));
        when(gateway.fetchQuotes("STOCK", Collections.singletonList("600519"), true))
                .thenReturn(new QuoteGatewayResult(Collections.singletonList(quote),
                        com.finscope.domain.marketdata.MarketDataQualityStatus.STALE_FALLBACK,
                        "TENCENT_STOCK", quote.getAsOf(), quote.getAsOf(), 86400L,
                        "报价源不可用，正在显示旧报价", "q-2"));
        when(flows.findLatestByGranularity(7L, "DAY_1", 320)).thenReturn(Collections.emptyList());
        CapitalBehaviorSnapshot saved = CapitalBehaviorSnapshot.of(7L, minute.getObservedAt(),
                Collections.singletonList(minute), Collections.emptyList(), "stale-quote-not-used");
        saved.setId(33L);
        when(snapshotFactory.create(eq(7L), anyList(), anyList(), anyList())).thenReturn(saved);
        when(snapshots.save(saved)).thenReturn(saved);
        when(ruleService.explain(anyList(), anyList())).thenReturn(new CapitalRuleExplanation());

        coordinator.refresh(run, instrument);

        assertEquals(null, minute.getPrice());
        verify(runs).updateStep(eq(19L), eq(MarketIntelRefreshStep.Status.SUCCEEDED), eq(1),
                eq("PARTIAL_DATA"), org.mockito.ArgumentMatchers.contains("QUOTE_UNAVAILABLE"));
    }

    @Test
    void persistsHistoricalEvaluationForTheSavedSnapshot() {
        CapitalFlowPoint daily = point(401L, "DAY_1", LocalDateTime.of(2026, 7, 15, 15, 0));
        CapitalFlowData fresh = new CapitalFlowData(Collections.emptyList(), Collections.singletonList(daily),
                null, null, Collections.emptyList(), "EASTMONEY_CAPITAL_FLOW");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary("EASTMONEY_CAPITAL_FLOW", fresh, null, "r-14"));
        CapitalBehaviorSnapshot saved = CapitalBehaviorSnapshot.of(7L, daily.getObservedAt(),
                Collections.singletonList(daily), Collections.emptyList(), "evaluation-input");
        saved.setId(41L);
        when(snapshotFactory.create(eq(7L), anyList(), anyList(), anyList())).thenReturn(saved);
        when(snapshots.save(saved)).thenReturn(saved);
        when(ruleService.explain(anyList(), anyList())).thenReturn(new CapitalRuleExplanation());
        CapitalBehaviorEvaluation evaluation = new CapitalBehaviorEvaluation();
        evaluation.setSnapshotId(41L);
        when(evaluationService.evaluate(saved)).thenReturn(evaluation);

        coordinator.refresh(run, instrument);

        verify(evaluations).save(evaluation);
        verify(runs).finishRun(11L, MarketIntelRefreshRun.Status.SUCCEEDED, 2, 0);
    }

    @Test
    void evaluationFailureKeepsCapitalRefreshUsableAndMakesTheWarningExplicit() {
        CapitalFlowPoint daily = point(402L, "DAY_1", LocalDateTime.of(2026, 7, 15, 15, 0));
        CapitalFlowData fresh = new CapitalFlowData(Collections.emptyList(), Collections.singletonList(daily),
                null, null, Collections.emptyList(), "EASTMONEY_CAPITAL_FLOW");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary("EASTMONEY_CAPITAL_FLOW", fresh, null, "r-15"));
        CapitalBehaviorSnapshot saved = CapitalBehaviorSnapshot.of(7L, daily.getObservedAt(),
                Collections.singletonList(daily), Collections.emptyList(), "evaluation-failed");
        saved.setId(42L);
        when(snapshotFactory.create(eq(7L), anyList(), anyList(), anyList())).thenReturn(saved);
        when(snapshots.save(saved)).thenReturn(saved);
        when(ruleService.explain(anyList(), anyList())).thenReturn(new CapitalRuleExplanation());
        when(evaluationService.evaluate(saved)).thenThrow(new IllegalStateException("broken label"));

        coordinator.refresh(run, instrument);

        verify(runs).updateStep(eq(19L), eq(MarketIntelRefreshStep.Status.SUCCEEDED), eq(1),
                eq("PARTIAL_DATA"), org.mockito.ArgumentMatchers.contains("历史评价暂不可用"));
        verify(runs).finishRun(11L, MarketIntelRefreshRun.Status.PARTIAL, 2, 0);
        verify(evaluations, never()).save(any(CapitalBehaviorEvaluation.class));
        verify(snapshots).updateWarnings(eq(42L), eq("PARTIAL"),
                org.mockito.ArgumentMatchers.argThat(values -> values.contains(
                        "历史评价暂不可用，本次资金快照仍已更新")));
    }

    @Test
    void clearsPersistedEvaluationWarningWhenTheSameSnapshotRecovers() {
        CapitalFlowPoint daily = point(403L, "DAY_1", LocalDateTime.of(2026, 7, 15, 15, 0));
        CapitalFlowData fresh = new CapitalFlowData(Collections.emptyList(), Collections.singletonList(daily),
                null, null, Collections.emptyList(), "EASTMONEY_CAPITAL_FLOW");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary("EASTMONEY_CAPITAL_FLOW", fresh, null, "r-16"));
        CapitalBehaviorSnapshot saved = CapitalBehaviorSnapshot.of(7L, daily.getObservedAt(),
                Collections.singletonList(daily), Collections.emptyList(), "evaluation-recovered");
        saved.setId(43L);
        when(snapshotFactory.create(eq(7L), anyList(), anyList(), anyList())).thenReturn(saved);
        when(snapshots.save(saved)).thenReturn(saved);
        when(ruleService.explain(anyList(), anyList())).thenReturn(new CapitalRuleExplanation());
        CapitalBehaviorEvaluation evaluation = new CapitalBehaviorEvaluation();
        evaluation.setSnapshotId(43L);
        when(evaluationService.evaluate(saved))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn(evaluation);

        coordinator.refresh(run, instrument);
        coordinator.refresh(run, instrument);

        verify(evaluations).save(evaluation);
        verify(snapshots).updateWarnings(eq(43L), eq("COMPLETE"), eq(Collections.emptyList()));
    }

    @Test
    void createsIndependentCapitalAndDragonTigerStepsAndFinishesOnce() {
        CapitalFlowPoint minute = point(501L, "MINUTE_1",
                LocalDateTime.of(2026, 7, 16, 10, 0));
        CapitalFlowData freshCapital = new CapitalFlowData(
                Collections.singletonList(minute), Collections.emptyList(),
                null, null, Collections.emptyList(), "TEST_CAPITAL");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary(
                        "TEST_CAPITAL", freshCapital, null, "capital-fresh"));
        DragonTigerRecord record = dragonTigerRecord();
        when(gateway.fetchDragonTiger(eq(instrument), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(freshDragonTiger(Collections.singletonList(record), "dt-fresh"));
        CapitalBehaviorSnapshot saved = CapitalBehaviorSnapshot.of(7L, minute.getObservedAt(),
                Collections.singletonList(minute), Collections.emptyList(), "two-dimensions");
        saved.setId(51L);
        when(snapshotFactory.create(eq(7L), anyList(), anyList(), anyList())).thenReturn(saved);
        when(snapshots.save(saved)).thenReturn(saved);
        when(ruleService.explain(anyList(), anyList())).thenReturn(new CapitalRuleExplanation());

        coordinator.refresh(run, instrument);

        verify(runs).createStep(11L, "CAPITAL_FLOW", "TEST_CAPITAL", 1);
        verify(runs).createStep(11L, "DRAGON_TIGER", "TEST_DRAGON_TIGER", 1);
        verify(dragonTiger).saveAll(Collections.singletonList(record));
        verify(runs).finishRun(11L, MarketIntelRefreshRun.Status.SUCCEEDED, 2, 0);
    }

    @Test
    void oneDimensionFailureMakesTheRunPartialWithoutDiscardingTheOther() {
        CapitalFlowPoint minute = point(502L, "MINUTE_1",
                LocalDateTime.of(2026, 7, 16, 10, 0));
        CapitalFlowData freshCapital = new CapitalFlowData(
                Collections.singletonList(minute), Collections.emptyList(),
                null, null, Collections.emptyList(), "TEST_CAPITAL");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary(
                        "TEST_CAPITAL", freshCapital, null, "capital-fresh"));
        when(gateway.fetchDragonTiger(eq(instrument), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(DragonTigerGatewayResult.unavailable(
                        "EASTMONEY_DRAGON_TIGER", "龙虎榜源不可用", "dt-failed"));
        CapitalBehaviorSnapshot saved = CapitalBehaviorSnapshot.of(7L, minute.getObservedAt(),
                Collections.singletonList(minute), Collections.emptyList(), "capital-survives");
        saved.setId(52L);
        when(snapshotFactory.create(eq(7L), anyList(), anyList(), anyList())).thenReturn(saved);
        when(snapshots.save(saved)).thenReturn(saved);
        when(ruleService.explain(anyList(), anyList())).thenReturn(new CapitalRuleExplanation());

        coordinator.refresh(run, instrument);

        verify(flows).saveAll(anyList());
        verify(runs).finishRun(11L, MarketIntelRefreshRun.Status.PARTIAL, 1, 1);
    }

    @Test
    void successfulDragonTigerEmptySetCountsAsSuccess() {
        CapitalFlowData emptyCapital = new CapitalFlowData(
                Collections.emptyList(), Collections.emptyList(),
                null, null, Collections.emptyList(), "TEST_CAPITAL");
        when(gateway.fetchCapitalFlow(eq(instrument), any(LocalDate.class)))
                .thenReturn(CapitalFlowGatewayResult.freshPrimary(
                        "TEST_CAPITAL", emptyCapital, null, "capital-empty"));

        coordinator.refresh(run, instrument);

        verify(runs).updateStep(20L, MarketIntelRefreshStep.Status.EMPTY,
                0, null, null);
        verify(runs).finishRun(11L, MarketIntelRefreshRun.Status.PARTIAL, 1, 0);
    }

    private DragonTigerGatewayResult freshDragonTiger(
            List<DragonTigerRecord> records, String refreshId) {
        return DragonTigerGatewayResult.freshPrimary(
                "TEST_DRAGON_TIGER",
                new DragonTigerData(records, Collections.emptyList()),
                LocalDateTime.of(2026, 7, 16, 16, 0), refreshId);
    }

    private DragonTigerRecord dragonTigerRecord() {
        DragonTigerRecord record = new DragonTigerRecord();
        record.setInstrumentId(7L);
        record.setProviderCode("TEST_DRAGON_TIGER");
        record.setTradeDate(LocalDate.of(2026, 7, 15));
        record.setExternalId("100373909");
        record.setReason("日跌幅偏离值达到7%的前5只证券");
        record.setRetrievedAt(LocalDateTime.of(2026, 7, 16, 16, 0));
        record.setPayloadHash("dragon-tiger");
        record.setQualityStatus("COMPLETE");
        return record;
    }

    private CapitalFlowPoint point(Long id, String granularity, LocalDateTime observedAt) {
        CapitalFlowPoint value = new CapitalFlowPoint();
        value.setId(id); value.setInstrumentId(7L); value.setProviderCode("EASTMONEY_CAPITAL_FLOW");
        value.setGranularity(granularity); value.setDataDate(observedAt.toLocalDate());
        value.setObservedAt(observedAt); value.setPrice(new BigDecimal("12"));
        value.setTradeVolume(new BigDecimal("100")); value.setIntervalTradeAmount(new BigDecimal("1000"));
        value.setMainNetInflow(new BigDecimal("10")); value.setQualityStatus("COMPLETE");
        value.setCalculationVersion("test"); value.setRetrievedAt(observedAt); value.setPayloadHash("hash-" + id);
        return value;
    }
}
