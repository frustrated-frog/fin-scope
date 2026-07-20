package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
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
    @Resource
    private IntakePromotionTaskService intakePromotionTaskService;

    /**
     * 查询最近的信息源抓取批次。
     *
     * @return 最近抓取批次列表。
     */
    @GetMapping("/batches")
    public ApiResponse<List<FetchBatch>> batches() {
        return ApiResponses.success(intakeService.latestBatches());
    }

    /**
     * 查询抓取批次详情。
     *
     * @param id 抓取批次 ID。
     * @return 指定抓取批次详情。
     */
    @GetMapping("/batches/{id}")
    public ApiResponse<FetchBatch> batch(@PathVariable Long id) {
        return ApiResponses.success(intakeService.batch(id));
    }

    /**
     * 查询摄入候选列表。
     *
     * @param status 候选状态过滤条件，默认 PENDING。
     * @param batchId 抓取批次 ID 过滤条件，可为空。
     * @param sourceId 信息源 ID 过滤条件，可为空。
     * @return 符合条件的摄入候选列表。
     */
    @GetMapping("/candidates")
    public ApiResponse<List<IntakeCandidate>> candidates(@RequestParam(defaultValue = "PENDING") String status,
                                            @RequestParam(required = false) Long batchId,
                                            @RequestParam(required = false) Long sourceId) {
        return ApiResponses.success(intakeService.candidates(status, batchId, sourceId));
    }

    /**
     * 查询摄入候选详情。
     *
     * @param id 摄入候选 ID。
     * @return 指定摄入候选详情。
     */
    @GetMapping("/candidates/{id}")
    public ApiResponse<IntakeCandidate> candidate(@PathVariable Long id) {
        return ApiResponses.success(intakeService.candidate(id));
    }

    /**
     * 更新摄入候选的人审状态。
     *
     * @param id 摄入候选 ID。
     * @param request 状态更新请求，包含人审状态和人审备注。
     * @return 更新后的摄入候选。
     */
    @PostMapping("/candidates/{id}/status")
    public ApiResponse<IntakeCandidate> updateStatus(@PathVariable Long id,
                                        @RequestBody UpdateIntakeCandidateStatusRequest request) {
        return ApiResponses.success(intakeService.updateHumanStatus(id, request.getHumanStatus(), request.getHumanNote()));
    }

    /**
     * 同步提升摄入候选为正式文章。
     *
     * @param id 摄入候选 ID。
     * @return 提升结果，包含生成文章、事件或跳过原因等信息。
     */
    @PostMapping("/candidates/{id}/promote")
    public ApiResponse<PromoteIntakeCandidateResponse> promote(@PathVariable Long id) {
        return ApiResponses.success(intakeService.promote(id));
    }

    /**
     * 异步提升摄入候选为正式文章。
     *
     * @param id 摄入候选 ID。
     * @return 已创建的异步任务视图，用于查询提升进度。
     */
    @PostMapping("/candidates/{id}/promote-async")
    public ApiResponse<TaskView> promoteAsync(@PathVariable Long id) { return ApiResponses.success(intakePromotionTaskService.submit(id)); }
}
