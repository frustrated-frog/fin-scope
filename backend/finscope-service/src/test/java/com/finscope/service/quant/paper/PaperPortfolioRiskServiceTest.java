package com.finscope.service.quant.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.quant.PaperStockNameRepository;
import com.finscope.domain.quant.paper.ObservedIndustry;
import com.finscope.rpc.quant.PythonPaperClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperPortfolioRiskServiceTest {
    @Test
    void preservesUnknownsAndShocksOnlyKnownIndustryExposure() throws Exception {
        var rpc = mock(PythonPaperClient.class);
        var stocks = mock(PaperStockNameRepository.class);
        var service = new PaperPortfolioRiskService();
        ReflectionTestUtils.setField(service, "paper", rpc);
        ReflectionTestUtils.setField(service, "stocks", stocks);
        var day = LocalDate.parse("2026-11-27");
        when(rpc.portfolioRisk(2, day, 60)).thenReturn(new ObjectMapper().readTree("""
                {"at":"2026-11-27T07:00:00Z","holdings":[
                {"code":"a","weight":0.3,"riskShare":1.2},{"code":"b","weight":0.2,"riskShare":-0.2},
                {"code":"c","weight":0.1,"riskShare":null}], "shocks":[]}
                """));
        var codes = List.of("a", "b", "c");
        var observed = new ObservedIndustry("银行", day.minusDays(1));
        when(stocks.industriesAt(codes, LocalDateTime.parse("2026-11-27T15:00"))).thenReturn(Map.of("a", observed, "b", observed));
        when(stocks.findNames(codes)).thenReturn(Map.of("a", "农行"));
        var result = service.analyze(2, day, 60);
        assertEquals("农行", result.at("/holdings/0/name").asText());
        assertEquals("2026-11-26", result.at("/holdings/0/industryAsOf").asText());
        assertEquals(0.5, result.at("/industries/0/weight").asDouble());
        assertEquals(1, result.at("/industries/0/riskShare").asDouble());
        assertTrue(result.at("/industries/1/industry").isNull());
        assertTrue(result.at("/industries/1/riskShare").isNull());
        assertEquals(-0.05, result.at("/shocks/0/change").asDouble(), 1e-10);
    }
}
