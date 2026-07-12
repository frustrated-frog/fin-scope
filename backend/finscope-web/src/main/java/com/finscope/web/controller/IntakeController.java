package com.finscope.web.controller;

import com.finscope.domain.intake.FetchBatch;
import com.finscope.domain.intake.IntakeCandidate;
import com.finscope.domain.intake.PromoteIntakeCandidateResponse;
import com.finscope.service.intake.IntakeService;
import com.finscope.service.intake.IntakePromotionTaskService;
import com.finscope.service.task.TaskView;
import com.finscope.web.request.UpdateIntakeCandidateStatusRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/intake")
public class IntakeController {
    @Resource
    private IntakeService intakeService;
    @Resource private IntakePromotionTaskService intakePromotionTaskService;

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
    public PromoteIntakeCandidateResponse promote(@PathVariable Long id) {
        return intakeService.promote(id);
    }

    @PostMapping("/candidates/{id}/promote-async")
    public TaskView promoteAsync(@PathVariable Long id) { return intakePromotionTaskService.submit(id); }
}
