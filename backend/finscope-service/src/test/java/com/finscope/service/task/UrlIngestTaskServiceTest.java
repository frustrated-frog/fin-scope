package com.finscope.service.task;

import com.finscope.dao.task.TaskRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.article.ArticleIngestResult;
import com.finscope.domain.request.IngestUrlRequest;
import com.finscope.domain.task.TaskPhase;
import com.finscope.service.article.ArticleCategoryPolicy;
import com.finscope.service.article.UrlIngestService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UrlIngestTaskServiceTest {
    @Test
    void persistsEachTransitionBeforePublishingAndCompletesAfterArticleExists() {
        TaskRepository repository = mock(TaskRepository.class);
        UrlIngestService ingestService = mock(UrlIngestService.class);
        ArticleCategoryPolicy categoryPolicy = mock(ArticleCategoryPolicy.class);
        TaskProgressPublisher publisher = mock(TaskProgressPublisher.class);
        when(categoryPolicy.normalize(any())).thenReturn("市场");
        Article article = new Article();
        article.setId(77L);
        doAnswer(invocation -> {
            java.util.function.Consumer<TaskPhase> phases = invocation.getArgument(4);
            phases.accept(TaskPhase.PARSING);
            phases.accept(TaskPhase.LLM);
            phases.accept(TaskPhase.PERSISTING);
            return new ArticleIngestResult(article, null);
        }).when(ingestService).ingest(anyString(), any(), any(), any(), any());

        UrlIngestTaskService service = new UrlIngestTaskService();
        ReflectionTestUtils.setField(service, "taskRepository", repository);
        ReflectionTestUtils.setField(service, "urlIngestService", ingestService);
        ReflectionTestUtils.setField(service, "articleCategoryPolicy", categoryPolicy);
        ReflectionTestUtils.setField(service, "taskProgressPublisher", publisher);
        ReflectionTestUtils.setField(service, "ingestTaskExecutor", (Executor) Runnable::run);
        IngestUrlRequest request = new IngestUrlRequest();
        request.setUrl("https://example.com/article");

        TaskView submitted = service.submit(request);

        InOrder order = inOrder(repository, publisher);
        order.verify(repository).markRunning(submitted.getTaskId(), TaskPhase.FETCHING, "正在抓取网页");
        order.verify(publisher).publish(anyString(), any(TaskProgressEvent.class));
        order.verify(repository).updatePhase(submitted.getTaskId(), TaskPhase.PARSING, "正在解析正文");
        order.verify(publisher).publish(anyString(), any(TaskProgressEvent.class));
        order.verify(repository).complete(submitted.getTaskId(), 77L, "情报卡片已生成，已加入文章列表");
        order.verify(publisher).publish(anyString(), any(TaskProgressEvent.class));
        order.verify(publisher).complete(submitted.getTaskId());
    }
}
