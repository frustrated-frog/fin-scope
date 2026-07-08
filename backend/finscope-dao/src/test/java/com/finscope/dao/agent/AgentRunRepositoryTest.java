package com.finscope.dao.agent;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.agent.AgentRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunRepositoryTest {
    private AgentRunRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-agent-run-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();

        repository = new AgentRunRepository(jdbcTemplate);
    }

    @Test
    void recordsHarnessTraceFieldsAndKeepsLegacyRecordCompatible() {
        repository.record(300L, null, null, "source-fetch", "SUCCESS",
                "sourceId=1", "success=2", null, 12L);

        AgentRun trace = new AgentRun();
        trace.setResearchRunId(300L);
        trace.setNodeName("source-fetch");
        trace.setStatus("SKIPPED");
        trace.setInput("sourceId=1");
        trace.setOutput("");
        trace.setErrorMessage("Repeated action reached hard threshold");
        trace.setDurationMs(5L);
        trace.setStepId("source-fetch:source:1");
        trace.setAttempt(3);
        trace.setActionFingerprint("source-fetch:source:1");
        trace.setInputHash("abc123");
        trace.setOutputHash("def456");
        trace.setErrorType("REPEATED_ACTION");
        trace.setFallbackUsed(true);
        trace.setFallbackReason("cached-result");
        trace.setTerminationReason("repeated-action");
        trace.setProgressDelta(0);
        trace.setBudgetSnapshot("{\"nodeCount\":3}");
        trace.setMetadataJson("{\"sourceId\":1}");
        repository.record(trace);

        List<AgentRun> runs = repository.findByResearchRunId(300L);

        assertEquals(2, runs.size());
        assertEquals("source-fetch", runs.get(0).getNodeName());
        assertNull(runs.get(0).getActionFingerprint());
        assertEquals(1, runs.get(0).getAttempt());
        assertFalse(runs.get(0).isFallbackUsed());

        AgentRun saved = runs.get(1);
        assertEquals("source-fetch:source:1", saved.getStepId());
        assertEquals(3, saved.getAttempt());
        assertEquals("source-fetch:source:1", saved.getActionFingerprint());
        assertEquals("abc123", saved.getInputHash());
        assertEquals("def456", saved.getOutputHash());
        assertEquals("REPEATED_ACTION", saved.getErrorType());
        assertEquals("cached-result", saved.getFallbackReason());
        assertEquals("repeated-action", saved.getTerminationReason());
        assertEquals(0, saved.getProgressDelta());
        assertEquals("{\"nodeCount\":3}", saved.getBudgetSnapshot());
        assertEquals("{\"sourceId\":1}", saved.getMetadataJson());
    }

    @Test
    void recordsCreatedAtAsNodeStartTime() {
        LocalDateTime beforeRecord = LocalDateTime.now();

        repository.record(300L, null, null, "brief-generate", "SUCCESS",
                "articles=3", "markdownChars=1200", null, 60_000L);

        AgentRun saved = repository.latest(1).get(0);
        assertTrue(saved.getCreatedAt().isBefore(beforeRecord),
                "createdAt should be the node start time, not the persistence time");
    }
}
