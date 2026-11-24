package com.finscope.service.quant.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.quant.PaperUniverse;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.domain.instrument.WatchlistItem;
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
        service.generate(new PaperResearchInput("研究动量", PaperUniverse.WATCHLIST, BigDecimal.TEN, null, null, null));
        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(paper).research(payload.capture());
        assertEquals(1, payload.getValue().path("codes").size());
        assertEquals("动量", payload.getValue().path("rule").path("name").asText());
        verify(paper, never()).deploy(anyLong());
    }

    @Test
    void refusesEmptyPoolBeforeGenerating() {
        assertThrows(BusinessException.class, () -> service.generate(
                new PaperResearchInput("研究动量", PaperUniverse.WATCHLIST, BigDecimal.TEN, null, null, null)));
        verifyNoInteractions(llm, paper);
    }
}
