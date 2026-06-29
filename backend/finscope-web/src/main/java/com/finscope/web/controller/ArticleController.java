package com.finscope.web.controller;

import com.finscope.domain.request.DeleteArticlesRequest;
import com.finscope.domain.request.IngestUrlRequest;
import com.finscope.domain.response.PageResponse;
import com.finscope.service.article.ArticleCardView;
import com.finscope.domain.article.ArticleIngestResult;
import com.finscope.service.article.ArticleDeletionService;
import com.finscope.service.article.ArticleQueryService;
import com.finscope.service.article.UrlIngestService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {
    private final ArticleQueryService articleQueryService;
    private final UrlIngestService urlIngestService;
    private final ArticleDeletionService articleDeletionService;

    public ArticleController(ArticleQueryService articleQueryService,
                             UrlIngestService urlIngestService,
                             ArticleDeletionService articleDeletionService) {
        this.articleQueryService = articleQueryService;
        this.urlIngestService = urlIngestService;
        this.articleDeletionService = articleDeletionService;
    }

    @GetMapping
    public List<ArticleCardView> list() {
        return articleQueryService.list();
    }

    @GetMapping("/paged")
    public PageResponse<ArticleCardView> listPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return articleQueryService.listPaged(page, pageSize);
    }

    @GetMapping("/{id}")
    public ArticleCardView detail(@PathVariable Long id) {
        return articleQueryService.detail(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        articleDeletionService.deleteById(id);
    }

    @DeleteMapping("/batch")
    public int deleteBatch(@RequestBody DeleteArticlesRequest request) {
        return articleDeletionService.deleteByIds(request.getIds());
    }

    @PostMapping("/ingest-url")
    public ArticleIngestResult ingestUrl(@RequestBody IngestUrlRequest request) {
        return urlIngestService.ingest(request.getUrl(), request.getSourceName(), request.getTags());
    }
}
