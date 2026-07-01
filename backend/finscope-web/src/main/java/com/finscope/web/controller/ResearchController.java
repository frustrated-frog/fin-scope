package com.finscope.web.controller;

import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunPlan;
import com.finscope.service.agent.AgentRunService;
import com.finscope.service.research.ResearchService;
import com.finscope.web.request.CreateResearchRunRequest;
import com.finscope.web.response.ResearchRunDetailResponse;
import com.finscope.web.response.ResearchRunResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/research/runs")
public class ResearchController {
    @Resource
    private ResearchService researchService;
    @Resource
    private AgentRunService agentRunService;

    @PostMapping
    public ResearchRunResponse create(@RequestBody CreateResearchRunRequest request) {
        ResearchRunPlan plan = researchService.createRun(
                request.getRunDate(),
                request.getThemeCodes(),
                request.getMaxSourcesPerTheme(),
                request.getIncludeDisabled());
        return ResearchRunResponse.of(plan);
    }

    @GetMapping
    public List<ResearchRun> list() {
        return researchService.listRuns();
    }

    @GetMapping("/{id}")
    public ResearchRunDetailResponse detail(@PathVariable Long id) {
        return new ResearchRunDetailResponse(
                researchService.detail(id),
                researchService.plannedSources(id),
                agentRunService.findByResearchRunId(id));
    }
}
