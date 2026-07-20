package com.finscope.service.article;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.insight.InsightCardRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleDeletionServiceExceptionTest {

    @Test
    void throwsTypedChineseExceptionWhenArticleDoesNotExist() {
        ArticleRepository articles = mock(ArticleRepository.class);
        InsightCardRepository cards = mock(InsightCardRepository.class);
        EventClusterRepository events = mock(EventClusterRepository.class);
        EvidenceItemRepository evidence = mock(EvidenceItemRepository.class);
        when(events.findEventIdsByArticleIds(anyList())).thenReturn(Collections.emptyList());
        when(articles.deleteById(999L)).thenReturn(0);

        ArticleDeletionService service = new ArticleDeletionService();
        ReflectionTestUtils.setField(service, "articleRepository", articles);
        ReflectionTestUtils.setField(service, "insightCardRepository", cards);
        ReflectionTestUtils.setField(service, "eventClusterRepository", events);
        ReflectionTestUtils.setField(service, "evidenceItemRepository", evidence);

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class, () -> service.deleteById(999L));

        assertEquals("文章不存在：999", error.getMessage());
    }
}
