package com.finscope.service.article;

import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.insight.InsightCardRepository;
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

    @Transactional
    public void deleteById(Long id) {
        log.info("Deleting article with id={}", id);
        insightCardRepository.deleteByArticleId(id);
        int deleted = articleRepository.deleteById(id);

        if (deleted == 0) {
            throw new IllegalArgumentException("Article not found: " + id);
        }
        log.info("Successfully deleted article id={}", id);
    }

    @Transactional
    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        log.info("Batch deleting {} articles", ids.size());
        insightCardRepository.deleteByArticleIds(ids);
        int deleted = articleRepository.deleteByIds(ids);
        log.info("Successfully deleted {} articles", deleted);
        return deleted;
    }
}
