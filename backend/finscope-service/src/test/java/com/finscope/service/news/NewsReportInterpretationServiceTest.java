package com.finscope.service.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.news.NewsInterpretationStatus;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.news.NewsReportInterpretationRepository;
import com.finscope.dao.news.NewsReportRepository;
import com.finscope.dao.news.NewsWindowSchemaMigrator;
import com.finscope.domain.news.NewsReport;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NewsReportInterpretationServiceTest {
    private static final String ID = "CLS:1";
    private static final String RESULT = """
            {"facts":["公司签订框架协议"],"whyItMatters":"履约仍取决于后续订单", "unknowns":["实际订单金额"],"nextChecks":["正式订单公告"]}
            """;
    @TempDir
    Path directory;
    private final List<Runnable> jobs = new ArrayList<>();
    private NewsReportRepository reports;
    private NewsReportInterpretationRepository interpretations;
    private NewsWorkbenchCapabilities capabilities;
    private LlmChatClient llm;
    private NewsReportInterpretationService service;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("news.db"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        ObjectMapper json = new ObjectMapper();
        NewsWindowSchemaMigrator migration = new NewsWindowSchemaMigrator();
        ReflectionTestUtils.setField(migration, "jdbc", jdbc);
        ReflectionTestUtils.setField(migration, "transactionManager", new DataSourceTransactionManager(source));
        migration.afterPropertiesSet();
        reports = new NewsReportRepository();
        ReflectionTestUtils.setField(reports, "jdbc", jdbc);
        ReflectionTestUtils.setField(reports, "mapper", json);
        interpretations = new NewsReportInterpretationRepository();
        ReflectionTestUtils.setField(interpretations, "jdbc", jdbc);
        ReflectionTestUtils.setField(interpretations, "mapper", json);
        capabilities = mock(NewsWorkbenchCapabilities.class);
        when(capabilities.isModelEnabled()).thenReturn(true);
        llm = mock(LlmChatClient.class);
        when(llm.complete(anyString(), anyString(), anyInt(), anyInt())).thenReturn(RESULT);
        NewsReportInterpretationAgent agent = new NewsReportInterpretationAgent();
        ReflectionTestUtils.setField(agent, "llm", llm);
        ReflectionTestUtils.setField(agent, "json", json);
        service = new NewsReportInterpretationService();
        ReflectionTestUtils.setField(service, "reports", reports);
        ReflectionTestUtils.setField(service, "interpretations", interpretations);
        ReflectionTestUtils.setField(service, "capabilities", capabilities);
        ReflectionTestUtils.setField(service, "agent", agent);
        ReflectionTestUtils.setField(service, "agentRuns", mock(AgentRunRepository.class));
        ReflectionTestUtils.setField(service, "executor", (Executor) jobs::add);
        reports.ingest(List.of(report("最初披露框架协议")));
    }

    @Test
    void readDoesNotCallModelAndRepeatedClicksReuseTaskAndResult() throws Exception {
        assertNull(service.get(ID, 1).getCurrent());
        assertEquals(NewsInterpretationStatus.QUEUED, service.request(ID, 1).getCurrent().getStatus());
        service.request(ID, 1);
        assertEquals(1, jobs.size());
        verifyNoInteractions(llm);
        jobs.remove(0).run();
        assertEquals(List.of("公司签订框架协议"), service.request(ID, 1).getCurrent().getResult().getFacts());
        assertTrue(jobs.isEmpty());
        verify(llm).complete(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void usesTheClickedVersionAndShowsItsResultAsPreviousAfterUpdate() throws Exception {
        reports.ingest(List.of(report("新版披露追加订单")));
        service.request(ID, 1);
        jobs.remove(0).run();
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(anyString(), prompt.capture(), anyInt(), anyInt());
        assertTrue(prompt.getValue().contains("最初披露框架协议"));
        assertFalse(prompt.getValue().contains("新版披露追加订单"));
        var latest = service.get(ID, 2);
        assertNull(latest.getCurrent());
        assertEquals(1, latest.getPrevious().getContentVersion());
        assertEquals(NewsInterpretationStatus.SUCCESS, latest.getPrevious().getStatus());
    }

    @Test
    void invalidModelOutputCanBeRetriedWithoutStoringAFakeResult() throws Exception {
        when(llm.complete(anyString(), anyString(), anyInt(), anyInt())).thenReturn("{}", RESULT);
        service.request(ID, 1);
        jobs.remove(0).run();
        var failed = service.get(ID, 1).getCurrent();
        assertEquals(NewsInterpretationStatus.FAILED, failed.getStatus());
        assertNull(failed.getResult());
        service.request(ID, 1);
        jobs.remove(0).run();
        assertEquals(NewsInterpretationStatus.SUCCESS, service.get(ID, 1).getCurrent().getStatus());
    }

    @Test
    void disabledModelAndMissingVersionDoNotQueueWork() {
        when(capabilities.isModelEnabled()).thenReturn(false);
        assertFalse(service.request(ID, 1).isAvailable());
        assertThrows(ResourceNotFoundException.class, () -> service.request(ID, 2));
        assertTrue(jobs.isEmpty());
        verifyNoInteractions(llm);
    }

    @Test
    void rejectedTaskBecomesRetryable() {
        ReflectionTestUtils.setField(service, "executor", (Executor) job -> {
            throw new RejectedExecutionException();
        });
        assertEquals(NewsInterpretationStatus.FAILED, service.request(ID, 1).getCurrent().getStatus());
        verifyNoInteractions(llm);
    }

    @Test
    void expiredTaskCannotOverwriteARetry() {
        assertTrue(interpretations.tryQueue(ID, 1, "old", 1_000L));
        assertTrue(interpretations.start(ID, 1, "old"));
        assertEquals(NewsInterpretationStatus.FAILED, interpretations.find(ID, 1, 121_000L).orElseThrow().getStatus());
        assertTrue(interpretations.tryQueue(ID, 1, "new", 121_000L));
        interpretations.finish(ID, 1, "old", null, "过期任务失败");
        assertEquals(NewsInterpretationStatus.QUEUED, interpretations.find(ID, 1, 121_000L).orElseThrow().getStatus());
    }

    private NewsReport report(String content) {
        NewsReport report = new NewsReport();
        report.setId(ID);
        report.setTitle("公司披露经营进展");
        report.setContent(content);
        report.setProviderCode("CLS");
        report.setSourceName("财联社");
        report.setKind("FLASH");
        report.setFirstSeenAt(LocalDateTime.of(2026, 11, 7, 10, 0));
        report.setLastSeenAt(report.getFirstSeenAt());
        return report;
    }
}
