package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchRunPlan;
import com.finscope.domain.research.evaluation.ResearchEvaluation;
import com.finscope.domain.research.runtime.ResearchRuntimeView;
import com.finscope.domain.research.mission.ResearchMissionView;
import com.finscope.service.agent.AgentRunService;
import com.finscope.service.research.evaluation.ResearchEvaluationService;
import com.finscope.service.research.ResearchRunPlanService;
import com.finscope.service.research.ResearchService;
import com.finscope.service.research.report.ResearchReportService;
import com.finscope.service.research.runtime.ResearchRuntimeService;
import com.finscope.service.research.mission.ResearchMissionService;
import com.finscope.service.research.agent.ResearchAgentTraceService;
import com.finscope.web.request.CreateResearchRunRequest;
import com.finscope.web.response.ResearchRunDetailResponse;
import com.finscope.web.response.ResearchRunResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    @Resource
    private ResearchRuntimeService researchRuntimeService;
    @Resource
    private ResearchEvaluationService researchEvaluationService;
    @Resource
    private ResearchMissionService researchMissionService;
    @Resource
    private ResearchAgentTraceService researchAgentTraceService;

    /**
     * 创建研究运行计划。
     *
     * @param request 研究运行创建请求，包含命题 ID、运行日期、主题编码和研究模式。
     * @return 创建后的研究运行响应，包含运行计划摘要。
     */
    @PostMapping
    public ApiResponse<ResearchRunResponse> create(@RequestBody CreateResearchRunRequest request) {
        ResearchRunPlan plan = researchService.createRun(
                request.getThesisId(),
                request.getRunDate(),
                request.getThemeCodes(),
                request.getMode());
        return ApiResponses.success(ResearchRunResponse.of(plan));
    }

    /**
     * 查询研究运行列表。
     *
     * @return 研究运行列表。
     */
    @GetMapping
    public ApiResponse<List<ResearchRun>> list() {
        return ApiResponses.success(researchService.listRuns());
    }

    /**
     * 删除研究运行。
     *
     * @param id 研究运行 ID。
     * @return 204 No Content 响应，表示删除成功且无响应体。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        researchService.deleteRun(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查询研究运行详情。
     *
     * @param id 研究运行 ID。
     * @return 研究运行详情，包含运行记录、计划来源、计划步骤和关联 Agent 运行。
     */
    @GetMapping("/{id}")
    public ApiResponse<ResearchRunDetailResponse> detail(@PathVariable Long id) {
        ResearchRuntimeView runtime = researchRuntimeService.findCheckpoint(id).isPresent()
                ? researchRuntimeService.view(id) : null;
        return ApiResponses.success(new ResearchRunDetailResponse(
                researchService.detail(id),
                researchService.plannedSources(id),
                researchRunPlanService.findByRunId(id),
                agentRunService.findByResearchRunId(id),
                researchReportService.findByRunId(id).orElse(null),
                runtime,
                researchEvaluationService.findLatest(id).orElse(null),
                researchMissionService.findDetail(id).orElse(null),
                researchAgentTraceService.findTrace(id).orElse(null)));
    }

    /**
     * 查询研究运行的任务地图。
     *
     * @param id 研究运行 ID。
     * @return 研究任务地图视图，包含研究任务的分解和进度。
     */
    @GetMapping("/{id}/mission")
    public ApiResponse<ResearchMissionView> mission(@PathVariable Long id) {
        researchService.detail(id);
        return ApiResponses.success(researchMissionService.detail(id));
    }

    /**
     * 查询研究运行的研究报告。
     *
     * @param id 研究运行 ID。
     * @return 该研究运行生成的研究报告。
     */
    @GetMapping("/{id}/report")
    public ApiResponse<ResearchReport> report(@PathVariable Long id) {
        return ApiResponses.success(researchReportService.detailByRunId(id));
    }

    /**
     * 重新生成研究报告。
     *
     * @param id 研究运行 ID。
     * @return 重新生成后的研究报告。
     */
    @PostMapping("/{id}/report/regenerate")
    public ApiResponse<ResearchReport> regenerateReport(@PathVariable Long id) {
        return ApiResponses.success(researchService.regenerateReport(id));
    }

    /**
     * 查询研究运行时视图。
     *
     * @param id 研究运行 ID。
     * @return 研究运行时视图，包含节点执行状态和检查点信息。
     */
    @GetMapping("/{id}/runtime")
    public ApiResponse<ResearchRuntimeView> runtime(@PathVariable Long id) {
        return ApiResponses.success(researchRuntimeService.view(id));
    }

    /**
     * 恢复中断的研究运行。
     *
     * @param id 研究运行 ID。
     * @return 恢复后的研究运行响应。
     */
    @PostMapping("/{id}/resume")
    public ApiResponse<ResearchRunResponse> resume(@PathVariable Long id) {
        return ApiResponses.success(ResearchRunResponse.of(researchService.resume(id)));
    }

    /**
     * 对研究运行发起质量评测。
     *
     * @param id 研究运行 ID。
     * @return 本次研究评测结果。
     */
    @PostMapping("/{id}/evaluations")
    public ApiResponse<ResearchEvaluation> evaluate(@PathVariable Long id) {
        return ApiResponses.success(researchEvaluationService.evaluate(id));
    }

    /**
     * 查询研究运行的最新评测结果。
     *
     * @param id 研究运行 ID。
     * @return 最新研究评测结果；若不存在则返回 null。
     */
    @GetMapping("/{id}/evaluations/latest")
    public ApiResponse<ResearchEvaluation> latestEvaluation(@PathVariable Long id) {
        return ApiResponses.success(researchEvaluationService.findLatest(id).orElse(null));
    }
}
