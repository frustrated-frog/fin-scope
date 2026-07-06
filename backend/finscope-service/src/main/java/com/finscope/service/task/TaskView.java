package com.finscope.service.task;

import com.finscope.domain.task.AsyncTask;
import com.finscope.service.article.ArticleCardView;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskView {
    private String taskId;
    private String type;
    private String status;
    private String phase;
    private String message;
    private String errorMessage;
    private Long articleId;
    private ArticleCardView article;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public static TaskView from(AsyncTask task, ArticleCardView article) {
        TaskView view = new TaskView();
        view.setTaskId(task.getId());
        view.setType(task.getType());
        view.setStatus(task.getStatus());
        view.setPhase(task.getPhase());
        view.setMessage(task.getMessage());
        view.setErrorMessage(task.getErrorMessage());
        view.setArticleId(task.getArticleId());
        view.setArticle(article);
        view.setCreatedAt(task.getCreatedAt());
        view.setUpdatedAt(task.getUpdatedAt());
        view.setStartedAt(task.getStartedAt());
        view.setEndedAt(task.getEndedAt());
        return view;
    }
}
