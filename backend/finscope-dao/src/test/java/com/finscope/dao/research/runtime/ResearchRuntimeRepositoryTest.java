package com.finscope.dao.research.runtime;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.domain.research.runtime.ResearchRuntimeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchRuntimeRepositoryTest {
    @TempDir
    Path tempDir;
    private ResearchRuntimeRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("finance.db"));
        jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        insertResearchRun(9L);

        repository = new ResearchRuntimeRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void compareAndSetRejectsStaleCheckpointVersion() {
        ResearchRuntimeCheckpoint created = repository.initialize(9L, 12);

        assertTrue(repository.compareAndSetStatus(9L, created.getStateVersion(), "RUNNING", "plan_sources"));
        assertFalse(repository.compareAndSetStatus(9L, created.getStateVersion(), "RUNNING", "collect_sources"));
        assertEquals(1, repository.findCheckpoint(9L).get().getStateVersion());
    }

    @Test
    void eventsUseMonotonicSequenceWithinRun() {
        repository.initialize(9L, 12);
        repository.appendEvent(event("NODE_STARTED", "plan_sources", "RUNNING"));
        repository.appendEvent(event("NODE_COMPLETED", "plan_sources", "COMPLETED"));

        List<Integer> sequences = repository.findEvents(9L).stream()
                .map(ResearchRuntimeEvent::getSequenceNo)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(1, 2, 3), sequences);
        assertTrue(repository.hasCompletedNode(9L, "plan_sources"));
    }

    @Test
    void countsPreviouslyStartedEquivalentActions() {
        repository.initialize(9L, 12);
        ResearchRuntimeEvent first = event("NODE_STARTED", "collect:1", "RUNNING");
        first.setActionFingerprint("fetch:source:1");
        repository.appendEvent(first);
        ResearchRuntimeEvent second = event("NODE_STARTED", "collect:1:retry", "RUNNING");
        second.setActionFingerprint("fetch:source:1");
        repository.appendEvent(second);

        assertEquals(2, repository.countStartedActions(9L, "fetch:source:1"));
    }

    @Test
    void marksRunningCheckpointInterruptedAndAppendsRecoveryEvent() {
        ResearchRuntimeCheckpoint created = repository.initialize(9L, 12);
        assertTrue(repository.compareAndSetStatus(9L, created.getStateVersion(), "RUNNING", "collect_sources"));

        assertEquals(1, repository.interruptRunning("process stopped"));

        assertEquals("INTERRUPTED", repository.findCheckpoint(9L).get().getStatus());
        assertEquals("RUNTIME_INTERRUPTED", repository.findEvents(9L).get(1).getEventType());
    }

    @Test
    void marksReadyCheckpointInterruptedSoItCanBeResumedAfterRestart() {
        repository.initialize(9L, 12);

        assertEquals(1, repository.interruptRunning("process stopped"));

        assertEquals("INTERRUPTED", repository.findCheckpoint(9L).get().getStatus());
        assertEquals("RUNTIME_INTERRUPTED", repository.findEvents(9L).get(1).getEventType());
    }

    private ResearchRuntimeEvent event(String type, String node, String status) {
        ResearchRuntimeEvent event = new ResearchRuntimeEvent();
        event.setResearchRunId(9L);
        event.setEventType(type);
        event.setNodeId(node);
        event.setStatus(status);
        return event;
    }

    private void insertResearchRun(Long id) {
        String now = LocalDateTime.now().toString();
        jdbc.update("INSERT INTO research_run(id,run_date,theme_codes,status,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                id, "2026-07-26", "china_macro", "RUNNING", now, now);
    }
}
