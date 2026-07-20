package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.domain.request.DeleteArticlesRequest;
import com.finscope.domain.request.IngestUrlRequest;
import com.finscope.domain.response.PageResponse;
import com.finscope.service.article.ArticleCardView;
import com.finscope.service.article.ArticleDeletionService;
import com.finscope.service.article.ArticleQueryService;
import com.finscope.service.task.TaskView;
import com.finscope.service.task.UrlIngestTaskService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {
    @Resource
    private ArticleQueryService articleQueryService;
    @Resource
    private UrlIngestTaskService urlIngestTaskService;
    @Resource
    private ArticleDeletionService articleDeletionService;

    /**
     * 查询全部文章卡片列表。
     *
     * @return 文章卡片视图列表，包含文章基础信息和摘要展示字段。
     */
    @GetMapping
    public ApiResponse<List<ArticleCardView>> list() {
        return ApiResponses.success(articleQueryService.list());
    }

    /**
     * 分页查询文章卡片列表。
     *
     * @param page 页码，从 0 开始。
     * @param pageSize 每页条数。
     * @return 分页后的文章卡片结果，包含记录列表和分页元数据。
     */
    @GetMapping("/paged")
    public ApiResponse<PageResponse<ArticleCardView>> listPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponses.success(articleQueryService.listPaged(page, pageSize));
    }

    /**
     * 查询单篇文章详情。
     *
     * @param id 文章主键 ID。
     * @return 指定文章的卡片详情视图。
     */
    @GetMapping("/{id}")
    public ApiResponse<ArticleCardView> detail(@PathVariable Long id) {
        return ApiResponses.success(articleQueryService.detail(id));
    }

    /**
     * 删除单篇文章。
     *
     * @param id 文章主键 ID。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        articleDeletionService.deleteById(id);
        return ApiResponses.success(null);
    }

    /**
     * 批量删除文章。
     *
     * @param request 批量删除请求，包含待删除文章 ID 列表。
     * @return 实际删除的文章数量。
     */
    @DeleteMapping("/batch")
    public ApiResponse<Integer> deleteBatch(@RequestBody DeleteArticlesRequest request) {
        return ApiResponses.success(articleDeletionService.deleteByIds(request.getIds()));
    }

    /**
     * 提交 URL 入库抓取任务。
     *
     * @param request URL 入库请求，包含待抓取地址及相关参数。
     * @return 已创建的异步任务视图，用于查询任务状态和进度。
     */
    @PostMapping("/ingest-url")
    public ApiResponse<TaskView> ingestUrl(@RequestBody IngestUrlRequest request) {
        return ApiResponses.success(urlIngestTaskService.submit(request));
    }
}
