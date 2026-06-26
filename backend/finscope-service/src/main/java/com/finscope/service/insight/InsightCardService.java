package com.finscope.service.insight;

import com.finscope.dao.insight.InsightCardRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.insight.InsightCard;
import com.finscope.service.agent.ArticleInterpretation;
import com.finscope.service.agent.ArticleInterpretationAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class InsightCardService {
    private final InsightCardRepository insightCardRepository;
    private final InsightCardGenerator insightCardGenerator;
    private final ArticleInterpretationAgent articleInterpretationAgent;

    public InsightCardService(InsightCardRepository insightCardRepository,
                              InsightCardGenerator insightCardGenerator,
                              ArticleInterpretationAgent articleInterpretationAgent) {
        this.insightCardRepository = insightCardRepository;
        this.insightCardGenerator = insightCardGenerator;
        this.articleInterpretationAgent = articleInterpretationAgent;
    }

    public InsightCard createForArticle(Article article) {
        Optional<InsightCard> existing = insightCardRepository.findByArticleId(article.getId());
        if (existing.isPresent()) {
            log.info("insight card reuse articleId={} insightCardId={}", article.getId(), existing.get().getId());
            return existing.get();
        }
        long start = System.currentTimeMillis();
        log.info("insight card generation start articleId={} title={}", article.getId(), article.getTitle());
        ArticleInterpretation interpretation = articleInterpretationAgent.interpret(article);
        InsightCard card = insightCardRepository.save(insightCardGenerator.generate(article, interpretation));
        log.info("insight card generation success articleId={} insightCardId={} durationMs={}",
                article.getId(), card.getId(), System.currentTimeMillis() - start);
        return card;
    }

    public List<InsightCard> list() {
        return insightCardRepository.findAll();
    }

    public InsightCard detail(Long id) {
        return insightCardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Insight card not found: " + id));
    }
}
