package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.news.ai.NewsAiStatus;
import com.finscope.service.news.NewsAiStatusService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;

@RestController
@RequestMapping("/api/news/ai")
public class NewsAiController {
    @Resource
    private NewsAiStatusService service;

    @GetMapping("/status")
    public ApiResponse<NewsAiStatus> status() {
        return ApiResponses.success(service.status());
    }
}
