package com.finscope.service.article;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.insight.InsightCardRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.insight.InsightCard;
import com.finscope.domain.response.PageResponse;
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

    public PageResponse<ArticleCardView> listPaged(int page, int pageSize) {
        List<Article> articles = articleRepository.findAllPaged(page, pageSize);
        List<Long> articleIds = articles.stream().map(Article::getId).collect(Collectors.toList());
        Map<Long, InsightCard> cards = new HashMap<Long, InsightCard>(
            insightCardRepository.findByArticleIds(articleIds)
        );

        List<ArticleCardView> views = new ArrayList<ArticleCardView>();
        for (Article article : articles) {
            views.add(new ArticleCardView(article, cards.get(article.getId())));
        }

        int totalCount = articleRepository.countAll();
        return PageResponse.of(views, totalCount, page, pageSize);
    }

    public ArticleCardView detail(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("文章不存在：" + id));
        InsightCard card = insightCardRepository.findByArticleId(id).orElse(null);
        return new ArticleCardView(article, card);
    }

    public int countAll() {
        return articleRepository.countAll();
    }
}
