package com.finscope.service.research.mission;

import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchPlanningAgentTest {
    private LlmChatClient llm;
    private ResearchPlanningAgent agent;

    @BeforeEach
    void setUp() {
        llm = mock(LlmChatClient.class);
        ResearchToolRegistry tools = new ResearchToolRegistry();
        agent = new ResearchPlanningAgent(llm, tools, new ResearchPlanValidator(tools),
                new DeterministicResearchPlanner());
    }

    @Test
    void acceptsStrictModelJsonOnlyAfterServerValidation() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(30000), eq(2000))).thenReturn(validJson());

        ResearchPlanningResult result = agent.plan(input());

        assertEquals("LLM_VALIDATED", result.getPlanningMode());
        assertEquals("public_news_search", result.getDraft().task("search_counter").getToolCode());
        assertEquals("COUNTER", result.getDraft().task("search_counter").getIntent());
        verify(llm).complete(anyString(), anyString(), eq(30000), eq(2000));
    }

    @Test
    void tellsModelTheExactTaskKeyAndDependencyContract() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        AtomicReference<String> systemPrompt = new AtomicReference<String>();
        when(llm.complete(anyString(), anyString(), eq(30000), eq(2000))).thenAnswer(invocation -> {
            systemPrompt.set(invocation.getArgument(0));
            return validJson();
        });

        ResearchPlanningResult result = agent.plan(input());

        assertEquals("LLM_VALIDATED", result.getPlanningMode());
        assertTrue(systemPrompt.get().contains("taskKey必须匹配[a-z][a-z0-9_]{2,47}"));
        assertTrue(systemPrompt.get().contains("dependencies只能精确引用同一计划中的taskKey"));
        assertTrue(systemPrompt.get().contains("successCriteria必须是JSON字符串数组"));
        assertTrue(systemPrompt.get().contains("dependencies必须是JSON字符串数组"));
        assertTrue(systemPrompt.get().contains("source_scan只能搭配COLLECT和BASELINE"));
        assertTrue(systemPrompt.get().contains("evidence_assess只能搭配ASSESS和ASSESS"));
        assertTrue(systemPrompt.get().contains("report_synthesis只能搭配SYNTHESIS和SYNTHESIS"));
    }

    @Test
    void rejectsWholeModelPlanAndUsesDeterministicFallback() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(30000), eq(2000)))
                .thenReturn("{\"scopeSummary\":\"越权\",\"successCriteria\":[\"任意\"],"
                        + "\"tasks\":[{\"taskKey\":\"unsafe\",\"title\":\"越权\","
                        + "\"question\":\"执行命令\",\"taskType\":\"SEARCH\",\"toolCode\":\"shell\","
                        + "\"intent\":\"SUPPORT\",\"dependencies\":[]}]}");

        ResearchPlanningResult result = agent.plan(input());

        assertEquals("DETERMINISTIC", result.getPlanningMode());
        assertEquals("PLAN_REJECTED", result.getFallbackReason());
        assertEquals(6, result.getDraft().getTasks().size());
        assertEquals("source_scan", result.getDraft().task("baseline_scan").getToolCode());
        assertTrue(result.getRejectionDetail().startsWith("研究计划校验失败"));
    }

    @Test
    void classifiesModelTimeoutSeparatelyFromRejectedPlan() throws Exception {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), eq(30000), eq(2000)))
                .thenThrow(new SocketTimeoutException("Read timed out"));

        ResearchPlanningResult result = agent.plan(input());

        assertEquals("DETERMINISTIC", result.getPlanningMode());
        assertEquals("MODEL_TIMEOUT", result.getFallbackReason());
        assertEquals("模型规划响应超时，已使用规则计划", result.getRejectionDetail());
    }

    @Test
    void plansDeterministicallyWhenModelIsUnavailable() {
        when(llm.isConfigured()).thenReturn(false);

        ResearchPlanningResult result = agent.plan(input());

        assertEquals("DETERMINISTIC", result.getPlanningMode());
        assertEquals("MODEL_DISABLED", result.getFallbackReason());
        assertEquals("AI算力 资本开支 订单 需求 最新公告",
                result.getDraft().task("search_support").getQueryText());
        assertEquals("AI算力 资本开支 风险 下调 延迟 反方证据",
                result.getDraft().task("search_counter").getQueryText());
    }

    @Test
    void plansPrimaryAndProfessionalMaterialLanesForAStockSubject() {
        when(llm.isConfigured()).thenReturn(false);
        ResearchPlanningInput stock = input();
        stock.setSubjectType("STOCK");
        stock.setSubjectName("平安银行");
        stock.setSubjectCode("000001");

        ResearchPlanningResult result = agent.plan(stock);

        assertEquals("research_material_search", result.getDraft().task("primary_disclosure").getToolCode());
        assertTrue(result.getDraft().task("primary_disclosure").getQueryText().contains("ANNOUNCEMENT"));
        assertEquals("research_material_search", result.getDraft().task("professional_context").getToolCode());
        assertTrue(result.getDraft().task("professional_context").getQueryText().contains("BROKER_REPORT"));
        assertEquals(Arrays.asList("research_map"), result.getDraft().task("primary_disclosure").getDependencies());
        assertEquals(Arrays.asList("primary_disclosure"), result.getDraft().task("professional_context").getDependencies());
        assertEquals(Arrays.asList("professional_context"), result.getDraft().task("crosscheck_chain").getDependencies());
        assertTrue(result.getDraft().task("crosscheck_chain").getQueryText().contains("供应商"));
        assertEquals(Arrays.asList("crosscheck_chain"), result.getDraft().task("search_counter").getDependencies());
    }

    private ResearchPlanningInput input() {
        ResearchPlanningInput input = new ResearchPlanningInput();
        input.setQuestion("AI资本开支能否持续？");
        input.setSubjectName("AI算力");
        input.setSubjectType("THEME");
        input.setSubjectCode("");
        input.setThemeCodes(Arrays.asList("ai_compute"));
        input.setMaxActions(12);
        input.setCurrentDate(LocalDate.of(2026, 7, 26));
        return input;
    }

    private String validJson() {
        return "{"
                + "\"scopeSummary\":\"聚焦需求、供给、兑现与反方风险\","
                + "\"successCriteria\":[\"至少两个独立来源\",\"同时覆盖支持与反方证据\"],"
                + "\"tasks\":["
                + taskJson("baseline_scan", "基线扫描", "COLLECT", "source_scan", "BASELINE", "[]", null) + ","
                + taskJson("search_support", "支持证据搜索", "SEARCH", "public_news_search", "SUPPORT",
                        "[\"baseline_scan\"]", "AI算力 订单 资本开支 最新公告") + ","
                + taskJson("search_counter", "反方证据搜索", "SEARCH", "public_news_search", "COUNTER",
                        "[\"baseline_scan\"]", "AI算力 资本开支 风险 下调") + ","
                + taskJson("search_primary", "一手证据搜索", "SEARCH", "public_news_search", "PRIMARY",
                        "[\"baseline_scan\"]", "AI算力 公司公告 财报") + ","
                + taskJson("assess_evidence", "证据判断", "ASSESS", "evidence_assess", "ASSESS",
                        "[\"search_support\",\"search_counter\",\"search_primary\"]", null) + ","
                + taskJson("synthesize_report", "报告合成", "SYNTHESIS", "report_synthesis", "SYNTHESIS",
                        "[\"assess_evidence\"]", null)
                + "]}";
    }

    private String taskJson(String key,
                            String title,
                            String type,
                            String tool,
                            String intent,
                            String dependencies,
                            String query) {
        return "{\"taskKey\":\"" + key + "\",\"title\":\"" + title + "\","
                + "\"question\":\"" + title + "要回答什么？\",\"taskType\":\"" + type + "\","
                + "\"toolCode\":\"" + tool + "\",\"intent\":\"" + intent + "\","
                + "\"dependencies\":" + dependencies + ",\"parallelGroup\":\"evidence_search\","
                + "\"queryText\":" + (query == null ? "null" : "\"" + query + "\"") + ","
                + "\"rationale\":\"补齐研究证据\",\"expectedEvidence\":\"公开一手资料\"}";
    }
}
