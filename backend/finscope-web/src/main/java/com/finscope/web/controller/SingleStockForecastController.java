package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.domain.quant.forecast.ForecastModelHealth;
import com.finscope.service.quant.forecast.SingleStockForecastService;
import com.finscope.web.request.quant.RunSingleStockForecastRequest;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.quant.SingleStockForecastRunResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/quant/single-stock-forecasts")
@Validated
public class SingleStockForecastController {
    private final SingleStockForecastService service;

    public SingleStockForecastController(SingleStockForecastService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<SingleStockForecastRunResponse> forecast(
            @Valid @RequestBody RunSingleStockForecastRequest request) {
        return ApiResponses.success(SingleStockForecastRunResponse.of(
                service.forecast(request.getCode(), request.getHorizonDays())));
    }

    @GetMapping
    public ApiResponse<List<SingleStockForecastRunResponse>> history(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer horizonDays,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return ApiResponses.success(service.history(code, limit, horizonDays).stream()
                .map(SingleStockForecastRunResponse::of)
                .collect(Collectors.toList()));
    }

    @GetMapping("/health")
    public ApiResponse<ForecastModelHealth> health(@RequestParam String code,
                                                   @RequestParam int horizonDays,
                                                   @RequestParam String modelVersion) {
        return ApiResponses.success(service.health(code, horizonDays, modelVersion));
    }

    @GetMapping("/{id}")
    public ApiResponse<SingleStockForecastRunResponse> detail(@PathVariable Long id) {
        return ApiResponses.success(SingleStockForecastRunResponse.of(service.detail(id)));
    }
}
