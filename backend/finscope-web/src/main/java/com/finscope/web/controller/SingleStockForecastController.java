package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.service.quant.forecast.SingleStockForecastService;
import com.finscope.web.request.quant.RunSingleStockForecastRequest;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/quant/single-stock-forecasts")
public class SingleStockForecastController {
    private final SingleStockForecastService service;

    public SingleStockForecastController(SingleStockForecastService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<SingleStockForecast> forecast(
            @Valid @RequestBody RunSingleStockForecastRequest request) {
        return ApiResponses.success(service.forecast(request.getCode()));
    }
}
