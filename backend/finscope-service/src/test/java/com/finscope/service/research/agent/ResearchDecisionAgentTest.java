package com.finscope.service.research.agent;

import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResearchDecisionAgentTest {
    private LlmChatClient llm;
    private ResearchDecisionAgent agent;

    @BeforeEach
    void setUp() {
        llm = mock(LlmChatClient.class);
        ResearchDecisionValidator validator = new ResearchDecisionValidator();
        agent = new ResearchDecisionAgent(llm, validator, new DeterministicResearchPolicy(validator));
    }

    @Test
    void acceptsOnlyAMissionTaskSelectionAndBuildsTheCallOnTheServer() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenReturn("{"
                + "\"missionTaskKey\":\"search_counter\","
                + "\"decisionSummary\":\"优先补齐反方证据\","
                + "\"confidence\":0.81}");

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("MODEL_ASSISTED", result.getDecision().getDecisionMode());
        assertEquals("search_counter", result.getDecision().getMissionTaskKey());
        assertEquals("public_news_search", result.getDecision().getToolCode());
        assertEquals("COUNTER", result.getArguments().get("intent"));
        assertNull(result.getFallbackReason());
    }

    @Test
    void ignoresProviderSpecificExtraShapesBecauseTheyDoNotOwnTheToolContract() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenReturn("{"
                + "\"decisionType\":\"TOOL_CALL\","
                + "\"missionTaskKey\":\"search_counter\","
                + "\"toolCode\":{\"unexpected\":true},"
                + "\"arguments\":\"provider-specific\","
                + "\"targetGap\":{\"evidenceGap\":3},"
                + "\"planPatch\":\"\","
                + "\"decisionSummary\":\"选择反方任务\","
                + "\"confidence\":\"high\"}");

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("MODEL_ASSISTED", result.getDecision().getDecisionMode());
        assertEquals("public_news_search", result.getDecision().getToolCode());
        assertEquals(0.85D, result.getDecision().getConfidence(), 0.0001D);
        assertNull(result.getFallbackReason());
    }

    @Test
    void repairsMalformedTaskSelectionOnceBeforeUsingControlledSelection() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenReturn("{\"missionTaskKey\":\"search_counter")
                .thenReturn("{\"missionTaskKey\":\"search_counter\","
                        + "\"decisionSummary\":\"修复后选择反方任务\",\"confidence\":0.8}");

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("MODEL_ASSISTED", result.getDecision().getDecisionMode());
        assertEquals("search_counter", result.getDecision().getMissionTaskKey());
        assertNull(result.getFallbackReason());
        verify(llm, times(2)).complete(anyString(), anyString(), eq(20000), eq(1200));
    }

    @Test
    void unrecoverableModelSelectionUsesPrimaryControlledPolicyWithoutFallbackError() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenReturn("not-json")
                .thenReturn("still-not-json");

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("CONTROLLED", result.getDecision().getDecisionMode());
        assertEquals("search_counter", result.getDecision().getMissionTaskKey());
        assertEquals("public_news_search", result.getDecision().getToolCode());
        assertEquals("MODEL_ASSISTANCE_UNAVAILABLE", result.getFallbackReason());
        assertTrue(result.getFallbackDetail().contains("模型辅助未采用"));
        verify(llm, times(2)).complete(anyString(), anyString(), eq(20000), eq(1200));
    }

    @Test
    void acceptsStrictModelDecisionWithBoundedCall() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenReturn(validDecisionJson());

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("MODEL_ASSISTED", result.getDecision().getDecisionMode());
        assertEquals("public_news_search", result.getDecision().getToolCode());
        assertEquals("COUNTER", result.getArguments().get("intent"));
        assertNull(result.getFallbackReason());
        verify(llm).complete(anyString(), anyString(), eq(20000), eq(1200));
    }

    @Test
    void normalizesTextualConfidenceFromReasoningModel() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenReturn(validDecisionJson().replace("\"confidence\":0.83", "\"confidence\":\"high\""));

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("MODEL_ASSISTED", result.getDecision().getDecisionMode());
        assertEquals(0.85D, result.getDecision().getConfidence(), 0.0001D);
        assertNull(result.getFallbackReason());
    }

    @Test
    void ignoresStructuredTargetGapAndKeepsTheServerOwnedGap() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenReturn(validDecisionJson()
                .replace("\"targetGap\":\"缺少反方证据\"",
                        "\"targetGap\":{\"evidenceGap\":3,\"sourceGap\":0}"));

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("MODEL_ASSISTED", result.getDecision().getDecisionMode());
        assertTrue(result.getDecision().getTargetGap().contains("intent=COUNTER"));
        assertNull(result.getFallbackReason());
    }

    @Test
    void ignoresEmptyOptionalDecisionObjectsOutsideTheSelectionContract() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenReturn(validDecisionJson()
                .replace("\"confidence\":0.83", "\"confidence\":0.83,\"planPatch\":\"\""));

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("MODEL_ASSISTED", result.getDecision().getDecisionMode());
        assertNull(result.getFallbackReason());
    }

    @Test
    void rebuildsToolAndArgumentsFromTheMissionTaskSelectedByModel() throws Exception {
        ResearchDecisionContext context = counterTaskContext();
        ResearchMissionTask baseline = task("baseline_scan", "source_scan", "BASELINE", "COMPLETED", null,
                Collections.<String>emptyList());
        ResearchMissionTask counter = task("search_counter", "public_news_search", "COUNTER", "PENDING",
                "宁德时代 资本开支 风险 下调 延迟 反方证据", Collections.singletonList("baseline_scan"));
        context.setTasks(Arrays.asList(baseline, counter));
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenReturn("{"
                + "\"decisionType\":\"TOOL_CALL\","
                + "\"currentSubgoal\":\"补齐反方证据\","
                + "\"missionTaskKey\":\"search_counter\","
                + "\"toolCode\":\"research_material_search\","
                + "\"arguments\":{\"stockCode\":\"300750\",\"materialType\":\"ANNOUNCEMENT\",\"query\":\"风险\"},"
                + "\"targetGap\":\"缺少反方证据\","
                + "\"expectedObservation\":\"获得独立反方来源\","
                + "\"decisionSummary\":\"选择计划中的反方任务\","
                + "\"confidence\":0.83}");

        ResearchDecisionResult result = agent.decide(context);

        assertEquals("MODEL_ASSISTED", result.getDecision().getDecisionMode());
        assertEquals("search_counter", result.getDecision().getMissionTaskKey());
        assertEquals("public_news_search", result.getDecision().getToolCode());
        assertEquals("宁德时代 资本开支 风险 下调 延迟 反方证据", result.getArguments().get("query"));
        assertEquals("COUNTER", result.getArguments().get("intent"));
        assertNull(result.getFallbackReason());
    }

    @Test
    void ignoresUnknownProviderFieldsOutsideTheSelectionContract() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenReturn(validDecisionJson().replace("\"confidence\":0.83", "\"confidence\":0.83,\"chainOfThought\":\"hidden\""));

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("MODEL_ASSISTED", result.getDecision().getDecisionMode());
        assertEquals("public_news_search", result.getDecision().getToolCode());
        assertEquals("COUNTER", result.getArguments().get("intent"));
        assertNull(result.getFallbackReason());
    }

    @Test
    void classifiesModelTimeoutSeparatelyFromRejectedDecision() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenThrow(new SocketTimeoutException("Read timed out"));

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("CONTROLLED", result.getDecision().getDecisionMode());
        assertEquals("MODEL_ASSISTANCE_UNAVAILABLE", result.getFallbackReason());
        assertTrue(result.getFallbackDetail().contains("TIMEOUT"));
        verify(llm, times(1)).complete(anyString(), anyString(), eq(20000), eq(1200));
    }

    @Test
    void doesNotSendAFormatRepairRequestAfterProviderTransportFailure() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenThrow(new IllegalStateException("HTTP 503"));

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("CONTROLLED", result.getDecision().getDecisionMode());
        assertEquals("MODEL_ASSISTANCE_UNAVAILABLE", result.getFallbackReason());
        verify(llm, times(1)).complete(anyString(), anyString(), eq(20000), eq(1200));
    }

    @Test
    void usesTheControlledPolicyWhenModelIsDisabled() {
        when(llm.isConfigured()).thenReturn(false);

        ResearchDecisionResult result = agent.decide(counterTaskContext());

        assertEquals("CONTROLLED", result.getDecision().getDecisionMode());
        assertNull(result.getFallbackReason());
        assertEquals("COUNTER", result.getArguments().get("intent"));
    }

    @Test
    void stopsWithoutCallingTheModelAfterExternalBudgetIsExhausted() throws Exception {
        ResearchDecisionContext context = counterTaskContext();
        context.setRemainingActions(0);
        ResearchMissionTask baseline = task("baseline_scan", "source_scan", "BASELINE", "COMPLETED", null,
                Collections.<String>emptyList());
        ResearchMissionTask assessment = task("assess_evidence", "evidence_assess", "ASSESS", "PENDING", null,
                Collections.singletonList("baseline_scan"));
        context.setTasks(Arrays.asList(baseline, assessment));
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenReturn("{"
                + "\"decisionType\":\"TOOL_CALL\","
                + "\"currentSubgoal\":\"重新评估证据\","
                + "\"toolCode\":\"evidence_assess\","
                + "\"arguments\":{},"
                + "\"targetGap\":\"搜索后刷新统计\","
                + "\"expectedObservation\":\"获得最新证据状态\","
                + "\"decisionSummary\":\"外部搜索结束后执行本地评估\","
                + "\"confidence\":0.95}");

        ResearchDecisionResult result = agent.decide(context);

        assertEquals("ABORT", result.getDecision().getDecisionType());
        assertEquals("CONTROLLED", result.getDecision().getDecisionMode());
        assertNull(result.getFallbackReason());
        verifyNoInteractions(llm);
    }

    @Test
    void finishesDeterministicallyWhenTheLatestEvidenceAssessmentIsSufficient() {
        ResearchDecisionContext context = counterTaskContext();
        context.getLatestGap().setSufficient(true);

        ResearchDecisionResult result = agent.decide(context);

        assertEquals("FINISH", result.getDecision().getDecisionType());
        assertEquals("CONTROLLED", result.getDecision().getDecisionMode());
        assertNull(result.getFallbackReason());
        verifyNoInteractions(llm);
    }

    private String validDecisionJson() {
        return "{"
                + "\"decisionType\":\"TOOL_CALL\","
                + "\"currentSubgoal\":\"补齐反方证据\","
                + "\"missionTaskKey\":\"search_counter\","
                + "\"toolCode\":\"public_news_search\","
                + "\"arguments\":{\"query\":\"AI资本开支 下调 风险\",\"intent\":\"COUNTER\"},"
                + "\"targetGap\":\"缺少反方证据\","
                + "\"expectedObservation\":\"获得独立反方来源\","
                + "\"decisionSummary\":\"当前材料单边，优先验证反方事实\","
                + "\"confidence\":0.83} ";
    }

    private ResearchDecisionContext counterTaskContext() {
        ResearchDecisionContext context = ResearchAgentTestFixtures.counterGapContext();
        ResearchMissionTask baseline = task("baseline_scan", "source_scan", "BASELINE", "COMPLETED", null,
                Collections.<String>emptyList());
        ResearchMissionTask counter = task("search_counter", "public_news_search", "COUNTER", "PENDING",
                "光模块 资本开支 风险 下调 延迟 反方证据",
                Collections.singletonList("baseline_scan"));
        context.setTasks(Arrays.asList(baseline, counter));
        return context;
    }

    private ResearchMissionTask task(String key,
                                     String tool,
                                     String intent,
                                     String status,
                                     String query,
                                     java.util.List<String> dependencies) {
        ResearchMissionTask value = new ResearchMissionTask();
        value.setTaskKey(key);
        value.setTitle("反方证据搜索");
        value.setToolCode(tool);
        value.setIntent(intent);
        value.setStatus(status);
        value.setQueryText(query);
        value.setRationale("补齐反方证据");
        value.setExpectedEvidence("获得独立反方来源");
        value.setDependencies(dependencies);
        return value;
    }
}
