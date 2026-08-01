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
    void acceptsStrictModelDecisionWithBoundedCall() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200))).thenReturn(validDecisionJson());

        ResearchDecisionResult result = agent.decide(ResearchAgentTestFixtures.counterGapContext());

        assertEquals("MODEL", result.getDecision().getDecisionMode());
        assertEquals("public_news_search", result.getDecision().getToolCode());
        assertEquals("COUNTER", result.getArguments().get("intent"));
        assertNull(result.getFallbackReason());
        verify(llm).complete(anyString(), anyString(), eq(20000), eq(1200));
    }

    @Test
    void rebuildsToolAndArgumentsFromTheMissionTaskSelectedByModel() throws Exception {
        ResearchDecisionContext context = ResearchAgentTestFixtures.counterGapContext();
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

        assertEquals("MODEL", result.getDecision().getDecisionMode());
        assertEquals("search_counter", result.getDecision().getMissionTaskKey());
        assertEquals("public_news_search", result.getDecision().getToolCode());
        assertEquals("宁德时代 资本开支 风险 下调 延迟 反方证据", result.getArguments().get("query"));
        assertEquals("COUNTER", result.getArguments().get("intent"));
        assertNull(result.getFallbackReason());
    }

    @Test
    void rejectsUnknownJsonFieldAndFallsBackToGapDirectedPolicy() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenReturn(validDecisionJson().replace("\"confidence\":0.83", "\"confidence\":0.83,\"chainOfThought\":\"hidden\""));

        ResearchDecisionResult result = agent.decide(ResearchAgentTestFixtures.counterGapContext());

        assertEquals("DETERMINISTIC", result.getDecision().getDecisionMode());
        assertEquals("public_news_search", result.getDecision().getToolCode());
        assertEquals("COUNTER", result.getArguments().get("intent"));
        assertEquals("DECISION_REJECTED", result.getFallbackReason());
        assertTrue(result.getFallbackDetail().contains("Unrecognized field"));
    }

    @Test
    void classifiesModelTimeoutSeparatelyFromRejectedDecision() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(20000), eq(1200)))
                .thenThrow(new SocketTimeoutException("Read timed out"));

        ResearchDecisionResult result = agent.decide(ResearchAgentTestFixtures.counterGapContext());

        assertEquals("DETERMINISTIC", result.getDecision().getDecisionMode());
        assertEquals("MODEL_TIMEOUT", result.getFallbackReason());
        assertEquals("模型决策响应超时，已切换规则决策", result.getFallbackDetail());
    }

    @Test
    void fallsBackWhenModelIsDisabled() {
        when(llm.isConfigured()).thenReturn(false);

        ResearchDecisionResult result = agent.decide(ResearchAgentTestFixtures.counterGapContext());

        assertEquals("DETERMINISTIC", result.getDecision().getDecisionMode());
        assertEquals("MODEL_DISABLED", result.getFallbackReason());
        assertEquals("COUNTER", result.getArguments().get("intent"));
    }

    @Test
    void acceptsLocalEvidenceAssessmentAfterExternalBudgetIsExhausted() throws Exception {
        ResearchDecisionContext context = ResearchAgentTestFixtures.counterGapContext();
        context.setRemainingActions(0);
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

        assertEquals("evidence_assess", result.getDecision().getToolCode());
        assertEquals("MODEL", result.getDecision().getDecisionMode());
        assertNull(result.getFallbackReason());
    }

    @Test
    void finishesDeterministicallyWhenTheLatestEvidenceAssessmentIsSufficient() {
        ResearchDecisionContext context = ResearchAgentTestFixtures.counterGapContext();
        context.getLatestGap().setSufficient(true);

        ResearchDecisionResult result = agent.decide(context);

        assertEquals("FINISH", result.getDecision().getDecisionType());
        assertEquals("DETERMINISTIC", result.getDecision().getDecisionMode());
        assertNull(result.getFallbackReason());
        verifyNoInteractions(llm);
    }

    private String validDecisionJson() {
        return "{"
                + "\"decisionType\":\"TOOL_CALL\","
                + "\"currentSubgoal\":\"补齐反方证据\","
                + "\"toolCode\":\"public_news_search\","
                + "\"arguments\":{\"query\":\"AI资本开支 下调 风险\",\"intent\":\"COUNTER\"},"
                + "\"targetGap\":\"缺少反方证据\","
                + "\"expectedObservation\":\"获得独立反方来源\","
                + "\"decisionSummary\":\"当前材料单边，优先验证反方事实\","
                + "\"confidence\":0.83} ";
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
