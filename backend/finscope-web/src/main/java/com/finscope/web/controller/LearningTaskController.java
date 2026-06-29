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

import java.util.List;

@RestController
@RequestMapping("/api/learning-tasks")
public class LearningTaskController {
    private final LearningTaskService learningTaskService;

    public LearningTaskController(LearningTaskService learningTaskService) {
        this.learningTaskService = learningTaskService;
    }

    @GetMapping
    public List<LearningTask> list() {
        return learningTaskService.list();
    }

    @PostMapping("/{id}/status")
    public LearningTask updateStatus(@PathVariable Long id, @RequestBody UpdateLearningTaskStatusRequest request) {
        return learningTaskService.updateStatus(id, request.getStatus());
    }
}
