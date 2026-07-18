package com.finscope.web.controller;

import com.finscope.common.api.ApiResponse;
import com.finscope.web.response.ApiResponses;
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

    /**
     * 查询异步任务状态。
     *
     * @param taskId 异步任务 ID。
     * @return 任务视图，包含任务状态、进度和结果摘要。
     */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskView> get(@PathVariable String taskId) {
        return ApiResponses.success(urlIngestTaskService.get(taskId));
    }

    /**
     * 订阅异步任务进度。
     *
     * @param taskId 异步任务 ID。
     * @return SSE 连接，用于持续推送任务进度事件。
     */
    @GetMapping("/{taskId}/stream")
    public SseEmitter stream(@PathVariable String taskId) {
        return taskSseRegistry.subscribe(taskId, urlIngestTaskService.get(taskId));
    }
}
