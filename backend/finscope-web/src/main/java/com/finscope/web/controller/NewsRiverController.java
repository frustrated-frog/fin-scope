package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.radar.NewsRiverService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.NewsRiverOverviewResponse;
import com.finscope.web.response.NewsRiverPageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/news/river")
public class NewsRiverController {
    @Resource
    private NewsRiverService service;

    @GetMapping
    public ApiResponse<NewsRiverOverviewResponse> overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponses.success(NewsRiverOverviewResponse.from(service.overview(date)));
    }

    @GetMapping("/frame")
    public ApiResponse<NewsRiverPageResponse> frame(@RequestParam long batchId, @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime bucket,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponses.success(NewsRiverPageResponse.from(service.frame(batchId, category, bucket, page)));
    }
}
