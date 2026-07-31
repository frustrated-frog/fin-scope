package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.news.NewsCategory;
import com.finscope.service.news.NewsFeedService;
import com.finscope.service.news.NewsFeedSnapshot;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/news")
public class NewsFeedController {
    private final NewsFeedService newsFeedService;

    public NewsFeedController(NewsFeedService newsFeedService) {
        this.newsFeedService = newsFeedService;
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
}
