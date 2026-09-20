package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.news.NewsFeedSnapshot;
import com.finscope.service.news.NewsWindowService;
import com.finscope.web.response.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class NewsWindowController {
    @Autowired
    private NewsWindowService news;

    @GetMapping("/api/news/paged")
    public ApiResponse<NewsFeedSnapshot> page(
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "ALL") String source,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "36") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {
        return ApiResponses.success(news.load(category, source, query, hours, page, pageSize, asOf));
    }
}
