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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        String nextScheduledAt = nextScheduledAt();
        if (run == null) {
            return ApiResponses.success(Map.of("status", "EMPTY", "businessStatus", "EMPTY",
                    "deliveryStatus", "PENDING", "retryPending", false,
                    "nextScheduledAt", nextScheduledAt));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("status", run.getStatus());
        result.put("businessStatus", run.getStatus());
        result.put("deliveryStatus", run.getStartedAt() == null ? "PENDING" : "DELIVERED");
        result.put("retryPending", "FAILED".equals(run.getStatus()));
        result.put("runId", run.getId());
        result.put("businessDate", run.getBusinessDate());
        result.put("nextScheduledAt", nextScheduledAt);
        if (run.getErrorMessage() != null && !run.getErrorMessage().trim().isEmpty()) {
            result.put("errorMessage", run.getErrorMessage());
        }
        return ApiResponses.success(result);
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

    private String nextScheduledAt() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.toLocalDate().atTime(LocalTime.of(15, 30)).atZone(zone);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY
                || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next.toOffsetDateTime().toString();
    }
}
