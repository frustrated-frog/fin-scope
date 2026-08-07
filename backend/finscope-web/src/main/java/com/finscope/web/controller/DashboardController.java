package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.service.dashboard.DashboardService;
import com.finscope.service.cache.ViewSnapshotCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.Duration;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    @Resource
    private DashboardService dashboardService;
    @Resource
    private ViewSnapshotCacheService viewSnapshotCacheService;
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 查询首页仪表盘汇总数据。
     *
     * @return 仪表盘汇总 Map，包含文章、事件、任务等首页展示指标。
     */
    @GetMapping
    public ApiResponse<JsonNode> summary() {
        return ApiResponses.success(viewSnapshotCacheService.readOrLoad("dashboard", "summary", Duration.ofSeconds(30),
                () -> dashboardService.summary()));
    }

    /** 首页热点由雷达生产后预热，读取过程不会查询 SQLite。 */
    @GetMapping("/hotspots")
    public ApiResponse<JsonNode> hotspots() {
        return ApiResponses.success(viewSnapshotCacheService.read("dashboard", "hotspots").orElseGet(() -> objectMapper.createArrayNode()));
    }
}
