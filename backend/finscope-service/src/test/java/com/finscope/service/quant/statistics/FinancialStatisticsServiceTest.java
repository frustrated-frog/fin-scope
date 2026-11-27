package com.finscope.service.quant.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.marketpulse.MarketPulseRepository;
import com.finscope.domain.quant.statistics.FinancialStatisticsInput;
import com.finscope.rpc.quant.PythonPaperClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialStatisticsServiceTest {
    @Test
    void attributesOnlyReturnsWithAPriorKnownStage() throws Exception {
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        var paper = mock(PythonPaperClient.class);
        var pulse = mock(MarketPulseRepository.class);
        var service = new FinancialStatisticsService();
        ReflectionTestUtils.setField(service, "json", json);
        ReflectionTestUtils.setField(service, "paper", paper);
        ReflectionTestUtils.setField(service, "pulse", pulse);
        var raw = json.readTree("""
                {"daily":[{"day":"2026-11-24","prior":"2026-11-23","value":0.02},
                          {"day":"2026-11-25","prior":"2026-11-24","value":-0.03}]}
                """);
        var prior = LocalDate.of(2026, 11, 23);
        when(paper.financialStatistics(any())).thenReturn(raw);
        when(pulse.timelyStages(prior, prior.plusDays(2))).thenReturn(Map.of(prior, MarketStage.RISK_ON));
        var result = service.analyze(new FinancialStatisticsInput("SH:000001", "SZ:399006", prior.plusDays(2), 90, 20, 0));
        var row = result.required("marketStages").get(0);
        assertEquals(1, row.required("n").asInt());
        assertEquals(.02, row.required("mean").asDouble(), 1e-10);
        assertFalse(result.has("daily"));
        assertThrows(BusinessException.class, () -> service.analyze(
                new FinancialStatisticsInput("SH:000001", "SH:000001", prior, 90, 20, 0)));
    }
}
