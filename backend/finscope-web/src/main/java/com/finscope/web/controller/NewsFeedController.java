package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.news.NewsCategory;
import com.finscope.service.news.NewsClassificationReviewRequest;
import com.finscope.service.news.NewsClassificationReviewService;
import com.finscope.service.news.NewsClassificationView;
import com.finscope.service.news.NewsFeedService;
import com.finscope.service.news.NewsFeedSnapshot;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/news")
public class NewsFeedController {
    private final NewsFeedService newsFeedService;
    private final NewsClassificationReviewService reviewService;

    public NewsFeedController(NewsFeedService newsFeedService,
                              NewsClassificationReviewService reviewService) {
        this.newsFeedService = newsFeedService;
        this.reviewService = reviewService;
    }

    /**
     * 查询新闻资讯流。
     *
     * @param category 新闻分类过滤条件，默认 ALL。
     * @param limit 返回条数上限，默认 100。
     * @return 新闻资讯流快照，包含分类新闻列表和更新时间。
     */
    @GetMapping
    public ApiResponse<NewsFeedSnapshot> feed(@RequestParam(defaultValue = "ALL") String category,
                                              @RequestParam(defaultValue = "100") int limit) {
        return ApiResponses.success(newsFeedService.load(category, limit));
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
        return ApiResponses.success(reviewService.review(request));
    }
}
