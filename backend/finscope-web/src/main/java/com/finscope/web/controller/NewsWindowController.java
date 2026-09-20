package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.news.*;
import com.finscope.service.news.NewsWindowService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/news/window")
public class NewsWindowController {
    @Resource
    private NewsWindowService news;

    @GetMapping
    public ApiResponse<NewsWindowPage> query(@ModelAttribute NewsWindowQuery query) {
        return ApiResponses.success(news.query(query));
    }

    @GetMapping("/detail")
    public ApiResponse<NewsReportDetail> detail(@RequestParam String id) {
        return ApiResponses.success(news.detail(id));
    }

    @PostMapping("/read")
    public ApiResponse<Boolean> read(@RequestParam String id, @RequestParam int version) {
        news.read(id, version);
        return ApiResponses.success(true);
    }

    @PostMapping("/review")
    public ApiResponse<Boolean> review(@RequestParam String id, @RequestParam String category,
                                       @RequestParam(defaultValue = "") String reason) {
        news.review(id, category, reason);
        return ApiResponses.success(true);
    }

    @GetMapping("/filters")
    public ApiResponse<List<NewsSavedFilter>> filters() {
        return ApiResponses.success(news.filters());
    }

    @PostMapping("/filters")
    public ApiResponse<NewsSavedFilter> save(@RequestBody NewsSavedFilter filter) {
        return ApiResponses.success(news.saveFilter(filter));
    }

    @DeleteMapping("/filters/{id}")
    public ApiResponse<Boolean> delete(@PathVariable String id) {
        news.deleteFilter(id);
        return ApiResponses.success(true);
    }
}
