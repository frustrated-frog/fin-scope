package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.domain.agent.AgentRun;
import com.finscope.service.agent.AgentRunService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunController {
    @Resource
    private AgentRunService agentRunService;

    /**
     * 查询最近的 Agent 运行记录。
     *
     * @return 最近 50 条 AgentRun 记录，按服务层默认顺序返回。
     */
    @GetMapping
    public ApiResponse<List<AgentRun>> list() {
        return ApiResponses.success(agentRunService.latest(50));
    }
}
