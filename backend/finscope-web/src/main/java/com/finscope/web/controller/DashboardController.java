package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.service.dashboard.DashboardService;
import com.finscope.service.cache.ViewSnapshotCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;
import java.time.Duration;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    @Resource
    private DashboardService dashboardService;
    @Resource
    private ViewSnapshotCacheService snapshots;

    /**
     * 查询首页仪表盘汇总数据。
     *
     * @return 仪表盘汇总 Map，包含文章、事件、任务等首页展示指标。
     */
    @GetMapping
    public ApiResponse<JsonNode> summary() {
        return ApiResponses.success(snapshots.readOrLoad("dashboard", "summary", Duration.ofSeconds(30),
                () -> dashboardService.summary()));
    }
}
