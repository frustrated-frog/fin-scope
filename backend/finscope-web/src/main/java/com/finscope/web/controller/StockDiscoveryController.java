package com.finscope.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.api.ApiResponse;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import com.finscope.service.quant.discovery.StockDiscoveryService;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quant/stock-discoveries")
public class StockDiscoveryController {
    @Resource
    private StockDiscoveryService service;
    @Resource
    private ObjectMapper json;

    @GetMapping("/latest")
    public ApiResponse<Object> latest() {
        return ApiResponses.success(service.latest().<Object>map(this::view)
                .orElseGet(() -> Collections.singletonMap("status", "EMPTY")));
    }

    @GetMapping("/runs")
    public ApiResponse<List<StockDiscoveryRun>> history(
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.success(service.history(limit));
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<Object> detail(@PathVariable Long id) {
        return ApiResponses.success(view(service.detail(id)));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        StockDiscoveryRun run = service.history(1).stream().findFirst().orElse(null);
        return ApiResponses.success(run == null
                ? Collections.<String, Object>singletonMap("status", "EMPTY")
                : Map.of("status", run.getStatus(), "runId", run.getId(),
                "businessDate", run.getBusinessDate()));
    }

    private Object view(StockDiscoveryRun run) {
        if (run.getReportJson() == null) {
            return run;
        }
        try {
            JsonNode report = json.readTree(run.getReportJson());
            return Map.of("run", run, "report", report);
        } catch (Exception error) {
            throw new IllegalStateException("股票发现报告无法读取", error);
        }
    }
}
