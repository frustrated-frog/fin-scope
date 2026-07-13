package com.finscope.web.controller;

import com.finscope.domain.research.LearningTask;
import com.finscope.service.research.LearningTaskService;
import com.finscope.web.request.UpdateLearningTaskStatusRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/learning-tasks")
public class LearningTaskController {
    @Resource
    private LearningTaskService learningTaskService;

    /**
     * 查询学习任务列表。
     *
     * @return 学习任务列表。
     */
    @GetMapping
    public List<LearningTask> list() {
        return learningTaskService.list();
    }

    /**
     * 更新学习任务状态。
     *
     * @param id 学习任务 ID。
     * @param request 状态更新请求，包含目标任务状态。
     * @return 更新后的学习任务。
     */
    @PostMapping("/{id}/status")
    public LearningTask updateStatus(@PathVariable Long id, @RequestBody UpdateLearningTaskStatusRequest request) {
        return learningTaskService.updateStatus(id, request.getStatus());
    }
}
