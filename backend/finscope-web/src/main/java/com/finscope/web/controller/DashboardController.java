package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.service.dashboard.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    @Resource
    private DashboardService dashboardService;

    /**
     * 查询首页仪表盘汇总数据。
     *
     * @return 仪表盘汇总 Map，包含文章、事件、任务等首页展示指标。
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponses.success(dashboardService.summary());
    }
}
