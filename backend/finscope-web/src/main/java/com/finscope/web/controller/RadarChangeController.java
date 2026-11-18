package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.common.enums.radar.RadarChangeKind;
import com.finscope.domain.radar.RadarChangePage;
import com.finscope.service.radar.RadarChangeService;
import com.finscope.web.response.ApiResponses;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/research-radar")
public class RadarChangeController {
    @Resource
    private RadarChangeService service;

    @GetMapping("/changes")
    public ApiResponse<RadarChangePage> recent(@RequestParam(defaultValue = "30") int minutes,
            @RequestParam(required = false) RadarChangeKind kind, @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {
        return ApiResponses.success(service.recent(minutes, kind, asOf, page));
    }
}
