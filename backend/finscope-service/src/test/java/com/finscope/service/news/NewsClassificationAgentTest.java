package com.finscope.service.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.news.NewsCategory;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsClassificationAgentTest {
    @Test
    void parsesFencedJsonAndKeepsOnlyKnownItemsAndCategories() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(contains("只能从给定分类目录"), contains("公司重大订单")))
                .thenReturn("```json\n["
                        + "{\"itemId\":\"CLS:1\",\"categoryCode\":\"COMPANY\",\"confidence\":0.9,\"reason\":\"公司经营事件\"},"
                        + "{\"itemId\":\"THS:2\",\"categoryCode\":\"MADE_UP\",\"confidence\":0.8,\"reason\":\"未知分类\"},"
                        + "{\"itemId\":\"OTHER:3\",\"categoryCode\":\"COMPANY\",\"confidence\":0.8,\"reason\":\"未知条目\"}"
                        + "]\n```");
        NewsClassificationAgent agent = new NewsClassificationAgent(llm, new ObjectMapper());

        Map<String, NewsClassificationAgent.Decision> result = agent.classify(
                Arrays.asList(candidate("CLS:1", "公司重大订单"), candidate("THS:2", "行业价格变化")),
                Arrays.asList(category("COMPANY", "公司动态"), category("INDUSTRY", "行业产业")));

        assertEquals(1, result.size());
        assertEquals("COMPANY", result.get("CLS:1").getCategoryCode());
        assertEquals(0.9, result.get("CLS:1").getConfidence(), 0.001);
        assertFalse(result.containsKey("THS:2"));
    }

    @Test
    void rejectsClassificationWhenModelIsNotConfigured() {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(false);
        NewsClassificationAgent agent = new NewsClassificationAgent(llm, new ObjectMapper());

        try {
            agent.classify(Arrays.asList(candidate("CLS:1", "公司重大订单")),
                    Arrays.asList(category("COMPANY", "公司动态")));
        } catch (IllegalStateException ex) {
            assertEquals("资讯分类 Agent 尚未配置", ex.getMessage());
            return;
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        throw new AssertionError("expected IllegalStateException");
    }

    private static NewsClassificationCandidate candidate(String id, String title) {
        return new NewsClassificationCandidate(id, title, "正文", "财联社",
                LocalDateTime.of(2026, 7, 31, 10, 0));
    }

    private static NewsCategory category(String code, String name) {
        return new NewsCategory(code, name, name + "分类指导", true, 10);
    }
}
