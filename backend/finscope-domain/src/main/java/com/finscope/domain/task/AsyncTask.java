package com.finscope.domain.task;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AsyncTask {
    private String id;
    private String type;
    private String status;
    private String phase;
    private String message;
    private String requestUrl;
    private Long articleId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public static AsyncTask queued(String id, String type, String requestUrl) {
        LocalDateTime now = LocalDateTime.now();
        AsyncTask task = new AsyncTask();
        task.setId(id);
        task.setType(type);
        task.setStatus(TaskStatus.QUEUED.name());
        task.setPhase(TaskPhase.QUEUED.name());
        task.setMessage("等待开始");
        task.setRequestUrl(requestUrl);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
