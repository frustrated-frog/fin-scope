package com.finscope.service.article;

import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.insight.InsightCardRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class ArticleDeletionService {
    private final ArticleRepository articleRepository;
    private final InsightCardRepository insightCardRepository;
    private final EventClusterRepository eventClusterRepository;
    private final EvidenceItemRepository evidenceItemRepository;

    public ArticleDeletionService(ArticleRepository articleRepository,
                                  InsightCardRepository insightCardRepository,
                                  EventClusterRepository eventClusterRepository,
                                  EvidenceItemRepository evidenceItemRepository) {
        this.articleRepository = articleRepository;
        this.insightCardRepository = insightCardRepository;
        this.eventClusterRepository = eventClusterRepository;
        this.evidenceItemRepository = evidenceItemRepository;
    }

    @Transactional
    public void deleteById(Long id) {
        log.info("Deleting article with id={}", id);
        List<Long> ids = java.util.Collections.singletonList(id);
        List<Long> eventIds = eventClusterRepository.findEventIdsByArticleIds(ids);
        insightCardRepository.deleteByArticleId(id);
        evidenceItemRepository.deleteByArticleIds(ids);
        eventClusterRepository.deleteLinksByArticleIds(ids);
        int deleted = articleRepository.deleteById(id);

        if (deleted == 0) {
            throw new IllegalArgumentException("Article not found: " + id);
        }
        eventClusterRepository.refreshCounts(eventIds);
        log.info("Successfully deleted article id={}", id);
    }

    @Transactional
    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        log.info("Batch deleting {} articles", ids.size());
        List<Long> eventIds = eventClusterRepository.findEventIdsByArticleIds(ids);
        insightCardRepository.deleteByArticleIds(ids);
        evidenceItemRepository.deleteByArticleIds(ids);
        eventClusterRepository.deleteLinksByArticleIds(ids);
        int deleted = articleRepository.deleteByIds(ids);
        eventClusterRepository.refreshCounts(eventIds);
        log.info("Successfully deleted {} articles", deleted);
        return deleted;
    }
}
