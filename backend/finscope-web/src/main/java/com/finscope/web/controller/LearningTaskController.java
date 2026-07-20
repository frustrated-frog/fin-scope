package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
import com.finscope.domain.research.LearningTask;
import com.finscope.service.research.LearningTaskService;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ApiResponse<List<LearningTask>> list() {
        return ApiResponses.success(learningTaskService.list());
    }

}
