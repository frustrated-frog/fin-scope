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

    @GetMapping
    public ApiResponse<NewsFeedSnapshot> feed(@RequestParam(defaultValue = "ALL") String category,
                                              @RequestParam(defaultValue = "100") int limit) {
        return ApiResponses.success(newsFeedService.load(category, limit));
    }

    @GetMapping("/categories")
    public ApiResponse<List<NewsCategory>> categories() {
        return ApiResponses.success(newsFeedService.categories());
    }

    @PostMapping("/classifications/review")
    public ApiResponse<NewsClassificationView> review(@RequestBody NewsClassificationReviewRequest request) {
        return ApiResponses.success(reviewService.review(request));
    }
}
