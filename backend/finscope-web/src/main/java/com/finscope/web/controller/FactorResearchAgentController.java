package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.factorresearch.FactorResearchAgentRun;
import com.finscope.service.factorresearch.FactorResearchAgentService;
import com.finscope.web.request.factorresearch.CreateFactorResearchAgentRunRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/factor-research/agent-runs")
public class FactorResearchAgentController {
    private final FactorResearchAgentService service;
    public FactorResearchAgentController(FactorResearchAgentService service) { this.service = service; }

    /**
     * 创建因子研究 Agent 运行计划。
     *
     * @param request 研究 Agent 创建请求，包含数据集 ID、因子、研究草稿 ID 和研究问题。
     * @return 201 Created 响应，响应体为新创建的研究 Agent 运行计划。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FactorResearchAgentRun>> create(@RequestBody(required = false) CreateFactorResearchAgentRunRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "研究 Agent 请求不能为空");
        }
        FactorResearchAgentRun value = service.createPlan(request.getDatasetId(), request.factor(), request.getResearchDraftId(), request.getQuestion());
        return ResponseEntity.created(URI.create("/api/factor-research/agent-runs/" + value.getId())).body(ApiResponses.success(value));
    }

    /**
     * 批准并执行因子研究 Agent 运行计划。
     *
     * @param id 研究 Agent 运行计划 ID。
     * @return 批准并执行后的研究 Agent 运行。
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<FactorResearchAgentRun> approve(@PathVariable Long id) {
        return ApiResponses.success(service.approveAndRun(id));
    }

    /**
     * 查询因子研究 Agent 运行详情。
     *
     * @param id 研究 Agent 运行计划 ID。
     * @return 指定研究 Agent 运行详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<FactorResearchAgentRun> get(@PathVariable Long id) {
        return ApiResponses.success(service.get(id));
    }
}
