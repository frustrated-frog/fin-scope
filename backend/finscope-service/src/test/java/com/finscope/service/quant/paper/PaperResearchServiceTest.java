package com.finscope.service.quant.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.quant.PaperUniverse;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.quant.paper.ClassicPaperResearchInput;
import com.finscope.domain.quant.paper.PaperResearchInput;
import com.finscope.rpc.llm.LunaResponsesClient;
import com.finscope.rpc.quant.PythonPaperClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperResearchServiceTest {
    @Mock private WatchlistRepository watchlist;
    @Mock private PythonPaperClient paper;
    @Mock private LunaResponsesClient llm;
    @Spy private ObjectMapper json = new ObjectMapper();
    @InjectMocks private PaperResearchService service;

    @Test
    void freezesDistinctStocksAndPassesExecutableRuleToReplay() throws Exception {
        WatchlistItem stock = new WatchlistItem();
        stock.setCode("600001");
        when(watchlist.findByTypes(List.of("STOCK"))).thenReturn(List.of(stock, stock));
        when(llm.isConfigured()).thenReturn(true);
        when(llm.completeText(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(2400))).thenReturn("{\"name\":\"动量\"}");
        service.generate(new PaperResearchInput("研究动量", PaperUniverse.WATCHLIST, BigDecimal.TEN, null, null, null, true));
        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(paper).research(payload.capture(), eq(true));
        assertEquals(1, payload.getValue().path("codes").size());
        assertEquals("动量", payload.getValue().path("rule").path("name").asText());
        verify(paper, never()).deploy(anyLong());
    }

    @Test
    void codingJobFreezesTheUniverseAndUsesTheRequestedAgent() {
        WatchlistItem stock = new WatchlistItem();
        stock.setCode("000001");
        when(watchlist.findByTypes(List.of("STOCK"))).thenReturn(List.of(stock));
        var token = java.util.UUID.randomUUID();
        service.submitCoding(new PaperResearchInput("", PaperUniverse.WATCHLIST,
                BigDecimal.TEN, null, null, null, true), true, token);
        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(paper).submitJob(payload.capture());
        JsonNode job = payload.getValue();
        assertEquals(token.toString(), job.path("requestId").asText());
        assertEquals("000001", job.path("codes").get(0).asText());
        assertTrue(job.path("autoIterate").asBoolean());
        assertTrue(job.path("autoStart").asBoolean());
        verifyNoInteractions(llm);
    }

    @Test
    void submitsDatedCohortExperimentWithExplicitCapital() {
        var token = java.util.UUID.randomUUID();
        service.submitRidge(new PaperResearchInput(null, null, BigDecimal.TEN, null, null, null, false), token);
        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(paper).submitJob(payload.capture());
        assertEquals("RIDGE", payload.getValue().path("researchMode").asText());
        assertEquals(token.toString(), payload.getValue().path("requestId").asText());
        assertEquals(10, payload.getValue().path("capital").asInt());
        assertFalse(payload.getValue().path("autoStart").asBoolean());
        verifyNoInteractions(llm, watchlist);
    }

    @Test
    void submitsClassicComparisonUsingExecutionEngineDefaultsForOmittedComponents() {
        var token = java.util.UUID.randomUUID();
        var recipe = new ClassicPaperResearchInput.Components("MOMENTUM", null, null, 7, null,
                null, null, "RISK_PARITY", null, null, null, BigDecimal.valueOf(30), 10,
                new BigDecimal("0.15"), new BigDecimal("0.08"), null);
        var input = new ClassicPaperResearchInput(BigDecimal.valueOf(10000), false, 4L, recipe, "2026-09-01", "2026-11-26");
        service.submitClassic(input, token);
        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(paper).submitJob(payload.capture());
        JsonNode job = payload.getValue();
        assertEquals(token.toString(), job.path("requestId").asText());
        assertEquals("CLASSIC", job.path("researchMode").asText());
        assertEquals("2026-09-01", job.path("startDate").asText());
        assertEquals("2026-11-26", job.path("endDate").asText());
        assertEquals(30, job.path("recipe").path("maxFeeBps").asInt());
        assertEquals("RISK_PARITY", job.path("recipe").path("allocation").asText());
        assertEquals(0.08, job.path("recipe").path("stopLoss").asDouble());
        assertEquals(4L, job.path("reference").asLong());
        assertEquals(10000, job.path("capital").asInt());
        assertFalse(job.path("autoStart").asBoolean());
        assertEquals("MOMENTUM", job.path("recipe").path("family").asText());
        assertEquals(7, job.path("recipe").path("window").asInt());
        assertFalse(job.path("recipe").has("period"));
        verifyNoInteractions(llm, watchlist);
    }

    @Test
    void teachesFromSelectedPeriodAndAccountWithoutRunningResearch() throws Exception {
        var day = java.time.LocalDate.of(2026, 11, 27);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.model()).thenReturn("test-luna");
        var report = json.readTree("""
                {"name":"选中策略","day":"2026-11-27","daily":{"returnDays":5},"quarterly":{"irrelevant":true},
                 "accounts":[{"version":1,"rejected":18},{"version":2,"rejected":0}],"entries":[]}
                """);
        when(paper.learning(1, day)).thenReturn(report);
        when(llm.completeText(anyString(), anyString(), eq(2000))).thenReturn("""
                {"lesson":"18 笔未成交需要核对原因","hypothesis":"执行成本与订单大小有关","criterion":"同规则比较五个交易日"}
                """);
        var answer = service.teach(1, day, "daily");
        var evidence = ArgumentCaptor.forClass(String.class);
        verify(llm).completeText(anyString(), evidence.capture(), eq(2000));
        JsonNode sent = json.readTree(evidence.getValue());
        assertEquals(18, sent.path("execution").path("rejected").asInt());
        assertFalse(sent.has("quarterly"));
        assertEquals("test-luna", answer.path("model").asText());
        verify(paper, never()).deploy(anyLong());
    }

    @Test
    void refusesEmptyPoolBeforeGenerating() {
        assertThrows(BusinessException.class, () -> service.generate(
                new PaperResearchInput("研究动量", PaperUniverse.WATCHLIST, BigDecimal.TEN, null, null, null, true)));
        verifyNoInteractions(llm, paper);
    }
}
