package com.finscope.domain.task;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AsyncTask {
    /**
     * 主键 ID。
     */
    private String id;
    /**
     * 类型。
     */
    private String type;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 阶段。
     */
    private String phase;
    /**
     * 提示消息。
     */
    private String message;
    /**
     * 请求 URL。
     */
    private String requestUrl;
    /**
     * 文章 ID。
     */
    private Long articleId;
    /**
     * 错误信息。
     */
    private String errorMessage;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedAt;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 结束时间。
     */
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
