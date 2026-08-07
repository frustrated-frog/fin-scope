package com.finscope.service.task;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.task.TaskRepository;
import com.finscope.domain.article.ArticleIngestResult;
import com.finscope.domain.request.IngestUrlRequest;
import com.finscope.domain.task.AsyncTask;
import com.finscope.domain.task.TaskPhase;
import com.finscope.service.article.ArticleCardView;
import com.finscope.service.article.ArticleCategoryPolicy;
import com.finscope.service.article.ArticleQueryService;
import com.finscope.service.article.UrlIngestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.Executor;
import com.finscope.common.exception.BizErrorCode;

@Service
@Slf4j
public class UrlIngestTaskService {
    private static final String TASK_TYPE = "ARTICLE_URL_INGEST";

    @Resource
    private TaskRepository taskRepository;
    @Resource
    private UrlIngestService urlIngestService;
    @Resource
    private ArticleQueryService articleQueryService;
    @Resource
    private ArticleCategoryPolicy articleCategoryPolicy;
    @Resource
    private TaskProgressPublisher taskProgressPublisher;
    @Resource(name = "ingestTaskExecutor")
    private Executor ingestTaskExecutor;

    public TaskView submit(IngestUrlRequest request) {
        urlIngestService.validateUrl(request.getUrl());
        request.setCategory(articleCategoryPolicy.normalize(request.getCategory()));
        String taskId = UUID.randomUUID().toString();
        AsyncTask task = AsyncTask.queued(taskId, TASK_TYPE, request.getUrl());
        taskRepository.create(task);
        ingestTaskExecutor.execute(() -> runTask(taskId, request));
        return TaskView.from(task, null);
    }

    public TaskView get(String taskId) {
        AsyncTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(BizErrorCode.ASYNC_TASK_NOT_FOUND, taskId));
        ArticleCardView article = null;
        if (task.getArticleId() != null) {
            article = articleQueryService.detail(task.getArticleId());
        }
        return TaskView.from(task, article);
    }

    private void runTask(String taskId, IngestUrlRequest request) {
        try {
            persistAndPublishPhase(taskId, TaskPhase.FETCHING);
            ArticleIngestResult result = urlIngestService.ingest(
                    request.getUrl(),
                    request.getSourceName(),
                    request.getTags(),
                    request.getCategory(),
                    phase -> persistAndPublishPhase(taskId, phase));
            taskRepository.complete(taskId, result.getArticle().getId(), "情报卡片已生成，已加入文章列表");
            publishTerminal(taskId, "COMPLETED", TaskPhase.COMPLETED.name(),
                    "情报卡片已生成，已加入文章列表", null, result.getArticle().getId());
        } catch (Exception ex) {
            String message = safeMessage(ex);
            taskRepository.fail(taskId, message);
            publishTerminal(taskId, "FAILED", TaskPhase.FAILED.name(), message, message, null);
            log.warn("链接入库任务失败 taskId={} message={}", taskId, message, ex);
        }
    }

    private void persistAndPublishPhase(String taskId, TaskPhase phase) {
        String message = messageFor(phase);
        if (phase == TaskPhase.FETCHING) {
            taskRepository.markRunning(taskId, phase, message);
        } else {
            taskRepository.updatePhase(taskId, phase, message);
        }
        TaskView view = taskState(taskId, "RUNNING", phase.name(), message, null, null);
        safePublish(taskId, TaskProgressEvent.phase(view), false);
    }

    private void publishTerminal(String taskId,
                                 String status,
                                 String phase,
                                 String message,
                                 String errorMessage,
                                 Long articleId) {
        TaskView view = taskState(taskId, status, phase, message, errorMessage, articleId);
        safePublish(taskId, "COMPLETED".equals(status)
                ? TaskProgressEvent.done(view) : TaskProgressEvent.error(view), true);
    }

    private void safePublish(String taskId, TaskProgressEvent event, boolean terminal) {
        try {
            taskProgressPublisher.publish(taskId, event);
        } catch (RuntimeException ex) {
            log.debug("任务状态已持久化但实时进度发布失败 taskId={} message={}", taskId, ex.getMessage());
        } finally {
            if (terminal) {
                try { taskProgressPublisher.complete(taskId); }
                catch (RuntimeException ex) { log.debug("任务 SSE 关闭失败 taskId={}", taskId, ex); }
            }
        }
    }

    private TaskView taskState(String taskId,
                               String status,
                               String phase,
                               String message,
                               String errorMessage,
                               Long articleId) {
        TaskView view = new TaskView();
        view.setTaskId(taskId);
        view.setType(TASK_TYPE);
        view.setStatus(status);
        view.setPhase(phase);
        view.setMessage(message);
        view.setErrorMessage(errorMessage);
        view.setArticleId(articleId);
        return view;
    }

    private String messageFor(TaskPhase phase) {
        switch (phase) {
            case FETCHING:
                return "正在抓取网页";
            case PARSING:
                return "正在解析正文";
            case LLM:
                return "正在生成情报卡片";
            case PERSISTING:
                return "正在写入文章库";
            default:
                return "正在处理";
        }
    }

    private String safeMessage(Exception ex) {
        if (ex.getMessage() == null || ex.getMessage().trim().isEmpty()) {
            return "URL 解析失败";
        }
        return ex.getMessage();
    }
}
