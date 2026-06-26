package com.finscope.web.controller;

import com.finscope.domain.request.IngestUrlRequest;
import com.finscope.service.article.ArticleCardView;
import com.finscope.domain.article.ArticleIngestResult;
import com.finscope.service.article.ArticleQueryService;
import com.finscope.service.article.UrlIngestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    @Resource
    private ArticleQueryService articleQueryService;
    @Resource
    private UrlIngestService urlIngestService;

    @GetMapping
    public List<ArticleCardView> list() {
        return articleQueryService.list();
    }

    @GetMapping("/{id}")
    public ArticleCardView detail(@PathVariable Long id) {
        return articleQueryService.detail(id);
    }

    @PostMapping("/ingest-url")
    public ArticleIngestResult ingestUrl(@RequestBody IngestUrlRequest request) {
        return urlIngestService.ingest(request.getUrl(), request.getSourceName(), request.getTags());
    }
}
