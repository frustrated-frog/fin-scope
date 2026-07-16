package com.finscope.service.insight;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.insight.InsightCardRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.insight.InsightCard;
import com.finscope.service.agent.ArticleInterpretation;
import com.finscope.service.agent.ArticleInterpretationAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class InsightCardService {
    @Resource
    private InsightCardRepository insightCardRepository;
    @Resource
    private InsightCardGenerator insightCardGenerator;
    @Resource
    private ArticleInterpretationAgent articleInterpretationAgent;

    public InsightCard createForArticle(Article article) {
        Optional<InsightCard> existing = insightCardRepository.findByArticleId(article.getId());
        if (existing.isPresent()) {
            log.info("复用情报卡片 articleId={} insightCardId={}", article.getId(), existing.get().getId());
            return existing.get();
        }
        long start = System.currentTimeMillis();
        log.info("情报卡片生成开始 articleId={} title={}", article.getId(), article.getTitle());
        ArticleInterpretation interpretation = articleInterpretationAgent.interpret(article);
        InsightCard card = insightCardRepository.save(insightCardGenerator.generate(article, interpretation));
        log.info("情报卡片生成成功 articleId={} insightCardId={} durationMs={}",
                article.getId(), card.getId(), System.currentTimeMillis() - start);
        return card;
    }

    public List<InsightCard> list() {
        return insightCardRepository.findAll();
    }

    public InsightCard detail(Long id) {
        return insightCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("情报卡片不存在：" + id));
    }
}
