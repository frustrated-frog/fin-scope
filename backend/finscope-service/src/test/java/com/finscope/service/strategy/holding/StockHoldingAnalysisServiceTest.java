package com.finscope.service.strategy.holding;

import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockHoldingAnalysis;
import com.finscope.domain.strategy.holding.StockHoldingAnalysisRequest;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.rpc.quant.PythonHoldingAnalysisClient;
import com.finscope.service.quant.forecast.SingleStockForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockHoldingAnalysisServiceTest {
    private StockAccountService accounts;
    private PythonHoldingAnalysisClient client;
    private SingleStockForecastRunRepository forecastRuns;
    private SingleStockForecastService forecasts;
    private StockHoldingAnalysisService service;

    @BeforeEach
    void setUp() {
        accounts = mock(StockAccountService.class);
        client = mock(PythonHoldingAnalysisClient.class);
        forecastRuns = mock(SingleStockForecastRunRepository.class);
        forecasts = mock(SingleStockForecastService.class);
        service = new StockHoldingAnalysisService();
        ReflectionTestUtils.setField(service, "accounts", accounts);
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "forecastRuns", forecastRuns);
        ReflectionTestUtils.setField(service, "forecasts", forecasts);
    }

    @Test
    void combinesLedgerPositionPathAndLatestForecastEvidence() {
        StockPosition position = position();
        StockAccountSnapshot account = new StockAccountSnapshot();
        account.setPositions(Collections.singletonList(position));
        when(accounts.snapshot()).thenReturn(account);
        StockHoldingAnalysis path = new StockHoldingAnalysis();
        path.setInstrumentCode("603618.SH");
        path.setHoldingReturn(0.208d);
        path.setQualityStatus("COMPLETE");
        when(client.analyze(any(StockHoldingAnalysisRequest.class))).thenReturn(path);
        SingleStockForecastRun run = forecastRun();
        when(forecastRuns.findLatest("603618.SH")).thenReturn(Optional.of(run));
        when(forecasts.detail(14L)).thenReturn(run);

        StockHoldingAnalysis result = service.analyze("603618.SH");

        assertEquals("杭电股份", result.getInstrumentName());
        assertNotNull(result.getForecast());
        assertEquals(14L, result.getForecast().getRunId());
        assertEquals(0.4847d, result.getForecast().getUpProbability());
    }

    private StockPosition position() {
        StockPosition value = new StockPosition();
        value.setInstrumentCode("603618.SH");
        value.setInstrumentName("杭电股份");
        value.setOpenedOn(LocalDate.of(2026, 7, 15));
        value.setAverageCost(new BigDecimal("32.49"));
        value.setQuantity(new BigDecimal("100"));
        value.setLastPrice(new BigDecimal("39.25"));
        return value;
    }

    private SingleStockForecastRun forecastRun() {
        SingleStockForecast report = new SingleStockForecast();
        report.setAsOfDate(LocalDate.of(2026, 9, 3));
        report.setStatus("NO_CLEAR_EDGE");
        report.setUpProbability(0.4847d);
        SingleStockForecast.ReturnDistribution distribution = new SingleStockForecast.ReturnDistribution();
        distribution.setP10(-0.05d);
        distribution.setP50(-0.001d);
        distribution.setP90(0.07d);
        report.setReturnDistribution(distribution);
        SingleStockForecastRun value = new SingleStockForecastRun();
        value.setId(14L);
        value.setInstrumentCode("603618.SH");
        value.setHorizonDays(5);
        value.setModelVersion("competition-v10");
        value.setReport(report);
        return value;
    }
}
