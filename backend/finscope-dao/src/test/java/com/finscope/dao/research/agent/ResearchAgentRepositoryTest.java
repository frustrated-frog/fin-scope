package com.finscope.dao.research.agent;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchAgentState;
import com.finscope.domain.research.agent.ResearchToolObservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchAgentRepositoryTest {
    @TempDir
    Path tempDir;
    private ResearchAgentRepository repository;
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
        insertResearchRun(91L);

        repository = new ResearchAgentRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void persistsVersionedStateAndAppendOnlyDecisionObservationTrace() {
        ResearchAgentState initial = repository.initialize(91L, "先补齐反方证据，再判断是否完成");
        assertEquals("READY", initial.getStatus());
        assertEquals(0, initial.getStateVersion());

        initial.setStatus("DECIDING");
        initial.setCurrentSubgoal("补齐独立反方来源");
        initial.setMemorySummary("支持证据较多，尚无反方材料");
        initial.setEvidenceSummary("evidence=4,sources=2,support=4,counter=0");
        initial.setAttemptedFingerprints(Arrays.asList("public_news_search:counter:v1"));
        initial.setDecisionCount(1);
        assertTrue(repository.updateState(initial, 0));
        assertFalse(repository.updateState(initial, 0));

        ResearchAgentDecision decision = decision(91L, 1);
        repository.appendDecision(decision);
        assertNotNull(decision.getId());

        ResearchToolObservation observation = new ResearchToolObservation();
        observation.setResearchRunId(91L);
        observation.setDecisionId(decision.getId());
        observation.setToolCode("public_news_search");
        observation.setStatus("SUCCESS");
        observation.setObservationSummary("新增1条反方证据和1个独立来源");
        observation.setNewInformation("公司下调短期订单指引");
        observation.setEvidenceDelta(1);
        observation.setSourceDelta(1);
        observation.setDataRefs(Arrays.asList("article:501", "evidence:701"));
        observation.setStateHash("5:3:4:1");
        repository.appendObservation(observation);
        assertNotNull(observation.getId());
        assertTrue(repository.updateDecisionStatus(decision.getId(), "COMPLETED", null));

        ResearchAgentState saved = repository.findState(91L).get();
        assertEquals(1, saved.getStateVersion());
        assertEquals("补齐独立反方来源", saved.getCurrentSubgoal());
        assertEquals(Arrays.asList("public_news_search:counter:v1"), saved.getAttemptedFingerprints());
        assertEquals(1, repository.findDecisions(91L).size());
        assertEquals("MODEL", repository.findDecisions(91L).get(0).getDecisionMode());
        assertEquals("COMPLETED", repository.findDecisions(91L).get(0).getStatus());
        assertEquals(1, repository.findObservations(91L).size());
        assertEquals(Arrays.asList("article:501", "evidence:701"),
                repository.findObservations(91L).get(0).getDataRefs());
        assertEquals(1, repository.findTrace(91L).getDecisions().size());
        assertEquals(1, repository.findTrace(91L).getObservations().size());
    }

    @Test
    void rejectsDuplicateIterationAndSecondObservationForOneDecision() {
        repository.initialize(91L, "计划");
        ResearchAgentDecision first = decision(91L, 1);
        repository.appendDecision(first);

        assertThrows(DataAccessException.class,
                () -> repository.appendDecision(decision(91L, 1)));

        ResearchToolObservation observation = observation(first.getId());
        repository.appendObservation(observation);
        assertThrows(DataAccessException.class,
                () -> repository.appendObservation(observation(first.getId())));
    }

    @Test
    void interruptsOnlyNonTerminalAgentStatesDuringStartupRecovery() {
        repository.initialize(91L, "计划");

        assertEquals(1, repository.interruptRunning("process shutdown"));
        assertEquals("INTERRUPTED", repository.findState(91L).get().getStatus());
        assertEquals("process shutdown", repository.findState(91L).get().getMemorySummary());
        assertEquals(0, repository.interruptRunning("second recovery"));
    }

    private ResearchAgentDecision decision(Long runId, int iteration) {
        ResearchAgentDecision value = new ResearchAgentDecision();
        value.setResearchRunId(runId);
        value.setIteration(iteration);
        value.setDecisionType("TOOL_CALL");
        value.setCurrentSubgoal("补齐反方证据");
        value.setToolCode("public_news_search");
        value.setArgumentsJson("{\"query\":\"AI资本开支 下调 风险\",\"intent\":\"COUNTER\"}");
        value.setTargetGap("counter=0");
        value.setExpectedObservation("获得独立反方来源");
        value.setDecisionSummary("当前证据单边，优先寻找反方材料");
        value.setConfidence(0.82D);
        value.setDecisionMode("MODEL");
        value.setActionFingerprint("public_news_search:counter:v1");
        value.setStatus("PROPOSED");
        return value;
    }

    private ResearchToolObservation observation(Long decisionId) {
        ResearchToolObservation value = new ResearchToolObservation();
        value.setResearchRunId(91L);
        value.setDecisionId(decisionId);
        value.setToolCode("public_news_search");
        value.setStatus("NO_PROGRESS");
        value.setObservationSummary("没有新增证据");
        value.setEvidenceDelta(0);
        value.setSourceDelta(0);
        value.setStateHash("4:2:4:0");
        return value;
    }

    private void insertResearchRun(Long id) {
        String now = LocalDateTime.now().toString();
        jdbc.update("INSERT INTO research_run(id,run_date,theme_codes,status,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                id, "2026-07-27", "ai_startup", "RUNNING", now, now);
    }
}
