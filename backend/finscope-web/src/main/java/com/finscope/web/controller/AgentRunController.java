package com.finscope.web.controller;

import com.finscope.domain.agent.AgentRun;
import com.finscope.service.agent.AgentRunService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunController {
    private final AgentRunService agentRunService;

    public AgentRunController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @GetMapping
    public List<AgentRun> list() {
        return agentRunService.latest(50);
    }
}
