package com.finscope.service.research.agent;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.dao.research.agent.ResearchAgentRepository;
import com.finscope.dao.research.mission.ResearchMissionRepository;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.research.agent.tool.ResearchAgentTool;
import com.finscope.service.research.agent.tool.ResearchAgentToolContext;
import com.finscope.service.research.agent.tool.ResearchAgentToolRegistry;
import com.finscope.service.research.agent.tool.ResearchToolDispatcher;
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
        ResearchAgentLoopService loop = new ResearchAgentLoopService(
                agents, contexts, decisionAgent, dispatcher, new ResearchAgentStateReducer(agents),
                finishVerifier, mock(ResearchMissionService.class), runtimeService);

        ResearchAgentLoopResult result = loop.run(66L);

        assertTrue(result.isFinishAccepted());
        assertEquals(2, result.getDecisionCount());
        assertEquals(1, result.getExternalActionCount());
        assertEquals(2, agents.findDecisions(66L).size());
        assertEquals(1, agents.findObservations(66L).size());
        verify(runtimeService).completeNode(eq(66L), anyString(), eq("next-state"), eq(2),
                eq("first-observation-added-counter-evidence"));
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
                agents, contexts, decisionAgent, dispatcher, new ResearchAgentStateReducer(agents),
                finishVerifier, mock(ResearchMissionService.class), mock(ResearchRuntimeService.class));

        ResearchAgentLoopResult result = loop.run(66L);

        assertTrue(result.isAborted());
        assertEquals("REPEATED_FINISH_REJECTED:EVIDENCE_INSUFFICIENT", result.getTerminationReason());
        assertEquals(2, result.getDecisionCount());
        assertEquals(2, agents.findDecisions(66L).size());
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
}
