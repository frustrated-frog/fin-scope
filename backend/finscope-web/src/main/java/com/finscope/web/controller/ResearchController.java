package com.finscope.web.controller;

import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchRunPlan;
import com.finscope.service.agent.AgentRunService;
import com.finscope.service.research.ResearchRunPlanService;
import com.finscope.service.research.ResearchService;
import com.finscope.service.research.report.ResearchReportService;
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
    @Resource
    private ResearchRunPlanService researchRunPlanService;
    @Resource
    private ResearchReportService researchReportService;

    /**
     * 创建研究运行计划。
     *
     * @param request 研究运行创建请求，包含命题 ID、运行日期、主题编码、每主题来源上限和是否包含禁用来源。
     * @return 创建后的研究运行响应，包含运行计划摘要。
     */
    @PostMapping
    public ResearchRunResponse create(@RequestBody CreateResearchRunRequest request) {
        ResearchRunPlan plan = researchService.createRun(
                request.getThesisId(),
                request.getRunDate(),
                request.getThemeCodes(),
                request.getMaxSourcesPerTheme(),
                request.getIncludeDisabled());
        return ResearchRunResponse.of(plan);
    }

    /**
     * 查询研究运行列表。
     *
     * @return 研究运行列表。
     */
    @GetMapping
    public List<ResearchRun> list() {
        return researchService.listRuns();
    }

    /**
     * 查询研究运行详情。
     *
     * @param id 研究运行 ID。
     * @return 研究运行详情，包含运行记录、计划来源、计划步骤和关联 Agent 运行。
     */
    @GetMapping("/{id}")
    public ResearchRunDetailResponse detail(@PathVariable Long id) {
        return new ResearchRunDetailResponse(
                researchService.detail(id),
                researchService.plannedSources(id),
                researchRunPlanService.findByRunId(id),
                agentRunService.findByResearchRunId(id));
    }

    @GetMapping("/{id}/report")
    public ResearchReport report(@PathVariable Long id) {
        return researchReportService.detailByRunId(id);
    }

    @PostMapping("/{id}/report/regenerate")
    public ResearchReport regenerateReport(@PathVariable Long id) {
        return researchService.regenerateReport(id);
    }
}
