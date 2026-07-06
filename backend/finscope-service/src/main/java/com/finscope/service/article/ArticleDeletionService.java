package com.finscope.service.article;

import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.insight.InsightCardRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
@Slf4j
public class ArticleDeletionService {
    @Resource
    private ArticleRepository articleRepository;
    @Resource
    private InsightCardRepository insightCardRepository;
    @Resource
    private EventClusterRepository eventClusterRepository;
    @Resource
    private EvidenceItemRepository evidenceItemRepository;

    @Transactional
    public void deleteById(Long id) {
        log.info("删除文章开始 articleId={}", id);
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
        log.info("删除文章成功 articleId={}", id);
    }

    @Transactional
    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        log.info("批量删除文章开始 articleCount={}", ids.size());
        List<Long> eventIds = eventClusterRepository.findEventIdsByArticleIds(ids);
        insightCardRepository.deleteByArticleIds(ids);
        evidenceItemRepository.deleteByArticleIds(ids);
        eventClusterRepository.deleteLinksByArticleIds(ids);
        int deleted = articleRepository.deleteByIds(ids);
        eventClusterRepository.refreshCounts(eventIds);
        log.info("批量删除文章成功 deletedCount={}", deleted);
        return deleted;
    }
}
