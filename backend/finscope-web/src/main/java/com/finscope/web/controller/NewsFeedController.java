package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.news.NewsCategory;
import com.finscope.service.news.NewsClassificationReviewRequest;
import com.finscope.service.news.NewsClassificationReviewService;
import com.finscope.service.news.NewsClassificationView;
import com.finscope.service.news.NewsFeedService;
import com.finscope.service.news.NewsFeedSnapshot;
import com.finscope.service.news.NewsSourceRefreshService;
import com.finscope.service.cache.ViewRevisionService;
import com.finscope.service.cache.ViewSnapshotCacheService;
import com.finscope.web.response.ApiResponses;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.Duration;

@RestController
@RequestMapping("/api/news")
public class NewsFeedController {
    private final NewsFeedService newsFeedService;
    private final NewsClassificationReviewService reviewService;
    private final NewsSourceRefreshService refreshService;
    private final ViewSnapshotCacheService snapshots;
    private final ViewRevisionService revisions;

    public NewsFeedController(NewsFeedService newsFeedService,
                              NewsClassificationReviewService reviewService,
                              NewsSourceRefreshService refreshService,
                              ViewSnapshotCacheService snapshots,
                              ViewRevisionService revisions) {
        this.newsFeedService = newsFeedService;
        this.reviewService = reviewService;
        this.refreshService = refreshService;
        this.snapshots = snapshots;
        this.revisions = revisions;
    }

    /**
     * 查询新闻资讯流。
     *
     * @param category 新闻分类过滤条件，默认 ALL。
     * @param limit 返回条数上限，默认 100。
     * @return 新闻资讯流快照，包含分类新闻列表和更新时间。
     */
    @GetMapping
    public ApiResponse<JsonNode> feed(@RequestParam(defaultValue = "ALL") String category,
                                      @RequestParam(defaultValue = "100") int limit) {
        String normalizedCategory = category == null ? "ALL" : category.trim().toUpperCase(java.util.Locale.ROOT);
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        JsonNode data = snapshots.readOrLoad("news", "category=" + normalizedCategory + "&limit=" + normalizedLimit,
                Duration.ofHours(36), () -> newsFeedService.load(normalizedCategory, normalizedLimit));
        return ApiResponses.success(data);
    }

    /**
     * 查询新闻分类列表。
     *
     * @return 新闻分类列表。
     */
    @GetMapping("/categories")
    public ApiResponse<List<NewsCategory>> categories() {
        return ApiResponses.success(newsFeedService.categories());
    }

    /**
     * 人工复核新闻分类结果。
     *
     * @param request 分类复核请求，包含新闻 ID 和目标分类。
     * @return 复核后的新闻分类视图。
     */
    @PostMapping("/classifications/review")
    public ApiResponse<NewsClassificationView> review(@RequestBody NewsClassificationReviewRequest request) {
        NewsClassificationView view = reviewService.review(request);
        revisions.invalidate("news");
        return ApiResponses.success(view);
    }

    /** 手动同步只提交后台任务；完成后由页面版本事件驱动读取最新快照。 */
    @PostMapping("/refresh")
    public ApiResponse<Boolean> refresh() {
        return ApiResponses.success(refreshService.requestRefresh());
    }
}
