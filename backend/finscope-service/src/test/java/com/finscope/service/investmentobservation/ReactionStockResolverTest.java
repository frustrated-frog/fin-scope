package com.finscope.service.investmentobservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.Quote;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.instrument.QuoteService;
import com.finscope.service.radar.RadarAgentTraceRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReactionStockResolverTest {
    private ReactionStockResolver resolver;
    private InstrumentRepository instruments;
    private QuoteService quotes;
    private LlmChatClient llm;
    private com.finscope.rpc.investmentobservation.ReactionStockNameLookup names;

    @BeforeEach
    void setup() {
        resolver = new ReactionStockResolver();
        instruments = mock(InstrumentRepository.class);
        quotes = mock(QuoteService.class);
        llm = mock(LlmChatClient.class);
        names = mock(com.finscope.rpc.investmentobservation.ReactionStockNameLookup.class);
        ReflectionTestUtils.setField(resolver, "names", names);
        ReflectionTestUtils.setField(resolver, "instruments", instruments);
        ReflectionTestUtils.setField(resolver, "quotes", quotes);
        ReflectionTestUtils.setField(resolver, "llm", llm);
        ReflectionTestUtils.setField(resolver, "json", new ObjectMapper());
        ReflectionTestUtils.setField(resolver, "traces", mock(RadarAgentTraceRecorder.class));
        when(instruments.findAll()).thenReturn(List.of());
    }

    @Test
    void localCompanyMatchWorksWithoutModelOrMarketRequest() {
        Instrument stock = new Instrument();
        stock.setCode("600519");
        stock.setName("贵州茅台");
        stock.setType("STOCK");
        stock.setMarket("SH");
        when(instruments.findAll()).thenReturn(List.of(stock));
        assertEquals("600519.SH", resolver.resolve("贵州茅台发布年度业绩").get(0).getCode());
        verifyNoInteractions(llm, quotes);
    }

    @Test
    void explicitTitleCompanyIsResolvedEvenWhenModelIsUnavailable() {
        var match = new com.finscope.domain.investmentobservation.ReactionStockMatch();
        match.setCode("300476");
        match.setName("胜宏科技");
        when(names.search("胜宏科技")).thenReturn(List.of(match));
        assertEquals("300476.SZ", resolver.resolve("胜宏科技：在手订单饱满").get(0).getCode());
        verifyNoInteractions(llm, quotes);
    }

    @Test
    void modelCodeMustMatchQuoteNameInOriginalTitle() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn("[{\"code\":\"600519\",\"name\":\"贵州茅台\"}]");
        Quote quote = new Quote();
        quote.setInstrumentCode("600519");
        quote.setName("另一家公司");
        when(quotes.fetch(eq("STOCK"), anyList())).thenReturn(List.of(quote));
        assertTrue(resolver.resolve("贵州茅台发布年度业绩").isEmpty());
        quote.setName("贵州茅台");
        assertEquals("600519.SH", resolver.resolve("贵州茅台发布年度业绩").get(0).getCode());
    }

    @Test
    void modelCannotInventBeneficiariesOrReplaceMissingDataWithATicker() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn("[{\"code\":\"600519\",\"name\":\"贵州茅台\"}]");
        assertTrue(resolver.resolve("白酒行业业绩改善").isEmpty());
        verifyNoInteractions(quotes);
        when(llm.complete(anyString(), anyString(), anyInt(), anyInt())).thenReturn("not json");
        assertTrue(resolver.resolve("白酒行业业绩改善").isEmpty());
    }
}
