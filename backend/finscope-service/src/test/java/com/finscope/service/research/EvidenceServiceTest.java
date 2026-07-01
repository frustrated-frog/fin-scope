package com.finscope.service.research;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceServiceTest {
    @Test
    void rejectsUngroundedAgentEvidenceAndFallsBackToArticleEvidence() throws Exception {
        EvidenceItemRepository evidenceItemRepository = mock(EvidenceItemRepository.class);
        AgentRunRepository agentRunRepository = mock(AgentRunRepository.class);
        EvidenceService service = service(evidenceItemRepository, agentRunRepository,
                new StaticLlmClient("{\"items\":[{\"evidenceType\":\"DATA\","
                        + "\"claim\":\"公司披露 990 亿美元虚构营收并宣布回购。\",\"confidence\":92}]}"));
        when(evidenceItemRepository.save(any(EvidenceItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(evidenceItemRepository.countByEventId(anyLong())).thenReturn(1);

        service.capture(event(), article());

        ArgumentCaptor<EvidenceItem> captor = ArgumentCaptor.forClass(EvidenceItem.class);
        org.mockito.Mockito.verify(evidenceItemRepository).save(captor.capture());
        EvidenceItem saved = captor.getValue();
        assertFalse(saved.getClaim().contains("990 亿美元虚构营收"));
        assertTrue(saved.getClaim().contains("单周净流入12亿美元"));
    }

    private EvidenceService service(EvidenceItemRepository evidenceItemRepository,
                                    AgentRunRepository agentRunRepository,
                                    LlmChatClient llmChatClient) {
        EvidenceService service = new EvidenceService();
        ReflectionTestUtils.setField(service, "evidenceItemRepository", evidenceItemRepository);
        ReflectionTestUtils.setField(service, "sourceRepository", mock(SourceRepository.class));
        ReflectionTestUtils.setField(service, "agentRunRepository", agentRunRepository);
        ReflectionTestUtils.setField(service, "llmChatClient", llmChatClient);
        return service;
    }

    private EventCluster event() {
        EventCluster event = new EventCluster();
        event.setId(7L);
        event.setCanonicalTitle("美联储降息预期升温 黄金ETF获得资金流入");
        event.setThemeCode(ResearchEnums.THEME_CHINA_MACRO);
        event.setSummary("黄金 ETF 因降息预期获得资金流入。");
        return event;
    }

    private Article article() {
        Article article = Article.createFetched(null, "Reuters",
                "美联储降息预期升温 黄金ETF单周流入12亿美元",
                "https://example.com/fed-gold",
                LocalDateTime.of(2026, 6, 27, 9, 0),
                "市场继续交易美联储降息预期，黄金ETF单周净流入12亿美元。",
                "市场继续交易美联储降息预期，黄金ETF单周净流入12亿美元，创下阶段新高。");
        article.setId(9L);
        return article;
    }

    private static class StaticLlmClient implements LlmChatClient {
        private final String response;

        StaticLlmClient(String response) {
            this.response = response;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String modelName() {
            return "test";
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            return response;
        }
    }
}
