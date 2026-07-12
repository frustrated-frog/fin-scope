package com.finscope.service.task;

/** 任务进度发布端口；SSE 只是其 Web 层适配器，发布失败不得反向破坏任务。 */
public interface TaskProgressPublisher {
    void publish(String taskId, TaskProgressEvent event);
    void complete(String taskId);
}
