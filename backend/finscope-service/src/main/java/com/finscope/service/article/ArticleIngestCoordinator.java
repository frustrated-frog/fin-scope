package com.finscope.service.article;

import com.finscope.common.constant.ContentConstants;
import com.finscope.common.util.StringUtils;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.article.ArticleIngestResult;
import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.insight.InsightCard;
import com.finscope.domain.source.Source;
import com.finscope.domain.task.TaskPhase;
import com.finscope.service.dedupe.FingerprintService;
import com.finscope.service.dedupe.NoveltyService;
import com.finscope.service.insight.InsightCardService;
import com.finscope.service.research.EventClusterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.function.Consumer;

@Service
@Slf4j
public class ArticleIngestCoordinator {
    @Resource
    private ArticleRepository articleRepository;
    @Resource
    private FingerprintService fingerprintService;
    @Resource
    private NoveltyService noveltyService;
    @Resource
    private InsightCardService insightCardService;
    @Resource
    private EventClusterService eventClusterService;
    @Resource
    private ArticleCategoryPolicy articleCategoryPolicy;

    public ArticleIngestResult ingest(Source source, RawItem item) {
        return ingestInternal(source, item, null, null);
    }

    public ArticleIngestResult ingest(Source source, RawItem item, Consumer<TaskPhase> phaseConsumer) {
        return ingestInternal(source, item, null, phaseConsumer);
    }

    public ArticleIngestResult ingest(Source source,
                                      RawItem item,
                                      String category,
                                      Consumer<TaskPhase> phaseConsumer) {
        return ingestInternal(source, item, category, phaseConsumer);
    }

    private ArticleIngestResult ingestInternal(Source source,
                                               RawItem item,
                                               String category,
                                               Consumer<TaskPhase> phaseConsumer) {
        long start = System.currentTimeMillis();
        String title = StringUtils.firstNonBlank(item.getTitle(), source.getUrl(), ContentConstants.DEFAULT_ARTICLE_TITLE);
        String url = StringUtils.firstNonBlank(item.getUrl(), source.getUrl(), "");
        log.info("文章入库开始 sourceId={} sourceName={} title={}", source.getId(), source.getName(), title);
        Article article = Article.createFetched(source.getId(), StringUtils.firstNonBlank(source.getName(), ContentConstants.DEFAULT_SOURCE_NAME),
                title, url, item.getPublishedAt(), item.getSummary(), item.getBody());
        article.setCategory(resolveArticleCategory(category, source.getTags(), item));

        String urlFingerprint = fingerprintService.urlFingerprint(url);
        String titleFingerprint = fingerprintService.normalizeText(title);
        String body = StringUtils.firstNonBlank(item.getBody(), item.getSummary(), title);
        long bodySimhash = fingerprintService.bodySimhash(body);
        NoveltyService.NoveltyDecision decision = noveltyService.decide(urlFingerprint, title, bodySimhash);
        article.setNoveltyType(decision.getType());
        article.setNoveltyReason(decision.getReason());

        publishPhase(phaseConsumer, TaskPhase.PERSISTING);
        Article saved = articleRepository.save(article, urlFingerprint, titleFingerprint, bodySimhash);
        publishPhase(phaseConsumer, TaskPhase.LLM);
        InsightCard card = insightCardService.createForArticle(saved);
        publishPhase(phaseConsumer, TaskPhase.PERSISTING);
        eventClusterService.attachArticle(saved);
        log.info("文章入库成功 articleId={} sourceId={} noveltyType={} insightCardId={} durationMs={}",
                saved.getId(), source.getId(), saved.getNoveltyType(), card.getId(), System.currentTimeMillis() - start);
        return new ArticleIngestResult(saved, card);
    }

    private void publishPhase(Consumer<TaskPhase> phaseConsumer, TaskPhase phase) {
        if (phaseConsumer != null) {
            phaseConsumer.accept(phase);
        }
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

    private String resolveArticleCategory(String category, String tags, RawItem item) {
        if (StringUtils.isNotBlank(category)) {
            return articleCategoryPolicy.normalize(category);
        }
        return articleCategoryPolicy.fromLegacyCategory(resolveCategory(tags, item));
    }
}
