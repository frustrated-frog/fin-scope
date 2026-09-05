package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.service.quant.forecast.NextSessionPredictionService;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.quant.NextSessionPredictionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/quant/next-session-predictions")
public class NextSessionPredictionController {
    @Resource
    private NextSessionPredictionService service;

    @GetMapping
    public ApiResponse<List<NextSessionPredictionResponse>> history(
            @RequestParam(required = false) String code, @RequestParam(defaultValue = "30") int limit) {
        return ApiResponses.success(service.history(code, limit).stream()
                .map(NextSessionPredictionResponse::of).collect(Collectors.toList()));
    }
}
