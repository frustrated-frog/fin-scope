package com.finscope.web.controller;

import com.finscope.domain.instrument.Quote;
import com.finscope.service.instrument.MarketIndexService;
import com.finscope.service.instrument.MarketIndexView;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketIndexController.class)
@Import(FinScopeProperties.class)
class MarketIndexControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketIndexService marketIndexService;

    @Test
    void getsMarketIndices() throws Exception {
        Quote quote = new Quote();
        quote.setPrice(3200.00);
        quote.setChangeAmount(12.50);
        quote.setChangePct(0.39);
        quote.setValid(true);
        when(marketIndexService.list()).thenReturn(Collections.singletonList(
                new MarketIndexView("000001", "上证指数", quote)
        ));

        mockMvc.perform(get("/api/market-indices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("000001"))
                .andExpect(jsonPath("$[0].name").value("上证指数"))
                .andExpect(jsonPath("$[0].quoteValid").value(true));
    }
}
