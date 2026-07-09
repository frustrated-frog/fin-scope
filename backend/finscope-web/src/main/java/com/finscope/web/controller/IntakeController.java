package com.finscope.web.controller;

import com.finscope.domain.intake.FetchBatch;
import com.finscope.domain.intake.IntakeCandidate;
import com.finscope.service.intake.IntakeService;
import com.finscope.web.request.UpdateIntakeCandidateStatusRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/intake")
public class IntakeController {
    @Resource
    private IntakeService intakeService;

    @GetMapping("/batches")
    public List<FetchBatch> batches() {
        return intakeService.latestBatches();
    }

    @GetMapping("/batches/{id}")
    public FetchBatch batch(@PathVariable Long id) {
        return intakeService.batch(id);
    }

    @GetMapping("/candidates")
    public List<IntakeCandidate> candidates(@RequestParam(defaultValue = "PENDING") String status,
                                            @RequestParam(required = false) Long batchId,
                                            @RequestParam(required = false) Long sourceId) {
        return intakeService.candidates(status, batchId, sourceId);
    }

    @GetMapping("/candidates/{id}")
    public IntakeCandidate candidate(@PathVariable Long id) {
        return intakeService.candidate(id);
    }

    @PostMapping("/candidates/{id}/status")
    public IntakeCandidate updateStatus(@PathVariable Long id,
                                        @RequestBody UpdateIntakeCandidateStatusRequest request) {
        return intakeService.updateHumanStatus(id, request.getHumanStatus(), request.getHumanNote());
    }

    @PostMapping("/candidates/{id}/promote")
    public Map<String, Object> promote(@PathVariable Long id) {
        IntakeCandidate candidate = intakeService.promote(id);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("candidateId", candidate.getId());
        response.put("articleId", candidate.getPromotedArticleId());
        response.put("status", candidate.getHumanStatus());
        return response;
    }
}
