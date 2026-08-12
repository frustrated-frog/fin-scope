package com.finscope.service.quant.forecast;

import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.dao.quant.ForecastCandidateRunRepository;
import com.finscope.dao.strategy.StrategyHoldingRepository;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import com.finscope.domain.quant.forecast.ForecastModelHealth;
import com.finscope.domain.strategy.StrategyHolding;
import com.finscope.rpc.quant.PythonSingleStockForecastClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SingleStockForecastServiceTest {
    @Test
    void savesEveryPythonReportWithAnIsolatedHoldingSnapshot() {
        PythonSingleStockForecastClient client = mock(PythonSingleStockForecastClient.class);
        SingleStockForecastRunRepository runs = mock(SingleStockForecastRunRepository.class);
        StrategyHoldingRepository holdings = mock(StrategyHoldingRepository.class);
        SingleStockForecast forecast = forecast();
        StrategyHolding holding = new StrategyHolding();
        holding.setCode("600519");
        holding.setName("贵州茅台");
        holding.setRole("LIVE_VALIDATION");
        holding.setQuantity(10d);
        holding.setAverageCost(1400d);
        when(client.forecast("600519", 5)).thenReturn(forecast);
        when(holdings.findStockByCode("600519")).thenReturn(Optional.of(holding));
        when(runs.save(any(SingleStockForecastRun.class))).thenAnswer(invocation -> {
            SingleStockForecastRun value = invocation.getArgument(0);
            value.setId(7L);
            return value;
        });
        SingleStockForecastService service = new SingleStockForecastService(client, runs, holdings);

        SingleStockForecastRun result = service.forecast("600519.SH", 5);

        assertEquals(7L, result.getId());
        assertSame(forecast, result.getReport());
        assertTrue(result.getHoldingSnapshot().isHeld());
        assertEquals(10d, result.getHoldingSnapshot().getQuantity());
        assertEquals(1505d / 1400d - 1d, result.getHoldingSnapshot().getUnrealizedReturn(), 0.000001);
        verify(client).forecast("600519", 5);
        verify(runs).save(any(SingleStockForecastRun.class));
    }

    @Test
    void loadsSavedDetailWithoutRecomputingTheForecast() {
        PythonSingleStockForecastClient client = mock(PythonSingleStockForecastClient.class);
        SingleStockForecastRunRepository runs = mock(SingleStockForecastRunRepository.class);
        StrategyHoldingRepository holdings = mock(StrategyHoldingRepository.class);
        SingleStockForecastRun stored = new SingleStockForecastRun();
        stored.setId(9L);
        stored.setReportJson("{\"instrumentCode\":\"600519.SH\",\"asOfDate\":\"2026-08-07\"}");
        stored.setHoldingSnapshotJson("{\"held\":false}");
        when(runs.findById(9L)).thenReturn(Optional.of(stored));
        SingleStockForecastService service = new SingleStockForecastService(client, runs, holdings);

        SingleStockForecastRun result = service.detail(9L);

        assertEquals("600519.SH", result.getReport().getInstrumentCode());
        verify(client, never()).forecast(any());
    }

    @Test
    void marksAnInsufficientForecastUnavailableForMaturityValidation() {
        PythonSingleStockForecastClient client = mock(PythonSingleStockForecastClient.class);
        SingleStockForecastRunRepository runs = mock(SingleStockForecastRunRepository.class);
        StrategyHoldingRepository holdings = mock(StrategyHoldingRepository.class);
        SingleStockForecast forecast = forecast();
        forecast.setStatus("INSUFFICIENT_DATA");
        forecast.setUpProbability(null);
        when(client.forecast("600519", 5)).thenReturn(forecast);
        when(holdings.findStockByCode("600519")).thenReturn(Optional.empty());
        when(runs.save(any(SingleStockForecastRun.class))).thenAnswer(
                invocation -> invocation.getArgument(0));
        SingleStockForecastService service = new SingleStockForecastService(client, runs, holdings);

        SingleStockForecastRun result = service.forecast("600519", 5);

        assertEquals(SingleStockForecastRun.MaturityStatus.UNAVAILABLE,
                result.getMaturityStatus());
    }

    @Test
    void pausesOnlyTheDirectionWhenRealOutcomeHealthGateIsClosed() {
        PythonSingleStockForecastClient client = mock(PythonSingleStockForecastClient.class);
        SingleStockForecastRunRepository runs = mock(SingleStockForecastRunRepository.class);
        StrategyHoldingRepository holdings = mock(StrategyHoldingRepository.class);
        ForecastOutcomeSettlementService settlement = mock(ForecastOutcomeSettlementService.class);
        ForecastModelHealthService healthService = mock(ForecastModelHealthService.class);
        SingleStockForecast forecast = forecast();
        forecast.setDecision("UP");
        ForecastModelHealth health = new ForecastModelHealth();
        health.setStatus("PAUSED");
        health.setDirectionOutputPaused(true);
        health.setConclusion("真实结果持续弱于门槛");
        when(client.forecast("600519", 5)).thenReturn(forecast);
        when(holdings.findStockByCode("600519")).thenReturn(Optional.empty());
        when(healthService.evaluate("600519.SH", 5, forecast.getModelVersion())).thenReturn(health);
        when(runs.save(any(SingleStockForecastRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SingleStockForecastService service = new SingleStockForecastService(
                client, runs, holdings, settlement, healthService);

        SingleStockForecastRun result = service.forecast("600519", 5);

        assertEquals("ABSTAIN", result.getReport().getDecision());
        assertEquals("UP", result.getReport().getModelDecision());
        assertEquals("真实结果持续弱于门槛", result.getReport().getDecisionReason());
        assertEquals(0.62d, result.getReport().getUpProbability(), 0.000001d);
        assertSame(health, result.getModelHealth());
    }

    @Test
    void forecastContinuesWhenBestEffortOutcomeSettlementTemporarilyFails() {
        PythonSingleStockForecastClient client = mock(PythonSingleStockForecastClient.class);
        SingleStockForecastRunRepository runs = mock(SingleStockForecastRunRepository.class);
        StrategyHoldingRepository holdings = mock(StrategyHoldingRepository.class);
        ForecastOutcomeSettlementService settlement = mock(ForecastOutcomeSettlementService.class);
        ForecastModelHealthService healthService = mock(ForecastModelHealthService.class);
        SingleStockForecast forecast = forecast();
        doThrow(new IllegalStateException("provider timeout"))
                .when(settlement).settlePending("600519.SH");
        when(client.forecast("600519", 5)).thenReturn(forecast);
        when(holdings.findStockByCode("600519")).thenReturn(Optional.empty());
        when(healthService.evaluate("600519.SH", 5, forecast.getModelVersion()))
                .thenReturn(new ForecastModelHealth());
        when(runs.save(any(SingleStockForecastRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SingleStockForecastService service = new SingleStockForecastService(
                client, runs, holdings, settlement, healthService);

        SingleStockForecastRun result = service.forecast("600519", 5);

        assertSame(forecast, result.getReport());
        verify(client).forecast("600519", 5);
    }

    @Test
    void savesEveryVersionSixCandidateAsFrozenShadowEvidence() {
        PythonSingleStockForecastClient client = mock(PythonSingleStockForecastClient.class);
        SingleStockForecastRunRepository runs = mock(SingleStockForecastRunRepository.class);
        ForecastCandidateRunRepository candidates = mock(ForecastCandidateRunRepository.class);
        StrategyHoldingRepository holdings = mock(StrategyHoldingRepository.class);
        SingleStockForecast forecast = forecast();
        forecast.setReportSchemaVersion("single-stock-research-v6");
        SingleStockForecast.ModelCompetition competition = new SingleStockForecast.ModelCompetition();
        competition.setSelectedModel("LOGISTIC");
        competition.getCandidates().add(candidate("LOGISTIC", "CHAMPION", .61d));
        competition.getCandidates().add(candidate("REGIME_LOGISTIC", "CHALLENGER", .66d));
        forecast.setModelCompetition(competition);
        when(client.forecast("600519", 5)).thenReturn(forecast);
        when(holdings.findStockByCode("600519")).thenReturn(Optional.empty());
        when(runs.save(any(SingleStockForecastRun.class))).thenAnswer(invocation -> {
            SingleStockForecastRun value = invocation.getArgument(0);
            value.setId(17L);
            return value;
        });
        SingleStockForecastService service = new SingleStockForecastService(
                client, runs, candidates, null, holdings, null, null, null);

        service.forecast("600519", 5);

        org.mockito.ArgumentCaptor<java.util.List> saved =
                org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(candidates).saveAll(eq(17L), saved.capture());
        assertEquals(2, saved.getValue().size());
    }

    private SingleStockForecast.ModelCandidate candidate(String code, String role, double probability) {
        SingleStockForecast.ModelCandidate value = new SingleStockForecast.ModelCandidate();
        value.setCode(code);
        value.setName(code);
        value.setSelected("CHAMPION".equals(role));
        value.setRole(role);
        value.setModelVersion("competition-" + code.toLowerCase() + "-platt-v6");
        value.setRawProbability(probability + .02d);
        value.setCalibratedProbability(probability);
        value.setShadowDecision(probability >= .6d ? "UP" : "ABSTAIN");
        value.setQualificationStatus("QUALIFIED");
        SingleStockForecast.ProbabilityMetricSet metrics = new SingleStockForecast.ProbabilityMetricSet();
        metrics.setSampleCount(15);
        metrics.setAccuracy(.60d);
        metrics.setBrierScore(.22d);
        metrics.setLogLoss(.64d);
        metrics.setBrierSkillScore(.08d);
        value.setLockedMetrics(metrics);
        return value;
    }

    private SingleStockForecast forecast() {
        SingleStockForecast value = new SingleStockForecast();
        value.setReportSchemaVersion("single-stock-research-v2");
        value.setModelVersion("logistic-walk-forward-v2");
        value.setInstrumentCode("600519.SH");
        value.setAsOfDate(LocalDate.of(2026, 8, 7));
        value.setHorizonDays(5);
        value.setStatus("NO_CLEAR_EDGE");
        value.setConclusion("没有明显优势");
        value.setBarCount(2000);
        value.setUpProbability(0.62);
        value.setDataFingerprint("fingerprint");
        value.setLastClose(1505d);
        return value;
    }
}
