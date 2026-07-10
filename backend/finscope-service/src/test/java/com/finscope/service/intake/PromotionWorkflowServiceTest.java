package com.finscope.service.intake;

import com.finscope.dao.research.ContentIdeaRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.intake.PromoteIntakeCandidateResponse;
import com.finscope.domain.research.EventCluster;
import com.finscope.service.research.EventClusterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionWorkflowServiceTest {
    @Mock
    private EventClusterService eventClusterService;
    @Mock
    private EvidenceItemRepository evidenceItemRepository;
    @Mock
    private LearningTaskRepository learningTaskRepository;
    @Mock
    private ContentIdeaRepository contentIdeaRepository;

    private PromotionWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new PromotionWorkflowService();
        ReflectionTestUtils.setField(service, "eventClusterService", eventClusterService);
        ReflectionTestUtils.setField(service, "evidenceItemRepository", evidenceItemRepository);
        ReflectionTestUtils.setField(service, "learningTaskRepository", learningTaskRepository);
        ReflectionTestUtils.setField(service, "contentIdeaRepository", contentIdeaRepository);
    }

    @Test
    void attach_nullArticle_marksFailed() {
        PromoteIntakeCandidateResponse response = service.attach(1L, "PROMOTED", null);
        assertEquals("FAILED", response.getWorkflowStatus());
        assertEquals("文章不存在，无法生成研究工作包", response.getWorkflowErrorMessage());
        assertNull(response.getEventId());
    }

    @Test
    void attach_articleWithoutId_marksFailed() {
        Article article = new Article();
        PromoteIntakeCandidateResponse response = service.attach(1L, "PROMOTED", article);
        assertEquals("FAILED", response.getWorkflowStatus());
        assertEquals("文章不存在，无法生成研究工作包", response.getWorkflowErrorMessage());
    }

    @Test
    void attach_attachThrows_marksFailedWithSanitizedMessage() {
        Article article = new Article();
        article.setId(1L);
        when(eventClusterService.attachArticle(any())).thenThrow(new RuntimeException("SQL constraint violation: duplicate key"));

        PromoteIntakeCandidateResponse response = service.attach(1L, "PROMOTED", article);
        assertEquals("FAILED", response.getWorkflowStatus());
        assertEquals("研究工作包生成异常，请稍后重试", response.getWorkflowErrorMessage());
        assertNull(response.getEventId());
    }

    @Test
    void attach_success_populatesAllFields() {
        Article article = new Article();
        article.setId(1L);

        EventCluster event = new EventCluster();
        event.setId(10L);
        event.setCanonicalTitle("美联储降息预期升温");

        when(eventClusterService.attachArticle(any())).thenReturn(event);
        when(evidenceItemRepository.countByEventId(10L)).thenReturn(3);
        when(learningTaskRepository.countByEventId(10L)).thenReturn(2);
        when(contentIdeaRepository.countByEventId(10L)).thenReturn(1);

        PromoteIntakeCandidateResponse response = service.attach(5L, "PROMOTED", article);

        assertEquals("SUCCESS", response.getWorkflowStatus());
        assertEquals(1L, response.getArticleId());
        assertEquals(5L, response.getCandidateId());
        assertEquals("PROMOTED", response.getStatus());
        assertEquals(10L, response.getEventId());
        assertEquals("美联储降息预期升温", response.getEventTitle());
        assertEquals(3, response.getEvidenceCount());
        assertEquals(2, response.getLearningTaskCount());
        assertEquals(1, response.getContentIdeaCount());
    }
}
