package com.finscope.service.research.agent;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.dao.research.agent.ResearchAgentRepository;
import com.finscope.dao.research.mission.ResearchMissionRepository;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import com.finscope.domain.research.ResearchMode;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchAgentState;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.research.agent.tool.ResearchAgentTool;
import com.finscope.service.research.agent.tool.ResearchAgentToolContext;
import com.finscope.service.research.agent.tool.ResearchAgentToolRegistry;
import com.finscope.service.research.agent.tool.ResearchToolDispatcher;
import com.finscope.service.research.agent.tool.ResearchToolRetryExecutor;
import com.finscope.service.research.mission.ResearchMissionService;
import com.finscope.service.research.mission.ResearchToolRegistry;
import com.finscope.service.research.runtime.ResearchRuntimeService;
import com.finscope.service.research.runtime.RuntimeNodeStart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchAgentLoopServiceTest {
    @TempDir
    Path tempDir;
    private ResearchAgentRepository agents;
    private ResearchMissionRepository missions;
    private ResearchRuntimeRepository runtimes;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        String now = LocalDateTime.now().toString();
        jdbc.update("INSERT INTO research_run(id,run_date,theme_codes,status,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                66L, "2026-07-27", "ai_compute", "RUNNING", now, now);
        agents = new ResearchAgentRepository();
        missions = new ResearchMissionRepository();
        runtimes = new ResearchRuntimeRepository();
        ReflectionTestUtils.setField(agents, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(missions, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(runtimes, "jdbcTemplate", jdbc);
        missions.initialize(66L, "AI资本开支能否持续？", "AI算力", "验证正反证据",
                Collections.singletonList("包含独立正反证据"), 12);
        agents.initialize(66L, "按最新 Observation 选择动作");
        runtimes.initialize(66L, 12);
    }

    @Test
    void feedsFirstToolObservationIntoSecondModelDecisionAndFinishes() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenAnswer(invocation -> {
            int call = calls.incrementAndGet();
            String prompt = invocation.getArgument(1);
            if (call == 1) return searchDecision();
            assertTrue(prompt.contains("first-observation-added-counter-evidence"));
            return finishDecision();
        });
        ResearchDecisionValidator validator = new ResearchDecisionValidator();
        ResearchDecisionAgent decisionAgent = new ResearchDecisionAgent(
                llm, validator, new DeterministicResearchPolicy(validator));
        ResearchAgentContextBuilder contexts = new ResearchAgentContextBuilder(
                missions, agents, runtimes, new ResearchToolRegistry());
        ResearchToolDispatcher dispatcher = new ResearchToolDispatcher(new ResearchAgentToolRegistry(
                Collections.<ResearchAgentTool>singletonList(new ObservationTool())));
        ResearchRuntimeService runtimeService = mock(ResearchRuntimeService.class);
        when(runtimeService.startNode(eq(66L), anyString(), eq("EXPAND"), anyString(), anyString()))
                .thenReturn(RuntimeNodeStart.started(new ResearchRuntimeCheckpoint()));
        ResearchFinishVerifier finishVerifier = mock(ResearchFinishVerifier.class);
        when(finishVerifier.verify(66L)).thenReturn(
                new ResearchFinishVerdict(true, "ACCEPTED", Collections.<String>emptyList()));
        ResearchMissionService missionService = mock(ResearchMissionService.class);
        when(missionService.assess(eq(66L), anyString())).thenReturn(sufficientGap());
        ResearchAgentLoopService loop = new ResearchAgentLoopService(
                agents, contexts, decisionAgent, new ResearchToolRetryExecutor(dispatcher),
                new ResearchAgentTurnService(agents, new ResearchAgentStateReducer(agents), runtimeService),
                new ResearchAgentStateReducer(agents),
                finishVerifier, missionService, runtimeService);

        ResearchAgentLoopResult result = loop.run(66L);

        assertTrue(result.isFinishAccepted());
        assertEquals(2, result.getDecisionCount());
        assertEquals(1, result.getExternalActionCount());
        assertEquals(2, agents.findDecisions(66L).size());
        assertEquals(1, agents.findObservations(66L).size());
        verify(runtimeService).completeNode(eq(66L), anyString(), eq("next-state"), eq(2),
                eq("first-observation-added-counter-evidence"));
        verify(missionService).assess(eq(66L), anyString());
    }

    @Test
    void abortsAfterRepeatedFinishRejectionWithoutEvidenceProgress() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenReturn(finishDecision());
        ResearchDecisionValidator validator = new ResearchDecisionValidator();
        ResearchDecisionAgent decisionAgent = new ResearchDecisionAgent(
                llm, validator, new DeterministicResearchPolicy(validator));
        ResearchAgentContextBuilder contexts = new ResearchAgentContextBuilder(
                missions, agents, runtimes, new ResearchToolRegistry());
        ResearchToolDispatcher dispatcher = new ResearchToolDispatcher(new ResearchAgentToolRegistry(
                Collections.<ResearchAgentTool>emptyList()));
        ResearchFinishVerifier finishVerifier = mock(ResearchFinishVerifier.class);
        when(finishVerifier.verify(66L)).thenReturn(new ResearchFinishVerdict(false,
                "EVIDENCE_INSUFFICIENT", Collections.singletonList("缺少反向证据")));
        ResearchAgentLoopService loop = new ResearchAgentLoopService(
                agents, contexts, decisionAgent, new ResearchToolRetryExecutor(dispatcher),
                new ResearchAgentTurnService(agents, new ResearchAgentStateReducer(agents),
                        mock(ResearchRuntimeService.class)),
                new ResearchAgentStateReducer(agents),
                finishVerifier, mock(ResearchMissionService.class), mock(ResearchRuntimeService.class));

        ResearchAgentLoopResult result = loop.run(66L);

        assertTrue(result.isAborted());
        assertEquals("REPEATED_FINISH_REJECTED:EVIDENCE_INSUFFICIENT", result.getTerminationReason());
        assertEquals(2, result.getDecisionCount());
        assertEquals(2, agents.findDecisions(66L).size());
    }

    @Test
    void retriesTransientToolFailureAndPersistsOnlyFinalObservation() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenReturn(searchDecision(), finishDecision());
        ResearchDecisionValidator validator = new ResearchDecisionValidator();
        ResearchDecisionAgent decisionAgent = new ResearchDecisionAgent(
                llm, validator, new DeterministicResearchPolicy(validator));
        ResearchAgentContextBuilder contexts = new ResearchAgentContextBuilder(
                missions, agents, runtimes, new ResearchToolRegistry());
        RetryingObservationTool tool = new RetryingObservationTool();
        ResearchToolDispatcher dispatcher = new ResearchToolDispatcher(new ResearchAgentToolRegistry(
                Collections.<ResearchAgentTool>singletonList(tool)));
        ResearchRuntimeService runtimeService = mock(ResearchRuntimeService.class);
        when(runtimeService.startNode(eq(66L), anyString(), eq("EXPAND"), anyString(), anyString()))
                .thenReturn(RuntimeNodeStart.started(new ResearchRuntimeCheckpoint()));
        ResearchFinishVerifier finishVerifier = mock(ResearchFinishVerifier.class);
        when(finishVerifier.verify(66L)).thenReturn(
                new ResearchFinishVerdict(true, "ACCEPTED", Collections.<String>emptyList()));
        ResearchAgentLoopService loop = new ResearchAgentLoopService(
                agents, contexts, decisionAgent, new ResearchToolRetryExecutor(dispatcher),
                new ResearchAgentTurnService(agents, new ResearchAgentStateReducer(agents), runtimeService),
                new ResearchAgentStateReducer(agents),
                finishVerifier, mock(ResearchMissionService.class), runtimeService);

        ResearchAgentLoopResult result = loop.run(66L);

        assertTrue(result.isFinishAccepted());
        assertEquals(2, tool.calls.get());
        assertEquals(1, agents.findObservations(66L).size());
        assertEquals(2, agents.findObservations(66L).get(0).getAttemptCount());
        assertTrue(agents.findObservations(66L).get(0).getObservationSummary().contains("恢复成功"));
        verify(runtimeService).completeNode(eq(66L), anyString(), eq("retry-recovered"), eq(2), anyString());
    }

    @Test
    void failsRuntimeNodeAndAbortsOnTerminalToolError() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenReturn(searchDecision());
        ResearchDecisionValidator validator = new ResearchDecisionValidator();
        ResearchDecisionAgent decisionAgent = new ResearchDecisionAgent(
                llm, validator, new DeterministicResearchPolicy(validator));
        ResearchAgentContextBuilder contexts = new ResearchAgentContextBuilder(
                missions, agents, runtimes, new ResearchToolRegistry());
        ResearchToolDispatcher dispatcher = new ResearchToolDispatcher(new ResearchAgentToolRegistry(
                Collections.<ResearchAgentTool>singletonList(new TerminalObservationTool())));
        ResearchRuntimeService runtimeService = mock(ResearchRuntimeService.class);
        when(runtimeService.startNode(eq(66L), anyString(), eq("EXPAND"), anyString(), anyString()))
                .thenReturn(RuntimeNodeStart.started(new ResearchRuntimeCheckpoint()));
        ResearchAgentLoopService loop = new ResearchAgentLoopService(
                agents, contexts, decisionAgent, new ResearchToolRetryExecutor(dispatcher),
                new ResearchAgentTurnService(agents, new ResearchAgentStateReducer(agents), runtimeService),
                new ResearchAgentStateReducer(agents),
                mock(ResearchFinishVerifier.class), mock(ResearchMissionService.class), runtimeService);

        ResearchAgentLoopResult result = loop.run(66L);

        assertTrue(result.isAborted());
        assertEquals("SOURCE_ACCESS_DENIED", result.getTerminationReason());
        assertEquals("FAILED", agents.findDecisions(66L).get(0).getStatus());
        assertEquals(1, agents.findObservations(66L).size());
        verify(runtimeService).failNode(eq(66L), anyString(), eq("SOURCE_ACCESS_DENIED"), anyString());
    }

    @Test
    void quickEndsAtTheEvidenceBoundaryWithoutProposingARejectedThirdSearch() throws Exception {
        seedCompletedSearchDecision(1);
        seedCompletedSearchDecision(2);
        ResearchAgentState seededState = agents.findState(66L).get();
        seededState.setDecisionCount(2);
        assertTrue(agents.updateState(seededState, seededState.getStateVersion()));
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenReturn(searchDecision());
        ResearchDecisionValidator validator = new ResearchDecisionValidator();
        ResearchDecisionAgent decisionAgent = new ResearchDecisionAgent(
                llm, validator, new DeterministicResearchPolicy(validator));
        ResearchAgentContextBuilder contexts = new ResearchAgentContextBuilder(
                missions, agents, runtimes, new ResearchToolRegistry());
        ResearchToolDispatcher dispatcher = new ResearchToolDispatcher(new ResearchAgentToolRegistry(
                Collections.<ResearchAgentTool>emptyList()));
        ResearchRuntimeService runtimeService = mock(ResearchRuntimeService.class);
        ResearchMissionService missionService = mock(ResearchMissionService.class);
        when(missionService.assess(eq(66L), anyString())).thenReturn(insufficientGap());
        ResearchAgentLoopService loop = new ResearchAgentLoopService(
                agents, contexts, decisionAgent, new ResearchToolRetryExecutor(dispatcher),
                new ResearchAgentTurnService(agents, new ResearchAgentStateReducer(agents), runtimeService),
                new ResearchAgentStateReducer(agents), mock(ResearchFinishVerifier.class),
                missionService, runtimeService);

        ResearchAgentLoopResult result = loop.run(66L, ResearchMode.QUICK);

        assertTrue(result.isAborted());
        assertEquals(2, result.getExternalActionCount());
        assertEquals("EVIDENCE_LIMIT_REACHED", result.getTerminationReason());
        assertEquals(3, agents.findDecisions(66L).size());
        assertEquals("ABORT", agents.findDecisions(66L).get(2).getDecisionType());
        assertEquals("COMPLETED", agents.findDecisions(66L).get(2).getStatus());
        assertTrue(agents.findDecisions(66L).get(2).getValidationError() == null);
    }

    private void seedCompletedSearchDecision(int iteration) {
        ResearchAgentDecision decision = new ResearchAgentDecision();
        decision.setResearchRunId(66L);
        decision.setIteration(iteration);
        decision.setDecisionType("TOOL_CALL");
        decision.setCurrentSubgoal("既有搜索 " + iteration);
        decision.setToolCode("public_news_search");
        decision.setArgumentsJson("{\"query\":\"历史查询\",\"intent\":\"SUPPORT\"}");
        decision.setDecisionSummary("既有搜索动作");
        decision.setConfidence(1D);
        decision.setDecisionMode("DETERMINISTIC");
        decision.setActionFingerprint("existing-search-" + iteration);
        decision.setStatus("COMPLETED");
        agents.appendDecision(decision);
    }

    private com.finscope.domain.research.mission.ResearchMissionGap sufficientGap() {
        com.finscope.domain.research.mission.ResearchMissionGap gap =
                new com.finscope.domain.research.mission.ResearchMissionGap();
        gap.setEvidenceCount(8);
        gap.setSourceCount(4);
        gap.setSupportCount(5);
        gap.setCounterCount(3);
        gap.setSufficient(true);
        gap.setStateHash("sufficient-gap");
        return gap;
    }

    private com.finscope.domain.research.mission.ResearchMissionGap insufficientGap() {
        com.finscope.domain.research.mission.ResearchMissionGap gap = sufficientGap();
        gap.setSufficient(false);
        gap.setCounterCount(0);
        gap.setStateHash("insufficient-gap");
        return gap;
    }

    private String searchDecision() {
        return "{\"decisionType\":\"TOOL_CALL\",\"currentSubgoal\":\"补齐反方证据\","
                + "\"toolCode\":\"public_news_search\","
                + "\"arguments\":{\"query\":\"AI算力 风险 下调\",\"intent\":\"COUNTER\"},"
                + "\"targetGap\":\"缺少反方证据\",\"expectedObservation\":\"获得独立反方来源\","
                + "\"decisionSummary\":\"优先补齐反方材料\",\"confidence\":0.8}";
    }

    private String finishDecision() {
        return "{\"decisionType\":\"FINISH\",\"currentSubgoal\":\"提交完成校验\","
                + "\"arguments\":{},\"planPatch\":{},"
                + "\"decisionSummary\":\"最新 Observation 已补齐关键证据\",\"confidence\":0.9}";
    }

    private static class ObservationTool implements ResearchAgentTool {
        @Override
        public ResearchToolDescriptor descriptor() {
            ResearchToolDescriptor value = new ResearchToolDescriptor();
            value.setCode("public_news_search");
            return value;
        }

        @Override
        public void validate(Map<String, Object> arguments) {
        }

        @Override
        public ResearchToolObservation execute(ResearchAgentToolContext context, Map<String, Object> arguments) {
            ResearchToolObservation value = new ResearchToolObservation();
            value.setStatus("SUCCESS");
            value.setObservationSummary("first-observation-added-counter-evidence");
            value.setEvidenceDelta(1);
            value.setSourceDelta(1);
            value.setStateHash("next-state");
            return value;
        }
    }

    private static class RetryingObservationTool extends ObservationTool {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ResearchToolObservation execute(ResearchAgentToolContext context, Map<String, Object> arguments) {
            if (calls.incrementAndGet() == 1) {
                ResearchToolObservation value = new ResearchToolObservation();
                value.setStatus("RETRYABLE_ERROR");
                value.setObservationSummary("搜索上游超时");
                value.setErrorType("SEARCH_TIMEOUT");
                value.setRetryable(true);
                value.setStateHash("retry-pending");
                return value;
            }
            ResearchToolObservation value = super.execute(context, arguments);
            value.setStateHash("retry-recovered");
            return value;
        }
    }

    private static class TerminalObservationTool extends ObservationTool {
        @Override
        public ResearchToolObservation execute(ResearchAgentToolContext context, Map<String, Object> arguments) {
            ResearchToolObservation value = new ResearchToolObservation();
            value.setStatus("TERMINAL_ERROR");
            value.setObservationSummary("来源拒绝访问，无法继续当前动作");
            value.setErrorType("SOURCE_ACCESS_DENIED");
            value.setRetryable(false);
            value.setStateHash("terminal-error");
            return value;
        }
    }
}
