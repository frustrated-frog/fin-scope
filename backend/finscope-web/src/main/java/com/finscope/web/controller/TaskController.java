package com.finscope.web.controller;

import com.finscope.service.task.TaskView;
import com.finscope.service.task.UrlIngestTaskService;
import com.finscope.web.sse.TaskSseRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    @Resource
    private UrlIngestTaskService urlIngestTaskService;
    @Resource
    private TaskSseRegistry taskSseRegistry;

    @GetMapping("/{taskId}")
    public TaskView get(@PathVariable String taskId) {
        return urlIngestTaskService.get(taskId);
    }

    @GetMapping("/{taskId}/stream")
    public SseEmitter stream(@PathVariable String taskId) {
        return taskSseRegistry.subscribe(taskId, urlIngestTaskService.get(taskId));
    }
}
