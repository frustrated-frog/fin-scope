package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.domain.investmentobservation.ReactionCandidate;
import com.finscope.domain.investmentobservation.ReactionRefreshResult;
import com.finscope.service.investmentobservation.ReactionRegistrationService;
import com.finscope.service.investmentobservation.ReactionRefreshService;
import com.finscope.web.request.CreateReactionSampleRequest;
import com.finscope.web.request.ConfirmReactionSampleRequest;
import com.finscope.web.request.ArchiveReactionSampleRequest;
import com.finscope.web.response.ApiResponses;
import com.finscope.web.response.ReactionSampleResponse;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/investment-reactions")
public class InvestmentReactionController {
    @Resource
    private ReactionRegistrationService registration;
    @Resource
    private ReactionRefreshService refreshService;

    @GetMapping
    public ApiResponse<List<ReactionSampleResponse>> list(@RequestParam(required = false) ReactionSampleState state,
                                                         @RequestParam(defaultValue = "0") long afterId,
                                                         @RequestParam(defaultValue = "100") int limit) {
        return ApiResponses.success(registration.list(state, afterId, limit).stream()
                .map(ReactionSampleResponse::from).collect(Collectors.toList()));
    }

    @GetMapping("/candidates")
    public ApiResponse<List<ReactionCandidate>> candidates() {
        return ApiResponses.success(registration.candidates());
    }

    @GetMapping("/{id}")
    public ApiResponse<ReactionSampleResponse> detail(@PathVariable long id) {
        return ApiResponses.success(ReactionSampleResponse.from(registration.require(id)));
    }

    @PostMapping
    public ApiResponse<ReactionSampleResponse> create(@Valid @RequestBody CreateReactionSampleRequest request) {
        return ApiResponses.success(ReactionSampleResponse.from(registration.createDraft(request.getMajorEventId())));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ReactionSampleResponse> confirm(@PathVariable long id,
                                                       @Valid @RequestBody ConfirmReactionSampleRequest request) {
        return ApiResponses.success(ReactionSampleResponse.from(registration.confirm(id, request.toCommand())));
    }

    @PatchMapping("/{id}/archive")
    public ApiResponse<ReactionSampleResponse> archive(@PathVariable long id,
                                                       @Valid @RequestBody ArchiveReactionSampleRequest request) {
        return ApiResponses.success(ReactionSampleResponse.from(
                registration.archive(id, request.getRevision(), request.getArchived())));
    }

    @PostMapping("/{id}/refresh")
    public ApiResponse<ReactionSampleResponse> refresh(@PathVariable long id) {
        return ApiResponses.success(ReactionSampleResponse.from(refreshService.refresh(id)));
    }

    @PostMapping("/refresh")
    public ApiResponse<ReactionRefreshResult> refreshPending() {
        return ApiResponses.success(refreshService.refreshPending());
    }
}
