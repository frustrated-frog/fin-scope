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
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Task not found: " + taskId));
        ArticleCardView article = null;
        if (task.getArticleId() != null) {
            article = articleQueryService.detail(task.getArticleId());
        }
        return TaskView.from(task, article);
    }

    private void runTask(String taskId, IngestUrlRequest request) {
        try {
            taskRepository.markRunning(taskId, TaskPhase.FETCHING, messageFor(TaskPhase.FETCHING));
            ArticleIngestResult result = urlIngestService.ingest(
                    request.getUrl(),
                    request.getSourceName(),
                    request.getTags(),
                    request.getCategory(),
                    phase -> taskRepository.updatePhase(taskId, phase, messageFor(phase)));
            taskRepository.complete(taskId, result.getArticle().getId(), "情报卡片已生成，已加入文章列表");
        } catch (Exception ex) {
            String message = safeMessage(ex);
            taskRepository.fail(taskId, message);
            log.warn("链接入库任务失败 taskId={} message={}", taskId, message, ex);
        }
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
