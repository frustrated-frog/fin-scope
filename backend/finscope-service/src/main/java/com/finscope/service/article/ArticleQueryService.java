package com.finscope.service.article;

import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.insight.InsightCardRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.insight.InsightCard;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ArticleQueryService {

    @Resource
    private ArticleRepository articleRepository;
    @Resource
    private InsightCardRepository insightCardRepository;

    public List<ArticleCardView> list() {
        List<Article> articles = articleRepository.findAll();
        List<Long> articleIds = articles.stream().map(Article::getId).collect(Collectors.toList());
        Map<Long, InsightCard> cards = new HashMap<Long, InsightCard>(insightCardRepository.findByArticleIds(articleIds));
        List<ArticleCardView> views = new ArrayList<ArticleCardView>();
        for (Article article : articles) {
            views.add(new ArticleCardView(article, cards.get(article.getId())));
        }
        return views;
    }

    public ArticleCardView detail(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));
        InsightCard card = insightCardRepository.findByArticleId(id).orElse(null);
        return new ArticleCardView(article, card);
    }

    public int countAll() {
        return articleRepository.countAll();
    }
}
