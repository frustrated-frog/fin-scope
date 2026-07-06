package com.finscope.web.controller;

import com.finscope.service.task.TaskView;
import com.finscope.service.task.UrlIngestTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    @Resource
    private UrlIngestTaskService urlIngestTaskService;

    @GetMapping("/{taskId}")
    public TaskView get(@PathVariable String taskId) {
        return urlIngestTaskService.get(taskId);
    }
}
