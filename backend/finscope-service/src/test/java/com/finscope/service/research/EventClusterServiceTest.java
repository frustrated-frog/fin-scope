package com.finscope.service.research;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.research.ContentIdeaRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.EventArticleLink;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchEnums;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventClusterServiceTest {
    @Test
    void updateStatusValidatesAllowedEventStatus() {
        EventClusterRepository eventClusterRepository = mock(EventClusterRepository.class);
        EventCluster event = event(1L, ResearchEnums.EVENT_ACTIVE);
        when(eventClusterRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventClusterRepository.updateStatus(1L, ResearchEnums.EVENT_COOLING))
                .thenReturn(event(1L, ResearchEnums.EVENT_COOLING));

        EventCluster updated = service(eventClusterRepository).updateStatus(1L, "cooling");

        assertEquals(ResearchEnums.EVENT_COOLING, updated.getStatus());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service(eventClusterRepository).updateStatus(1L, "BROKEN"));
        assertEquals("Unsupported event status: BROKEN", error.getMessage());
    }

    @Test
    void mergeMovesEventArtifactsAndArchivesSource() {
        EventClusterRepository eventClusterRepository = mock(EventClusterRepository.class);
        EvidenceItemRepository evidenceItemRepository = mock(EvidenceItemRepository.class);
        LearningTaskRepository learningTaskRepository = mock(LearningTaskRepository.class);
        ContentIdeaRepository contentIdeaRepository = mock(ContentIdeaRepository.class);
        EventCluster source = event(1L, ResearchEnums.EVENT_ACTIVE);
        EventCluster target = event(2L, ResearchEnums.EVENT_ACTIVE);

        when(eventClusterRepository.findById(1L)).thenReturn(Optional.of(source));
        when(eventClusterRepository.findById(2L)).thenReturn(Optional.of(target));
        when(eventClusterRepository.update(any(EventCluster.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventCluster merged = service(eventClusterRepository, evidenceItemRepository,
                learningTaskRepository, contentIdeaRepository).merge(1L, 2L);

        assertEquals(2L, merged.getId());
        assertEquals(ResearchEnums.EVENT_ARCHIVED, source.getStatus());
        verify(eventClusterRepository).moveLinks(1L, 2L);
        verify(evidenceItemRepository).moveByEventId(1L, 2L);
        verify(learningTaskRepository).moveByEventId(1L, 2L);
        verify(contentIdeaRepository).moveByEventId(1L, 2L);
        verify(eventClusterRepository).refreshCounts(Arrays.asList(1L, 2L));
        verify(eventClusterRepository).update(source);
    }

    @Test
    void mergeRejectsArchivedSourceOrTarget() {
        EventClusterRepository eventClusterRepository = mock(EventClusterRepository.class);
        EventCluster archivedSource = event(1L, ResearchEnums.EVENT_ARCHIVED);
        EventCluster activeTarget = event(2L, ResearchEnums.EVENT_ACTIVE);
        when(eventClusterRepository.findById(1L)).thenReturn(Optional.of(archivedSource));
        when(eventClusterRepository.findById(2L)).thenReturn(Optional.of(activeTarget));

        BusinessException sourceError = assertThrows(BusinessException.class,
                () -> service(eventClusterRepository).merge(1L, 2L));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, sourceError.getErrorCode());
        verify(eventClusterRepository, never()).moveLinks(any(), any());

        EventCluster activeSource = event(3L, ResearchEnums.EVENT_ACTIVE);
        EventCluster archivedTarget = event(4L, ResearchEnums.EVENT_ARCHIVED);
        when(eventClusterRepository.findById(3L)).thenReturn(Optional.of(activeSource));
        when(eventClusterRepository.findById(4L)).thenReturn(Optional.of(archivedTarget));

        BusinessException targetError = assertThrows(BusinessException.class,
                () -> service(eventClusterRepository).merge(3L, 4L));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, targetError.getErrorCode());
    }

    @Test
    void moveArticleCanCreateNewEventAndMoveArticleEvidence() {
        EventClusterRepository eventClusterRepository = mock(EventClusterRepository.class);
        EvidenceItemRepository evidenceItemRepository = mock(EvidenceItemRepository.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EventClassifier eventClassifier = mock(EventClassifier.class);
        EventCluster source = event(1L, ResearchEnums.EVENT_ACTIVE);
        EventCluster created = event(3L, ResearchEnums.EVENT_ACTIVE);
        Article article = article(2L);
        EventArticleLink link = new EventArticleLink();
        link.setEventId(1L);
        link.setArticleId(2L);
        link.setNoveltyReason("命中历史事件");

        when(eventClusterRepository.findById(1L)).thenReturn(Optional.of(source));
        when(eventClusterRepository.findById(3L)).thenReturn(Optional.of(created));
        when(eventClusterRepository.findLink(1L, 2L)).thenReturn(Optional.of(link));
        when(articleRepository.findById(2L)).thenReturn(Optional.of(article));
        when(eventClassifier.signature(article)).thenReturn(new EventClassifier.EventSignature(
                ResearchEnums.THEME_CHINA_MACRO,
                "china_macro:fed:rate",
                82));
        when(eventClusterRepository.save(any(EventCluster.class))).thenAnswer(invocation -> {
            EventCluster event = invocation.getArgument(0);
            event.setId(3L);
            return event;
        });
        when(eventClusterRepository.moveArticleLink(1L, 2L, 3L, "命中历史事件；人工治理调整"))
                .thenReturn(1);

        EventCluster moved = service(eventClusterRepository, evidenceItemRepository,
                mock(LearningTaskRepository.class), mock(ContentIdeaRepository.class), articleRepository,
                eventClassifier).moveArticle(1L, 2L, null, true);

        assertEquals(3L, moved.getId());
        verify(eventClusterRepository).moveArticleLink(1L, 2L, 3L, "命中历史事件；人工治理调整");
        verify(evidenceItemRepository).moveByEventIdAndArticleId(1L, 2L, 3L);
        verify(eventClusterRepository).refreshCounts(Arrays.asList(1L, 3L));
    }

    @Test
    void moveArticleRejectsArchivedSourceOrTarget() {
        EventClusterRepository eventClusterRepository = mock(EventClusterRepository.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EventCluster archivedSource = event(1L, ResearchEnums.EVENT_ARCHIVED);
        EventCluster activeTarget = event(3L, ResearchEnums.EVENT_ACTIVE);
        when(eventClusterRepository.findById(1L)).thenReturn(Optional.of(archivedSource));
        when(eventClusterRepository.findById(3L)).thenReturn(Optional.of(activeTarget));
        when(eventClusterRepository.findLink(1L, 2L)).thenReturn(Optional.of(link(1L, 2L)));
        when(articleRepository.findById(2L)).thenReturn(Optional.of(article(2L)));

        BusinessException sourceError = assertThrows(BusinessException.class,
                () -> service(eventClusterRepository, mock(EvidenceItemRepository.class),
                        mock(LearningTaskRepository.class), mock(ContentIdeaRepository.class),
                        articleRepository, mock(EventClassifier.class)).moveArticle(1L, 2L, 3L, null));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, sourceError.getErrorCode());

        EventCluster activeSource = event(4L, ResearchEnums.EVENT_ACTIVE);
        EventCluster archivedTarget = event(5L, ResearchEnums.EVENT_ARCHIVED);
        when(eventClusterRepository.findById(4L)).thenReturn(Optional.of(activeSource));
        when(eventClusterRepository.findById(5L)).thenReturn(Optional.of(archivedTarget));
        when(eventClusterRepository.findLink(4L, 2L)).thenReturn(Optional.of(link(4L, 2L)));

        BusinessException targetError = assertThrows(BusinessException.class,
                () -> service(eventClusterRepository, mock(EvidenceItemRepository.class),
                        mock(LearningTaskRepository.class), mock(ContentIdeaRepository.class),
                        articleRepository, mock(EventClassifier.class)).moveArticle(4L, 2L, 5L, null));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, targetError.getErrorCode());
    }

    @Test
    void moveArticleRejectsWhenLinkMoveAffectsNoRows() {
        EventClusterRepository eventClusterRepository = mock(EventClusterRepository.class);
        EvidenceItemRepository evidenceItemRepository = mock(EvidenceItemRepository.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EventCluster source = event(1L, ResearchEnums.EVENT_ACTIVE);
        EventCluster target = event(3L, ResearchEnums.EVENT_ACTIVE);
        when(eventClusterRepository.findById(1L)).thenReturn(Optional.of(source));
        when(eventClusterRepository.findById(3L)).thenReturn(Optional.of(target));
        when(eventClusterRepository.findLink(1L, 2L)).thenReturn(Optional.of(link(1L, 2L)));
        when(articleRepository.findById(2L)).thenReturn(Optional.of(article(2L)));
        when(eventClusterRepository.moveArticleLink(1L, 2L, 3L, "命中历史事件；人工治理调整"))
                .thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(eventClusterRepository, evidenceItemRepository,
                        mock(LearningTaskRepository.class), mock(ContentIdeaRepository.class),
                        articleRepository, mock(EventClassifier.class)).moveArticle(1L, 2L, 3L, null));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, error.getErrorCode());
        verify(evidenceItemRepository, never()).moveByEventIdAndArticleId(any(), any(), any());
    }

    private EventClusterService service(EventClusterRepository eventClusterRepository) {
        return service(eventClusterRepository, mock(EvidenceItemRepository.class),
                mock(LearningTaskRepository.class), mock(ContentIdeaRepository.class));
    }

    private EventClusterService service(EventClusterRepository eventClusterRepository,
                                        EvidenceItemRepository evidenceItemRepository,
                                        LearningTaskRepository learningTaskRepository,
                                        ContentIdeaRepository contentIdeaRepository) {
        return service(eventClusterRepository, evidenceItemRepository, learningTaskRepository,
                contentIdeaRepository, mock(ArticleRepository.class), mock(EventClassifier.class));
    }

    private EventClusterService service(EventClusterRepository eventClusterRepository,
                                        EvidenceItemRepository evidenceItemRepository,
                                        LearningTaskRepository learningTaskRepository,
                                        ContentIdeaRepository contentIdeaRepository,
                                        ArticleRepository articleRepository,
                                        EventClassifier eventClassifier) {
        EventClusterService service = new EventClusterService();
        ReflectionTestUtils.setField(service, "eventClusterRepository", eventClusterRepository);
        ReflectionTestUtils.setField(service, "evidenceItemRepository", evidenceItemRepository);
        ReflectionTestUtils.setField(service, "learningTaskRepository", learningTaskRepository);
        ReflectionTestUtils.setField(service, "contentIdeaRepository", contentIdeaRepository);
        ReflectionTestUtils.setField(service, "articleRepository", articleRepository);
        ReflectionTestUtils.setField(service, "eventClassifier", eventClassifier);
        return service;
    }

    private EventCluster event(Long id, String status) {
        EventCluster event = new EventCluster();
        event.setId(id);
        event.setCanonicalTitle("美联储降息预期升温");
        event.setCanonicalEventKey("china_macro:fed:rate");
        event.setThemeCode(ResearchEnums.THEME_CHINA_MACRO);
        event.setSummary("市场交易降息预期。");
        event.setStatus(status);
        event.setFirstSeenAt(LocalDateTime.of(2026, 6, 28, 9, 0));
        event.setLastSeenAt(LocalDateTime.of(2026, 6, 28, 9, 0));
        event.setNoveltyState(ResearchEnums.NOVELTY_NEW);
        event.setImportanceScore(80);
        return event;
    }

    private Article article(Long id) {
        Article article = Article.createFetched(null, "Reuters",
                "美联储降息预期升温",
                "https://example.com/fed",
                LocalDateTime.of(2026, 6, 28, 9, 0),
                "市场交易降息预期。",
                "市场交易降息预期。");
        article.setId(id);
        return article;
    }

    private EventArticleLink link(Long eventId, Long articleId) {
        EventArticleLink link = new EventArticleLink();
        link.setEventId(eventId);
        link.setArticleId(articleId);
        link.setNoveltyReason("命中历史事件");
        return link;
    }
}
