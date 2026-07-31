package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.investmentrecognition.InvestmentRecognitionCandidate;
import com.finscope.domain.investmentrecognition.InvestmentRecognitionRun;
import com.finscope.service.investmentrecognition.InvestmentRecognitionAgentService;
import com.finscope.service.investmentrecognition.InvestmentRecognitionService;
import com.finscope.web.request.knowledge.InvestmentRecognitionActionRequest;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge/investment-recognitions")
public class InvestmentRecognitionController {
    private final InvestmentRecognitionAgentService agent;
    private final InvestmentRecognitionService recognitions;

    public InvestmentRecognitionController(InvestmentRecognitionAgentService agent,
                                           InvestmentRecognitionService recognitions) {
        this.agent = agent;
        this.recognitions = recognitions;
    }

    @GetMapping
    public ApiResponse<List<InvestmentRecognitionCandidate>> list(
            @RequestParam(required = false) String status) {
        return ApiResponses.success(recognitions.list(status));
    }

    @PostMapping("/run")
    public ApiResponse<InvestmentRecognitionRun> run() {
        return ApiResponses.success(agent.run());
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<InvestmentRecognitionCandidate> accept(
            @PathVariable long id, @RequestBody InvestmentRecognitionActionRequest request) {
        return ApiResponses.success(recognitions.accept(id, revision(request)));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<InvestmentRecognitionCandidate> updateStatus(
            @PathVariable long id, @RequestBody InvestmentRecognitionActionRequest request) {
        if (request == null || request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_MISSING, "缺少 status");
        }
        return ApiResponses.success(recognitions.updateStatus(id, request.getStatus(), revision(request)));
    }

    private long revision(InvestmentRecognitionActionRequest request) {
        if (request == null || request.getExpectedRevision() == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_MISSING, "缺少 expectedRevision");
        }
        return request.getExpectedRevision();
    }
}
