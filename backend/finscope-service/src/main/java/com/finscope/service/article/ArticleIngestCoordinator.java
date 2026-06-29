package com.finscope.service.article;

import com.finscope.common.constant.ContentConstants;
import com.finscope.common.util.StringUtils;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.article.ArticleIngestResult;
import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.insight.InsightCard;
import com.finscope.domain.source.Source;
import com.finscope.service.dedupe.FingerprintService;
import com.finscope.service.dedupe.NoveltyService;
import com.finscope.service.insight.InsightCardService;
import com.finscope.service.research.EventClusterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ArticleIngestCoordinator {
    private final ArticleRepository articleRepository;
    private final FingerprintService fingerprintService;
    private final NoveltyService noveltyService;
    private final InsightCardService insightCardService;
    private final EventClusterService eventClusterService;

    public ArticleIngestCoordinator(ArticleRepository articleRepository,
                                    FingerprintService fingerprintService,
                                    NoveltyService noveltyService,
                                    InsightCardService insightCardService,
                                    EventClusterService eventClusterService) {
        this.articleRepository = articleRepository;
        this.fingerprintService = fingerprintService;
        this.noveltyService = noveltyService;
        this.insightCardService = insightCardService;
        this.eventClusterService = eventClusterService;
    }

    @Transactional
    public ArticleIngestResult ingest(Source source, RawItem item) {
        long start = System.currentTimeMillis();
        String title = StringUtils.firstNonBlank(item.getTitle(), source.getUrl(), ContentConstants.DEFAULT_ARTICLE_TITLE);
        String url = StringUtils.firstNonBlank(item.getUrl(), source.getUrl(), "");
        log.info("article ingest start sourceId={} sourceName={} title={}", source.getId(), source.getName(), title);
        Article article = Article.createFetched(source.getId(), StringUtils.firstNonBlank(source.getName(), ContentConstants.DEFAULT_SOURCE_NAME),
                title, url, item.getPublishedAt(), item.getSummary(), item.getBody());
        article.setCategory(resolveCategory(source.getTags(), item));

        String urlFingerprint = fingerprintService.urlFingerprint(url);
        String titleFingerprint = fingerprintService.normalizeText(title);
        String body = StringUtils.firstNonBlank(item.getBody(), item.getSummary(), title);
        long bodySimhash = fingerprintService.bodySimhash(body);
        NoveltyService.NoveltyDecision decision = noveltyService.decide(urlFingerprint, title, bodySimhash);
        article.setNoveltyType(decision.getType());
        article.setNoveltyReason(decision.getReason());

        Article saved = articleRepository.save(article, urlFingerprint, titleFingerprint, bodySimhash);
        InsightCard card = insightCardService.createForArticle(saved);
        eventClusterService.attachArticle(saved);
        log.info("article ingest success articleId={} sourceId={} noveltyType={} insightCardId={} durationMs={}",
                saved.getId(), source.getId(), saved.getNoveltyType(), card.getId(), System.currentTimeMillis() - start);
        return new ArticleIngestResult(saved, card);
    }

    private String resolveCategory(String tags, RawItem item) {
        String text = (StringUtils.firstNonBlank(tags, "") + " " + StringUtils.firstNonBlank(item.getTitle(), "") + " "
                + StringUtils.firstNonBlank(item.getSummary(), "") + " " + StringUtils.firstNonBlank(item.getBody(), "")).toLowerCase();
        if (text.contains("宏观") || text.contains("央行") || text.contains("fed") || text.contains("美联储")
                || text.contains("降息") || text.contains("加息") || text.contains("通胀")) {
            return ContentConstants.CATEGORY_MACRO;
        }
        if (text.contains("政策") || text.contains("监管")) {
            return ContentConstants.CATEGORY_POLICY;
        }
        if (text.contains("财报") || text.contains("公司") || text.contains("营收") || text.contains("利润")) {
            return ContentConstants.CATEGORY_COMPANY;
        }
        if (text.contains("行业") || text.contains("产业链")) {
            return ContentConstants.CATEGORY_INDUSTRY;
        }
        return ContentConstants.CATEGORY_MARKET;
    }
}
