package com.finscope.web.controller;

import com.finscope.domain.learningcard.StockLearningCard;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.service.learningcard.StockLearningCardService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockLearningCardController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class StockLearningCardControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private StockLearningCardService learningCardService;

    @Test
    void startsAControlledStockLearningRun() throws Exception {
        StockLearningCardRun run = new StockLearningCardRun();
        run.setId(8L); run.setStatus("RUNNING"); run.setStage("QUEUED");
        when(learningCardService.start(eq("600519"))).thenReturn(run);

        mockMvc.perform(post("/api/stock-learning-cards/{code}/runs", "600519"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.stage").value("QUEUED"))
                .andExpect(jsonPath("$.data.researchRunId").doesNotExist());
    }

    @Test
    void returnsTheLatestLearningCard() throws Exception {
        StockLearningCard card = new StockLearningCard();
        card.setCode("600519"); card.setName("贵州茅台");
        StockLearningCardRun run = new StockLearningCardRun();
        run.setStatus("READY"); run.setSummary("仅供学习，不构成投资建议");
        when(learningCardService.get(eq("600519"))).thenReturn(new StockLearningCardService.StockLearningCardView(card, run));

        mockMvc.perform(get("/api/stock-learning-cards/{code}", "600519"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.card.name").value("贵州茅台"))
                .andExpect(jsonPath("$.data.latestRun.status").value("READY"));
    }
}
