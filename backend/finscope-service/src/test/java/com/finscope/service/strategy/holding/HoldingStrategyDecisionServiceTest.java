package com.finscope.service.strategy.holding;

import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.dao.strategy.HoldingStrategyDecisionRepository;
import com.finscope.domain.quant.forecast.ForecastModelHealth;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import com.finscope.domain.strategy.holding.HoldingStrategyAdvice;
import com.finscope.domain.strategy.holding.HoldingStrategyDecision;
import com.finscope.domain.strategy.holding.HoldingStrategyEvaluationRequest;
import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.rpc.quant.PythonHoldingStrategyClient;
import com.finscope.service.quant.forecast.SingleStockForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HoldingStrategyDecisionServiceTest {
    private HoldingStrategyDecisionRepository decisions;
    private SingleStockForecastRunRepository forecastRuns;
    private SingleStockForecastService forecasts;
    private StockAccountService accounts;
    private PythonHoldingStrategyClient client;
    private HoldingStrategyDecisionService service;

    @BeforeEach
    void setUp() {
        decisions = mock(HoldingStrategyDecisionRepository.class);
        forecastRuns = mock(SingleStockForecastRunRepository.class);
        forecasts = mock(SingleStockForecastService.class);
        accounts = mock(StockAccountService.class);
        client = mock(PythonHoldingStrategyClient.class);
        service = new HoldingStrategyDecisionService();
        ReflectionTestUtils.setField(service, "decisions", decisions);
        ReflectionTestUtils.setField(service, "forecastRuns", forecastRuns);
        ReflectionTestUtils.setField(service, "forecasts", forecasts);
        ReflectionTestUtils.setField(service, "accounts", accounts);
        ReflectionTestUtils.setField(service, "client", client);
    }

    @Test
    void freezesAdviceFromLatestForecastAndKeepsCostOutOfPrediction() {
        StockPosition position = position("600570.SH", "恒生电子");
        StockAccountSnapshot account = account(position);
        SingleStockForecastRun run = run(12L, "600570.SH");
        HoldingStrategyAdvice advice = advice();
        when(accounts.snapshot()).thenReturn(account);
        when(decisions.findUnique(any(), any(), any())).thenReturn(Optional.empty());
        when(forecastRuns.findLatest("600570.SH")).thenReturn(Optional.of(run));
        when(forecasts.detail(12L)).thenReturn(run);
        when(client.evaluate(any(HoldingStrategyEvaluationRequest.class))).thenReturn(advice);
        when(decisions.save(any(HoldingStrategyDecision.class))).thenAnswer(invocation -> {
            HoldingStrategyDecision value = invocation.getArgument(0);
            value.setId(21L);
            return value;
        });

        List<HoldingStrategyDecision> result = service.refresh();

        assertEquals(1, result.size());
        assertEquals("ALLOW_ADD", result.get(0).getAction());
        assertEquals(12L, result.get(0).getForecastRunId());
        assertEquals("同一只股票保持当时持仓不动", result.get(0).getBenchmark());
        assertTrue(result.get(0).getInputJson().contains("\"costBasis\":25.0"));
        verify(forecasts).detail(12L);
        verify(client).evaluate(any(HoldingStrategyEvaluationRequest.class));
    }

    @Test
    void isolatesMissingForecastPerPositionWithoutPersistingFalseEvidence() {
        StockPosition first = position("600570.SH", "恒生电子");
        StockPosition second = position("000001.SZ", "平安银行");
        StockAccountSnapshot account = account(first, second);
        when(accounts.snapshot()).thenReturn(account);
        when(decisions.findUnique(any(), any(), any())).thenReturn(Optional.empty());
        when(forecastRuns.findLatest(any())).thenReturn(Optional.empty());

        List<HoldingStrategyDecision> result = service.refresh();

        assertEquals(2, result.size());
        assertEquals("ABSTAIN", result.get(0).getAction());
        assertEquals("UNAVAILABLE", result.get(1).getValidationStatus());
        assertNull(result.get(0).getId());
        assertTrue(result.get(0).getBlockers().get(0).contains("尚无可复用"));
    }

    private StockAccountSnapshot account(StockPosition... positions) {
        StockAccountSnapshot value = new StockAccountSnapshot();
        value.setCash(new BigDecimal("4000"));
        value.setMarketValue(new BigDecimal("6000"));
        value.setTotalEquity(new BigDecimal("10000"));
        value.setPositions(Arrays.asList(positions));
        return value;
    }

    private StockPosition position(String code, String name) {
        StockPosition value = new StockPosition();
        value.setInstrumentCode(code);
        value.setInstrumentName(name);
        value.setQuantity(new BigDecimal("100"));
        value.setAverageCost(new BigDecimal("25"));
        value.setLastPrice(new BigDecimal("30"));
        value.setMarketValue(new BigDecimal("3000"));
        value.setQuoteDate(LocalDate.now());
        return value;
    }

    private SingleStockForecastRun run(Long id, String code) {
        SingleStockForecast report = new SingleStockForecast();
        report.setInstrumentCode(code);
        report.setAsOfDate(LocalDate.now());
        report.setHorizonDays(5);
        report.setStatus("CONDITIONAL");
        report.setUpProbability(0.68d);
        SingleStockForecast.ReturnDistribution distribution = new SingleStockForecast.ReturnDistribution();
        distribution.setP10(-0.035d);
        distribution.setP50(0.032d);
        distribution.setP90(0.09d);
        report.setReturnDistribution(distribution);
        SingleStockForecast.StrategyPolicy policy = new SingleStockForecast.StrategyPolicy();
        policy.setRoundTripCostRate(0.0015d);
        report.setStrategyPolicy(policy);
        SingleStockForecastRun value = new SingleStockForecastRun();
        value.setId(id);
        value.setInstrumentCode(code);
        value.setHorizonDays(5);
        value.setModelVersion("panel-logit-v10");
        value.setDataFingerprint("sha256:abc");
        value.setReport(report);
        ForecastModelHealth health = new ForecastModelHealth();
        health.setStatus("HEALTHY");
        value.setModelHealth(health);
        return value;
    }

    private HoldingStrategyAdvice advice() {
        HoldingStrategyAdvice value = new HoldingStrategyAdvice();
        value.setAction("ALLOW_ADD");
        value.setSuggestedQuantity(100);
        value.setExpectedEdgeAfterCost(0.0305d);
        value.setP10RiskAmount(-105d);
        value.setP90UpsideAmount(270d);
        value.setCurrentMarketValue(3000d);
        value.setProjectedWeight(0.6d);
        value.setEvidence(Collections.singletonList("费用后中位优势 3.05%"));
        value.setBlockers(Collections.emptyList());
        value.setExplanation("允许按最小整手逐步增加暴露");
        value.setBenchmark("同一只股票保持当时持仓不动");
        value.setPolicyVersion("holding-policy-v1");
        return value;
    }
}
