package com.finscope.web.controller;

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

    @PostMapping
    public ResponseEntity<FactorResearchAgentRun> create(@RequestBody(required = false) CreateFactorResearchAgentRunRequest request) {
        if (request == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "研究 Agent 请求不能为空");
        FactorResearchAgentRun value = service.createPlan(request.getDatasetId(), request.factor(), request.getResearchDraftId(), request.getQuestion());
        return ResponseEntity.created(URI.create("/api/factor-research/agent-runs/" + value.getId())).body(value);
    }

    @PostMapping("/{id}/approve")
    public FactorResearchAgentRun approve(@PathVariable Long id) { return service.approveAndRun(id); }

    @GetMapping("/{id}")
    public FactorResearchAgentRun get(@PathVariable Long id) { return service.get(id); }
}
