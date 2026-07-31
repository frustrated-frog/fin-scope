package com.finscope.web.controller;

import com.finscope.domain.news.NewsCategory;
import com.finscope.service.news.NewsClassificationReviewService;
import com.finscope.service.news.NewsClassificationView;
import com.finscope.service.news.NewsFeedService;
import com.finscope.service.news.NewsFeedSnapshot;
import com.finscope.web.config.FinScopeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsFeedController.class)
@Import(FinScopeProperties.class)
class NewsFeedControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private NewsFeedService newsFeedService;
    @MockBean
    private NewsClassificationReviewService reviewService;

    @Test
    void passesCategoryToFeedService() throws Exception {
        when(newsFeedService.load("COMPANY", 25)).thenReturn(new NewsFeedSnapshot(Collections.emptyList(),
                Collections.emptyList(), LocalDateTime.of(2026, 7, 31, 10, 0), 0));

        mockMvc.perform(get("/api/news").param("category", "COMPANY").param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        verify(newsFeedService).load("COMPANY", 25);
    }

    @Test
    void returnsEnabledCategoryCatalog() throws Exception {
        when(newsFeedService.categories()).thenReturn(Collections.singletonList(
                new NewsCategory("COMPANY", "公司动态", "公司经营变化", true, 10)));

        mockMvc.perform(get("/api/news/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("COMPANY"))
                .andExpect(jsonPath("$.data[0].name").value("公司动态"));
    }

    @Test
    void reviewsNewsClassification() throws Exception {
        when(reviewService.review(org.mockito.ArgumentMatchers.argThat(request ->
                "CLS:1".equals(request.getItemId())
                        && "INDUSTRY".equals(request.getCategoryCode())
                        && "产业链影响".equals(request.getReason())))).thenReturn(
                new NewsClassificationView("CLS:1", "COMPANY", "INDUSTRY", 0.65,
                        "公司公告", "CORRECTED", "产业链影响",
                        LocalDateTime.of(2026, 8, 1, 10, 0)));

        mockMvc.perform(post("/api/news/classifications/review")
                        .contentType("application/json")
                        .content("{\"itemId\":\"CLS:1\",\"categoryCode\":\"INDUSTRY\","
                                + "\"reason\":\"产业链影响\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentCategoryCode").value("COMPANY"))
                .andExpect(jsonPath("$.data.effectiveCategoryCode").value("INDUSTRY"))
                .andExpect(jsonPath("$.data.reviewStatus").value("CORRECTED"));
    }
}
