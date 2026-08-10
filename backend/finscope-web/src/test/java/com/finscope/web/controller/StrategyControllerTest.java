package com.finscope.web.controller;

import com.finscope.domain.strategy.StrategyHolding;
import com.finscope.domain.strategy.StrategyPlaybook;
import com.finscope.domain.strategy.StrategyPlaybookRule;
import com.finscope.domain.strategy.StrategyStockThesis;
import com.finscope.service.strategy.StrategyHoldingService;
import com.finscope.service.strategy.StrategyPlaybookService;
import com.finscope.service.strategy.StrategyPlaybookView;
import com.finscope.service.strategy.StrategyReviewService;
import com.finscope.service.strategy.StrategyStockThesisService;
import com.finscope.web.handler.ApiExceptionHandler;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StrategyController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class StrategyControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private StrategyHoldingService holdingService;
    @MockBean private StrategyPlaybookService playbookService;
    @MockBean private StrategyStockThesisService thesisService;
    @MockBean private StrategyReviewService reviewService;

    @Test
    void getsEmptyWorkspace() throws Exception {
        when(holdingService.list()).thenReturn(Collections.emptyList());
        when(playbookService.list()).thenReturn(Collections.emptyList());
        when(thesisService.list()).thenReturn(Collections.emptyList());
        when(reviewService.list()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/strategy/overview")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holdings").isArray()).andExpect(jsonPath("$.data.targetWeight").value(0));
    }

    @Test
    void createsHoldingWithRevision() throws Exception {
        StrategyHolding value=new StrategyHolding();value.setId(1L);value.setCode("020608");value.setName("测试基金");value.setType("FUND");value.setRole("CORE");value.setTargetWeight(60);value.setRevision(0);
        when(holdingService.add(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                any(), any(), anyString())).thenReturn(value);
        mockMvc.perform(post("/api/strategy/holdings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"020608\",\"type\":\"FUND\",\"role\":\"CORE\",\"targetWeight\":60,\"currentWeight\":0,\"note\":\"长期核心\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("020608"))
                .andExpect(jsonPath("$.data.revision").value(0));
    }

    @Test
    void allowsPatchFromLocalFrontend() throws Exception {
        StrategyStockThesis value = new StrategyStockThesis();
        value.setId(1L);
        value.setStage("WATCH_POOL");
        when(thesisService.update(org.mockito.ArgumentMatchers.eq(1L), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(value);

        mockMvc.perform(patch("/api/strategy/stock-theses/1")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"WATCH_POOL\",\"thesis\":\"逻辑\",\"buyConditions\":\"买入\","
                                + "\"invalidationConditions\":\"失效\",\"watchFocus\":\"观察\",\"note\":\"\",\"revision\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("WATCH_POOL"));
    }

    @Test
    void getsPlaybookDetailWithProvenanceAndRules() throws Exception {
        StrategyPlaybook value = playbook();
        StrategyPlaybookRule rule = rule();
        when(playbookService.get(value.getCode()))
                .thenReturn(StrategyPlaybookView.of(value, Collections.singletonList(rule)));

        mockMvc.perform(get("/api/strategy/playbooks/{code}", value.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author").value("陈潇"))
                .andExpect(jsonPath("$.data.sourceTitle").value("《中长线股票策略基础》"))
                .andExpect(jsonPath("$.data.rules[0].sectionTitle").value("基本面筛选"))
                .andExpect(jsonPath("$.data.rules[0].sourcePage").value(10));
    }

    @Test
    void createsDatabaseBackedPlaybook() throws Exception {
        StrategyPlaybook value = playbook();
        when(playbookService.create(any(StrategyPlaybook.class), any()))
                .thenReturn(StrategyPlaybookView.of(value, Collections.singletonList(rule())));

        mockMvc.perform(post("/api/strategy/playbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"STOCK_QUALITY_TREND_CHEN_XIAO_2020\"," +
                                "\"title\":\"质量趋势中长线\",\"scope\":\"股票\"," +
                                "\"summary\":\"基本面与趋势结合\",\"cadence\":\"周线观察\"," +
                                "\"riskBoundary\":\"不抄底\",\"author\":\"陈潇\"," +
                                "\"sourceTitle\":\"《中长线股票策略基础》\",\"sourceType\":\"BOOK\"," +
                                "\"sourceRef\":\"local-pdf:chen-xiao\",\"sourcePublishedAt\":\"2020\"," +
                                "\"validationStatus\":\"UNVALIDATED\",\"status\":\"RESEARCHING\"," +
                                "\"rules\":[{\"sectionCode\":\"FUNDAMENTAL\"," +
                                "\"sectionTitle\":\"基本面筛选\",\"ruleType\":\"FILTER\"," +
                                "\"ruleText\":\"先看盈利质量\",\"testability\":\"CANDIDATE_RULE\"," +
                                "\"sourcePage\":10,\"sortOrder\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("STOCK_QUALITY_TREND_CHEN_XIAO_2020"));
    }

    private StrategyPlaybook playbook() {
        StrategyPlaybook value = new StrategyPlaybook();
        value.setId(9L);
        value.setCode("STOCK_QUALITY_TREND_CHEN_XIAO_2020");
        value.setTitle("质量趋势中长线");
        value.setScope("股票");
        value.setSummary("基本面与趋势结合");
        value.setCadence("周线观察");
        value.setRiskBoundary("不抄底");
        value.setAuthor("陈潇");
        value.setSourceTitle("《中长线股票策略基础》");
        value.setSourceType("BOOK");
        value.setSourceRef("local-pdf:chen-xiao");
        value.setSourcePublishedAt("2020");
        value.setValidationStatus("UNVALIDATED");
        value.setStatus("RESEARCHING");
        return value;
    }

    private StrategyPlaybookRule rule() {
        StrategyPlaybookRule rule = new StrategyPlaybookRule();
        rule.setSectionCode("FUNDAMENTAL");
        rule.setSectionTitle("基本面筛选");
        rule.setRuleType("FILTER");
        rule.setRuleText("先看盈利质量");
        rule.setTestability("CANDIDATE_RULE");
        rule.setSourcePage(10);
        rule.setSortOrder(1);
        return rule;
    }
}
