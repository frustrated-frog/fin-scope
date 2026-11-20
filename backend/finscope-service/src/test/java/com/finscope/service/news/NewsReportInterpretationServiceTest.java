package com.finscope.service.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.news.NewsInterpretationStatus;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.news.NewsReportInterpretationRepository;
import com.finscope.dao.news.NewsReportRepository;
import com.finscope.dao.news.NewsWindowSchemaMigrator;
import com.finscope.domain.news.NewsReport;
import com.finscope.domain.news.NewsReportInterpretation;
import com.finscope.rpc.llm.NewsAiLunaClient;
import com.finscope.rpc.llm.LunaResponsesClient.Prepared;
import com.finscope.rpc.llm.LunaResponsesClient.Reply;
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
            {"lead":"公司签订框架协议","concepts":[{"term":"框架协议","meaning":"双方先约定合作方式。"}],
             "sections":[{"question":"签约相当于拿到订单了吗？","answer":"实际采购量取决于后续订单。"}]}
            """;
    @TempDir
    Path directory;
    private final List<Runnable> jobs = new ArrayList<>();
    private NewsReportRepository reports;
    private NewsReportInterpretationRepository interpretations;
    private NewsWorkbenchCapabilities capabilities;
    private NewsAiLunaClient llm;
    private NewsReportInterpretationService service;

    private Reply reply(String output) {
        Reply value = new Reply();
        value.setCompleted(true);
        value.setText(output);
        return value;
    }

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
        when(capabilities.isInterpretationEnabled()).thenReturn(true);
        llm = mock(NewsAiLunaClient.class);
        when(llm.prepare(anyString(), anyString(), eq(1600), eq(NewsReadingGuidance.SCHEMA))).thenReturn(new Prepared());
        when(llm.complete(any(Prepared.class))).thenReturn(reply(RESULT));
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
        var saved = service.get(ID, 1).getCurrent().getResult().getReading();
        assertEquals("框架协议", saved.concepts().getFirst().term());
        assertEquals("实际采购量取决于后续订单。", saved.sections().getFirst().answer());
        assertTrue(jobs.isEmpty());
        verify(llm).complete(any(Prepared.class));
    }

    @Test
    void acceptsASingleSentenceWithoutInventingFollowUpSections() throws Exception {
        when(llm.complete(any(Prepared.class))).thenReturn(reply("""
                {"lead":"公司已否认网传订单。","concepts":[],"sections":[]}
                """));
        service.request(ID, 1);
        jobs.remove(0).run();
        var result = service.get(ID, 1).getCurrent().getResult();
        assertEquals(List.of("公司已否认网传订单。"), result.getFacts());
        assertTrue(result.getWhyItMatters().isEmpty());
        assertTrue(result.getReading().concepts().isEmpty());
        assertTrue(result.getReading().sections().isEmpty());
    }

    @Test
    void usesTheClickedVersionAndShowsItsResultAsPreviousAfterUpdate() throws Exception {
        reports.ingest(List.of(report("新版披露追加订单")));
        service.request(ID, 1);
        jobs.remove(0).run();
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llm).prepare(anyString(), prompt.capture(), eq(1600), eq(NewsReadingGuidance.SCHEMA));
        assertTrue(prompt.getValue().contains("最初披露框架协议"));
        assertFalse(prompt.getValue().contains("新版披露追加订单"));
        var latest = service.get(ID, 2);
        assertNull(latest.getCurrent());
        assertEquals(1, latest.getPrevious().getContentVersion());
        assertEquals(NewsInterpretationStatus.SUCCESS, latest.getPrevious().getStatus());
    }

    @Test
    void invalidModelOutputCanBeRetriedWithoutStoringAFakeResult() throws Exception {
        when(llm.complete(any(Prepared.class))).thenReturn(reply("{}"), reply(RESULT));
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
    void legacyResultRemainsReadableThroughFailureAndUpgradesOnlyOnRequest() throws Exception {
        var old = new NewsReportInterpretation.Result();
        old.setFormatVersion(2);
        old.setFacts(List.of("旧解读"));
        assertTrue(interpretations.tryQueue(ID, 1, "legacy", 1000L));
        interpretations.finish(ID, 1, "legacy", old, null);
        assertNull(service.get(ID, 1).getCurrent());
        assertEquals(old, service.get(ID, 1).getPrevious().getResult());
        verifyNoInteractions(llm);

        when(llm.complete(any(Prepared.class)))
                .thenReturn(reply("42"), reply(RESULT));
        assertEquals(old, service.request(ID, 1).getPrevious().getResult());
        service.request(ID, 1);
        assertEquals(1, jobs.size());
        jobs.remove(0).run();
        var failed = service.get(ID, 1);
        assertEquals(NewsInterpretationStatus.FAILED, failed.getCurrent().getStatus());
        assertEquals(old, failed.getPrevious().getResult());
        service.request(ID, 1);
        jobs.remove(0).run();
        var upgraded = service.request(ID, 1);
        assertEquals(NewsReportInterpretation.CURRENT_FORMAT, upgraded.getCurrent().getResult().getFormatVersion());
        assertTrue(jobs.isEmpty());
        verify(llm, times(2)).complete(any(Prepared.class));
    }

    @Test
    void disabledModelAndMissingVersionDoNotQueueWork() {
        when(capabilities.isInterpretationEnabled()).thenReturn(false);
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
        assertTrue(interpretations.start(ID, 1, "old", 100_000L));
        assertFalse(interpretations.tryQueue(ID, 1, "early", 121_000L));
        assertEquals(NewsInterpretationStatus.FAILED, interpretations.find(ID, 1, 220_000L).orElseThrow().getStatus());
        assertTrue(interpretations.tryQueue(ID, 1, "new", 220_000L));
        interpretations.finish(ID, 1, "old", null, "过期任务失败");
        assertEquals(NewsInterpretationStatus.QUEUED, interpretations.find(ID, 1, 220_000L).orElseThrow().getStatus());
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
