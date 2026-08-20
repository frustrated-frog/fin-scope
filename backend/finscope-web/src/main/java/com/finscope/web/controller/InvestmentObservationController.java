package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.domain.investmentobservation.InvestmentObservation;
import com.finscope.domain.investmentobservation.InvestmentObservationDetail;
import com.finscope.domain.investmentobservation.InvestmentObservationRefreshResult;
import com.finscope.domain.investmentobservation.InvestmentObservationWorkspace;
import com.finscope.service.investmentobservation.InvestmentObservationService;
import com.finscope.web.request.ArchiveInvestmentObservationRequest;
import com.finscope.web.request.UpdateInvestmentObservationStateRequest;
import com.finscope.web.response.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/investment-observations")
public class InvestmentObservationController {
    @Resource
    private InvestmentObservationService service;

    @GetMapping
    public ApiResponse<InvestmentObservationWorkspace> workspace() {
        return ApiResponses.success(service.workspace());
    }

    @PostMapping("/refresh")
    public ApiResponse<InvestmentObservationRefreshResult> refresh() {
        return ApiResponses.success(service.refresh());
    }

    @GetMapping("/{id}")
    public ApiResponse<InvestmentObservationDetail> detail(@PathVariable Long id) {
        return ApiResponses.success(service.detail(id));
    }

    @PatchMapping("/{id}/state")
    public ApiResponse<InvestmentObservation> updateState(@PathVariable Long id,
                                                          @RequestBody UpdateInvestmentObservationStateRequest request) {
        if (request.getDisposition() == null) {
            throw new BusinessException(BizErrorCode.INVESTMENT_OBSERVATION_DISPOSITION_REQUIRED);
        }
        return ApiResponses.success(service.updateDisposition(id, request.getDisposition(), revision(request.getRevision())));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<InvestmentObservation> archive(@PathVariable Long id,
                                                      @RequestBody ArchiveInvestmentObservationRequest request) {
        return ApiResponses.success(service.archive(id, revision(request.getRevision()), request.getReason()));
    }

    private int revision(Integer revision) {
        if (revision == null || revision < 0) {
            throw new BusinessException(BizErrorCode.INVESTMENT_OBSERVATION_REVISION_REQUIRED);
        }
        return revision;
    }
}
