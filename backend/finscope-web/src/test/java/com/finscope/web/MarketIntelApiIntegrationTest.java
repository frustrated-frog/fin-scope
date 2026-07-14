package com.finscope.web;

import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.rpc.marketintel.CapitalFlowProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"finscope.data-root=target/test-data/market-intel-api","spring.datasource.url=jdbc:sqlite:target/test-data/market-intel-api/finance.db"})
@AutoConfigureMockMvc
@DirtiesContext(classMode= DirtiesContext.ClassMode.AFTER_CLASS)
class MarketIntelApiIntegrationTest {
    @Resource MockMvc mvc;@Resource JdbcTemplate jdbc;
    @MockBean CapitalFlowProvider provider;@MockBean LlmChatClient llm;
    @MockBean(name="marketIntelRefreshExecutor") Executor refreshExecutor;
    @MockBean(name="marketIntelAgentExecutor") Executor agentExecutor;

    @BeforeEach void setUp(){
        jdbc.update("DELETE FROM market_capital_interpretation");jdbc.update("DELETE FROM market_capital_behavior_snapshot");jdbc.update("DELETE FROM market_capital_flow_snapshot");
        jdbc.update("DELETE FROM market_intel_refresh_step");jdbc.update("DELETE FROM market_intel_refresh_run");jdbc.update("DELETE FROM agent_run");jdbc.update("DELETE FROM watchlist_item");jdbc.update("DELETE FROM instrument");
        jdbc.update("INSERT INTO instrument(id,code,type,name,market,created_at,updated_at) VALUES(7,'600519','STOCK','贵州茅台','SH',?,?)",LocalDateTime.now().toString(),LocalDateTime.now().toString());
        doAnswer(invocation->{((Runnable)invocation.getArgument(0)).run();return null;}).when(refreshExecutor).execute(any(Runnable.class));
        doAnswer(invocation->{((Runnable)invocation.getArgument(0)).run();return null;}).when(agentExecutor).execute(any(Runnable.class));
        when(provider.providerCode()).thenReturn("TEST");when(provider.supports(any())).thenReturn(true);when(provider.fetch(any(),any())).thenReturn(fixture());
    }

    @Test void refreshThenQueryReturnsRuleExplanationWithoutCallingLlm() throws Exception{
        mvc.perform(post("/api/market-intel/instruments/7/refresh")).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/market-intel/instruments/7/capital-behavior?range=20d&granularity=5m")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleExplanation.ruleVersion").value("capital-rules-v1"))
                .andExpect(jsonPath("$.intradayTimeline").isArray()).andExpect(jsonPath("$.health.status").value("FRESH"));
        verifyNoInteractions(llm);
    }

    @Test void clickInterpretationRunsAgentAndReturnsGuardedHypothesis() throws Exception{
        mvc.perform(post("/api/market-intel/instruments/7/refresh")).andExpect(status().isAccepted());
        Long evidenceId=jdbc.queryForObject("SELECT id FROM market_capital_flow_snapshot WHERE instrument_id=7 ORDER BY observed_at DESC LIMIT 1",Long.class);
        when(llm.isConfigured()).thenReturn(true);when(llm.modelName()).thenReturn("test-model");
        when(llm.complete(anyString(),anyString(),anyInt())).thenReturn("{\"plainSummary\":\"可能存在拆单\",\"hypotheses\":[{\"type\":\"ORDER_SPLITTING\",\"claim\":\"连续小额成交可能对应拆单\",\"confidence\":\"HIGH\",\"supportingMetricRefs\":[\"flow:"+evidenceId+":mainNetInflow\"],\"counterEvidence\":[],\"dataGaps\":[]}],\"dataGaps\":[\"缺少逐笔\"],\"observationPoints\":[\"观察尾盘\"],\"disclaimer\":\"不构成投资建议\"}");
        MvcResult created=mvc.perform(post("/api/market-intel/instruments/7/capital-interpretations")).andExpect(status().isAccepted()).andExpect(jsonPath("$.id").isNumber()).andReturn();
        long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(get("/api/market-intel/capital-interpretations/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.facts").isArray()).andExpect(jsonPath("$.hypotheses[0].confidence").value("LOW"));
    }

    private CapitalFlowData fixture(){LocalDate today=LocalDate.now();CapitalFlowPoint minute=point("MINUTE_1",today.atTime(10,30),new BigDecimal("120000000"),new BigDecimal("18000000"),"minute");
        CapitalFlowPoint previous=point("DAY_1",today.minusDays(1).atTime(15,0),new BigDecimal("100000000"),new BigDecimal("20000000"),"daily-1");
        CapitalFlowPoint latest=point("DAY_1",today.atTime(15,0),new BigDecimal("180000000"),new BigDecimal("-30000000"),"daily-2");
        return new CapitalFlowData(Collections.singletonList(minute),Arrays.asList(previous,latest),new BigDecimal("3.21"),new BigDecimal("1.67"),Collections.emptyList(),"TEST");}
    private CapitalFlowPoint point(String granularity,LocalDateTime at,BigDecimal amount,BigDecimal flow,String hash){CapitalFlowPoint p=new CapitalFlowPoint();p.setInstrumentId(7L);p.setProviderCode("TEST");p.setGranularity(granularity);p.setDataDate(at.toLocalDate());p.setObservedAt(at);p.setPrice(new BigDecimal("100"));p.setIntervalTradeAmount(amount);p.setMainNetInflow(flow);p.setTurnoverRate(new BigDecimal("3.21"));p.setVolumeRatio(new BigDecimal("1.67"));p.setCalculationVersion("test-v1");p.setRetrievedAt(LocalDateTime.now());p.setPayloadHash(hash);p.setQualityStatus("COMPLETE");return p;}
}
