package com.finscope.service.task;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/** 与传输层无关的异步任务进度事件。数据库 TaskView 始终是最终事实来源。 */
@Data
public final class TaskProgressEvent {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final String eventId;
    private final String taskId;
    private final String type;
    private final String status;
    private final String phase;
    private final String message;
    private final String errorMessage;
    private final Long articleId;
    private final LocalDateTime occurredAt;

    private TaskProgressEvent(String type, TaskView task) {
        this.eventId = String.valueOf(SEQUENCE.incrementAndGet());
        this.taskId = task.getTaskId();
        this.type = type;
        this.status = task.getStatus();
        this.phase = task.getPhase();
        this.message = task.getMessage();
        this.errorMessage = task.getErrorMessage();
        this.articleId = task.getArticleId();
        this.occurredAt = LocalDateTime.now();
    }

    public static TaskProgressEvent snapshot(TaskView task) {
        return new TaskProgressEvent("SNAPSHOT", task);
    }
    public static TaskProgressEvent phase(TaskView task) { return new TaskProgressEvent("PHASE", task); }
    public static TaskProgressEvent done(TaskView task) { return new TaskProgressEvent("DONE", task); }
    public static TaskProgressEvent error(TaskView task) { return new TaskProgressEvent("ERROR", task); }

}
