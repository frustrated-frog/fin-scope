package com.finscope.service.radar;

import com.finscope.dao.radar.RadarEvidenceRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarEvidencePlan;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadarEvidenceOrchestratorTest {
    private RadarEvidencePlanAgent planAgent;
    private RadarEvidenceToolAdapter tools;
    private RadarEvidenceRepository evidence;
    private RadarEvidenceOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        planAgent = mock(RadarEvidencePlanAgent.class);
        tools = mock(RadarEvidenceToolAdapter.class);
        evidence = mock(RadarEvidenceRepository.class);
        orchestrator = new RadarEvidenceOrchestrator(planAgent, tools, evidence);
    }

    @Test
    void highPriorityEventExecutesAtMostTwoWhitelistedActions() {
        RadarEvent event = event(82);
        RadarEvidencePlan plan = plan("300750",
                action("research_material_search", "ANNOUNCEMENT", "300750", "量产公告"),
                action("public_news_search", null, null, "宁德时代 新电池"),
                action("research_material_search", "BROKER_REPORT", "300750", "盈利影响"));
        when(planAgent.plan(eq(event), anyList())).thenReturn(plan);
        when(tools.execute(any(RadarEvidencePlan.Action.class))).thenAnswer(invocation ->
                RadarEvidenceToolAdapter.ToolResult.success(Collections.singletonList(
                        evidence("公开来源", "https://example.com/" + invocation.getArgument(0, RadarEvidencePlan.Action.class).getToolCode()))));

        RadarEvidenceOrchestrator.Outcome outcome = orchestrator.enrich(event, Collections.singletonList(signal()));

        ArgumentCaptor<RadarEvidencePlan.Action> actions = ArgumentCaptor.forClass(RadarEvidencePlan.Action.class);
        verify(tools, org.mockito.Mockito.times(2)).execute(actions.capture());
        assertEquals(Arrays.asList("research_material_search", "public_news_search"), Arrays.asList(
                actions.getAllValues().get(0).getToolCode(), actions.getAllValues().get(1).getToolCode()));
        verify(evidence).replaceForEvent(eq(7L), anyList());
        assertEquals("SUCCESS", outcome.getStatus());
        assertEquals(2, outcome.getEvidenceCount());
    }

    @Test
    void structuredMaterialActionWithoutStockCodeIsRejectedButPublicSearchCanRun() {
        RadarEvent event = event(80);
        when(planAgent.plan(eq(event), anyList())).thenReturn(plan("",
                action("research_material_search", "ANNOUNCEMENT", "", "公司公告"),
                action("public_news_search", null, null, "人工智能 行业变化")));
        when(tools.execute(any(RadarEvidencePlan.Action.class))).thenReturn(
                RadarEvidenceToolAdapter.ToolResult.success(Collections.singletonList(
                        evidence("公开来源", "https://example.com/news"))));

        RadarEvidenceOrchestrator.Outcome outcome = orchestrator.enrich(event, Collections.singletonList(signal()));

        ArgumentCaptor<RadarEvidencePlan.Action> action = ArgumentCaptor.forClass(RadarEvidencePlan.Action.class);
        verify(tools).execute(action.capture());
        assertEquals("public_news_search", action.getValue().getToolCode());
        assertTrue(outcome.getWarning().contains("股票代码"));
    }

    @Test
    void lowPriorityEventDoesNotPlanOrSearchAutomatically() {
        RadarEvidenceOrchestrator.Outcome outcome = orchestrator.enrich(event(54), Collections.singletonList(signal()));

        verify(planAgent, never()).plan(any(RadarEvent.class), anyList());
        verify(tools, never()).execute(any(RadarEvidencePlan.Action.class));
        verify(evidence, never()).replaceForEvent(any(), anyList());
        assertEquals("SKIPPED", outcome.getStatus());
    }

    @Test
    void unchangedEventReusesPersistedEvidenceWithoutPlanningAgain() {
        RadarEvent event = event(82);
        RadarEvidenceOrchestrator.Outcome first = orchestrator.enrich(event, Collections.singletonList(signal()));
        event.setEvidenceFingerprint(first.getFingerprint());

        RadarEvidenceOrchestrator.Outcome cached = orchestrator.enrich(event, Collections.singletonList(signal()));

        assertEquals("CACHED", cached.getStatus());
        verify(planAgent, org.mockito.Mockito.times(1)).plan(eq(event), anyList());
    }

    private RadarEvent event(int score) {
        RadarEvent event = new RadarEvent();
        event.setId(7L); event.setEventKey("company:event"); event.setCanonicalTitle("宁德时代发布新一代电池");
        event.setSummary("公司公布新产品与量产计划"); event.setPriorityScore(score); event.setSourceCount(2);
        event.setSignalCount(2);
        return event;
    }

    private RadarSignal signal() {
        RadarSignal signal = new RadarSignal(); signal.setTitle("宁德时代发布新一代电池");
        signal.setContent("股票代码300750，公司公布量产计划"); return signal;
    }

    private RadarEvidencePlan plan(String stockCode, RadarEvidencePlan.Action... actions) {
        RadarEvidencePlan plan = new RadarEvidencePlan(); plan.setEventType("COMPANY_EVENT");
        plan.setSubject("宁德时代"); plan.setStockCode(stockCode); plan.setActions(Arrays.asList(actions)); return plan;
    }

    private RadarEvidencePlan.Action action(String tool, String type, String code, String query) {
        RadarEvidencePlan.Action action = new RadarEvidencePlan.Action(); action.setToolCode(tool);
        action.setMaterialType(type); action.setStockCode(code); action.setQuery(query); return action;
    }

    private RadarEvidence evidence(String source, String url) {
        RadarEvidence value = new RadarEvidence(); value.setSourceName(source); value.setUrl(url);
        value.setTitle("补充证据"); value.setSummary("证据摘要"); return value;
    }
}
