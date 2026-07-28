package com.finscope.web;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.DragonTigerRecord;
import com.finscope.domain.marketintel.DragonTigerSeat;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.rpc.marketintel.CapitalFlowProvider;
import com.finscope.rpc.marketintel.DragonTigerData;
import com.finscope.rpc.marketintel.DragonTigerProvider;
import com.finscope.rpc.marketintel.ProviderContractException;
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
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"finscope.data-root=target/test-data/market-intel-api","spring.datasource.url=jdbc:sqlite:target/test-data/market-intel-api/finance.db"})
@AutoConfigureMockMvc
@DirtiesContext(classMode= DirtiesContext.ClassMode.AFTER_CLASS)
class MarketIntelApiIntegrationTest {
    @Resource MockMvc mvc;@Resource JdbcTemplate jdbc;
    @MockBean(name="pythonMarketDataCapitalFlowProvider") CapitalFlowProvider provider;
    @MockBean(name="eastmoneyCapitalFlowProvider") CapitalFlowProvider fallbackProvider;
    @MockBean(name="eastmoneyDragonTigerProvider") DragonTigerProvider dragonTigerProvider;
    @MockBean LlmChatClient llm;
    @MockBean(name="marketIntelRefreshExecutor") Executor refreshExecutor;
    @MockBean(name="marketIntelAgentExecutor") Executor agentExecutor;

    @BeforeEach void setUp(){
        jdbc.update("DELETE FROM market_capital_interpretation");jdbc.update("DELETE FROM market_capital_behavior_evaluation");jdbc.update("DELETE FROM market_capital_behavior_snapshot");jdbc.update("DELETE FROM market_capital_flow_snapshot");
        jdbc.update("DELETE FROM market_dragon_tiger_seat");jdbc.update("DELETE FROM market_dragon_tiger_record");
        jdbc.update("DELETE FROM market_intel_refresh_step");jdbc.update("DELETE FROM market_intel_refresh_run");jdbc.update("DELETE FROM agent_run");jdbc.update("DELETE FROM watchlist_item");jdbc.update("DELETE FROM instrument");
        jdbc.update("INSERT INTO instrument(id,code,type,name,market,created_at,updated_at) VALUES(7,'600519','STOCK','贵州茅台','SH',?,?)",LocalDateTime.now().toString(),LocalDateTime.now().toString());
        doAnswer(invocation->{((Runnable)invocation.getArgument(0)).run();return null;}).when(refreshExecutor).execute(any(Runnable.class));
        doAnswer(invocation->{((Runnable)invocation.getArgument(0)).run();return null;}).when(agentExecutor).execute(any(Runnable.class));
        when(provider.providerCode()).thenReturn("TEST");when(provider.providerFamily()).thenReturn("TEST_FAMILY");
        when(provider.capabilities()).thenReturn(Collections.singleton(com.finscope.domain.marketdata.MarketDataCapability.CAPITAL_FLOW_5M));
        when(provider.supports(com.finscope.domain.marketdata.MarketDataCapability.CAPITAL_FLOW_5M)).thenReturn(true);
        when(provider.priority()).thenReturn(10);when(provider.batchLimit()).thenReturn(1);
        when(provider.minimumInterval()).thenReturn(Duration.ZERO);when(provider.timeout()).thenReturn(Duration.ofSeconds(1));
        when(provider.supports(any(Instrument.class))).thenReturn(true);when(provider.fetch(any(),any())).thenReturn(fixture());
        when(dragonTigerProvider.providerCode()).thenReturn("TEST_DRAGON_TIGER");
        when(dragonTigerProvider.providerFamily()).thenReturn("TEST_FAMILY");
        when(dragonTigerProvider.capabilities()).thenReturn(Collections.singleton(
                com.finscope.domain.marketdata.MarketDataCapability.DRAGON_TIGER));
        when(dragonTigerProvider.supports(
                com.finscope.domain.marketdata.MarketDataCapability.DRAGON_TIGER)).thenReturn(true);
        when(dragonTigerProvider.priority()).thenReturn(10);
        when(dragonTigerProvider.batchLimit()).thenReturn(1);
        when(dragonTigerProvider.minimumInterval()).thenReturn(Duration.ZERO);
        when(dragonTigerProvider.timeout()).thenReturn(Duration.ofSeconds(1));
        when(dragonTigerProvider.supports(any(Instrument.class))).thenReturn(true);
        when(dragonTigerProvider.fetch(any(), any(), any())).thenReturn(
                ProviderResult.of(new DragonTigerData(
                                Collections.emptyList(), Collections.emptyList()),
                        LocalDateTime.now(), "empty-lhb", Collections.emptyList()));
    }

    @Test void refreshThenQueryReturnsRuleExplanationWithoutCallingLlm() throws Exception{
        MvcResult refresh=mvc.perform(post("/api/market-intel/instruments/7/refresh")).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PENDING")).andReturn();
        long runId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(refresh.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(get("/api/market-intel/refresh-runs/"+runId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        mvc.perform(get("/api/market-intel/instruments/7/capital-behavior?range=20d&granularity=5m")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ruleExplanation.ruleVersion").value("capital-rules-v2"))
                .andExpect(jsonPath("$.data.factorVersion").value("capital-factor-v1"))
                .andExpect(jsonPath("$.data.signalVersion").value("capital-signal-v2"))
                .andExpect(jsonPath("$.data.historicalEvaluation.evaluationVersion").value("capital-evaluation-v2"))
                .andExpect(jsonPath("$.data.historicalEvaluation.status").value("DATA_UNRELIABLE"))
                .andExpect(jsonPath("$.data.historicalEvaluation.historyQualityStatus").value("DATA_UNRELIABLE"))
                .andExpect(jsonPath("$.data.historicalEvaluation.dataGaps[0]").value("历史日线仅 2 个交易日，至少需要 60 个。"))
                .andExpect(jsonPath("$.data.historicalEvaluation.signals").isArray())
                .andExpect(jsonPath("$.data.factorObservations").isArray())
                .andExpect(jsonPath("$.data.metrics.latest.tradeAmount").value(180000000))
                .andExpect(jsonPath("$.data.metrics.latest.tradeVolume").value(1210000))
                .andExpect(jsonPath("$.data.metrics.latest.volumeRatio").value(1.67))
                .andExpect(jsonPath("$.data.metrics.latest.mainNetInflowSharePct").value(-16.666667))
                .andExpect(jsonPath("$.data.metrics.intradayStreak.direction").value("INFLOW"))
                .andExpect(jsonPath("$.data.metrics.dailyStreak.direction").value("OUTFLOW"))
                .andExpect(jsonPath("$.data.metrics.objectiveTags[0].code").value("AMOUNT_EXPANSION_WITH_OUTFLOW"))
                .andExpect(jsonPath("$.data.intradayTimeline").isArray()).andExpect(jsonPath("$.data.health.status").value("FRESH_PRIMARY"));
        verifyNoInteractions(llm);
    }

    @Test void instrumentWithoutSnapshotReturnsNormalEmptyState() throws Exception{
        mvc.perform(get("/api/market-intel/instruments/7/capital-behavior?range=20d&granularity=5m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instrument.id").value(7))
                .andExpect(jsonPath("$.data.health.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.snapshot").doesNotExist())
                .andExpect(jsonPath("$.data.intradayTimeline").isEmpty())
                .andExpect(jsonPath("$.data.dailyTrend").isEmpty());
        verifyNoInteractions(provider,llm);
    }

    @Test void failedRefreshReturnsProviderErrorDetails() throws Exception{
        when(provider.fetch(any(),any())).thenThrow(new ProviderContractException(
                "ALL_FUND_FLOW_SOURCES_FAILED", "东财资金流接口暂不可用，请稍后重试", true));
        MvcResult refresh=mvc.perform(post("/api/market-intel/instruments/7/refresh"))
                .andExpect(status().isAccepted()).andReturn();
        long runId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(refresh.getResponse().getContentAsString()).path("data").path("id").asLong();

        mvc.perform(get("/api/market-intel/refresh-runs/"+runId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.errorType").value("ALL_FUND_FLOW_SOURCES_FAILED"))
                .andExpect(jsonPath("$.data.errorMessage").value("东财资金流接口暂不可用，请稍后重试"));
    }

    @Test void partialRefreshPersistsAndReturnsProviderWarnings() throws Exception{
        CapitalFlowData complete=fixture();
        when(provider.fetch(any(),any())).thenReturn(new CapitalFlowData(
                complete.getMinutePoints(), complete.getDailyPoints(), complete.getTurnoverRate(),
                complete.getVolumeRatio(), Collections.singletonList("实时行情接口暂不可用"), "TEST"));
        MvcResult refresh=mvc.perform(post("/api/market-intel/instruments/7/refresh"))
                .andExpect(status().isAccepted()).andReturn();
        long runId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(refresh.getResponse().getContentAsString()).path("data").path("id").asLong();

        mvc.perform(get("/api/market-intel/refresh-runs/"+runId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.errorType").value("PARTIAL_DATA"))
                .andExpect(jsonPath("$.data.errorMessage").value("实时行情接口暂不可用"));
        mvc.perform(get("/api/market-intel/instruments/7/capital-behavior?range=20d&granularity=5m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshot.qualityStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.data.snapshot.warnings[0]").value("实时行情接口暂不可用"))
                .andExpect(jsonPath("$.data.health.status").value("PARTIAL_FRESH"))
                .andExpect(jsonPath("$.data.health.warnings[0]").value("实时行情接口暂不可用"));
    }

    @Test void clickInterpretationRunsAgentAndReturnsGuardedHypothesis() throws Exception{
        mvc.perform(post("/api/market-intel/instruments/7/refresh")).andExpect(status().isAccepted());
        Long evidenceId=jdbc.queryForObject("SELECT id FROM market_capital_flow_snapshot WHERE instrument_id=7 ORDER BY observed_at DESC LIMIT 1",Long.class);
        when(llm.isConfigured()).thenReturn(true);when(llm.modelName()).thenReturn("test-model");
        String latestDaily=jdbc.queryForObject("SELECT observed_at FROM market_capital_flow_snapshot WHERE instrument_id=7 AND granularity='DAY_1' ORDER BY observed_at DESC LIMIT 1",String.class);
        String metricRef="flow:"+evidenceId+":mainNetInflow";
        String observations="{\"dimension\":\"VOLUME\",\"claim\":\"量能有所放大\",\"factorRefs\":[\"factor:AMOUNT_RATIO_5D:"+latestDaily+"\"],\"metricRefs\":[\""+metricRef+"\"]},"
                +"{\"dimension\":\"FLOW\",\"claim\":\"资金方向偏弱\",\"factorRefs\":[\"factor:MAIN_FLOW_SHARE:"+latestDaily+"\"],\"metricRefs\":[\""+metricRef+"\"]},"
                +"{\"dimension\":\"MULTI_PERIOD\",\"claim\":\"多周期资金表现分化\",\"factorRefs\":[\"factor:MAIN_FLOW_SUM_5D:"+latestDaily+"\"],\"metricRefs\":[\""+metricRef+"\"]}";
        when(llm.complete(anyString(),anyString(),anyInt())).thenReturn("{\"marketState\":\"MIXED\",\"executiveSummary\":\"可能存在拆单\",\"observations\":["+observations+"],\"hypotheses\":[{\"type\":\"ORDER_SPLITTING\",\"claim\":\"连续小额成交可能对应拆单\",\"confidence\":\"HIGH\",\"supportingMetricRefs\":[\""+metricRef+"\"],\"counterEvidence\":[],\"dataGaps\":[]}],\"counterEvidence\":[],\"watchConditionRefs\":[],\"dataGaps\":[\"缺少逐笔\"],\"confidence\":\"MID\",\"disclaimer\":\"不构成投资建议\"}");
        MvcResult created=mvc.perform(post("/api/market-intel/instruments/7/capital-interpretations")).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.id").isNumber()).andReturn();
        long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(get("/api/market-intel/capital-interpretations/"+id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.facts").isArray())
                .andExpect(jsonPath("$.data.factorVersion").value("capital-factor-v1"))
                .andExpect(jsonPath("$.data.signalVersion").value("capital-signal-v2"))
                .andExpect(jsonPath("$.data.hypotheses[0].confidence").value("LOW"));
        mvc.perform(get("/api/market-intel/instruments/7/capital-interpretations/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        mvc.perform(post("/api/market-intel/instruments/7/capital-interpretations"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(id));

        assertEquals("SUCCESS", jdbc.queryForObject(
                "SELECT status FROM agent_run WHERE subject_type='CAPITAL_INTERPRETATION' AND subject_id=?",
                String.class, id));
        assertEquals(0, jdbc.queryForObject(
                "SELECT fallback_used FROM agent_run WHERE subject_type='CAPITAL_INTERPRETATION' AND subject_id=?",
                Integer.class, id).intValue());
        String budget = jdbc.queryForObject(
                "SELECT budget_snapshot FROM agent_run WHERE subject_type='CAPITAL_INTERPRETATION' AND subject_id=?",
                String.class, id);
        assertTrue(budget.contains("\"llmCallCount\":1"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run WHERE subject_type='CAPITAL_INTERPRETATION' AND subject_id=?",
                Integer.class, id).intValue());
    }

    @Test void returnsPersistedDragonTigerFactsAndSeats() throws Exception {
        DragonTigerRecord record = dragonTigerRecord();
        when(dragonTigerProvider.fetch(any(), any(), any())).thenReturn(
                ProviderResult.of(new DragonTigerData(
                                Collections.singletonList(record), Collections.emptyList()),
                        LocalDateTime.now(), "lhb-record", Collections.emptyList()));

        mvc.perform(post("/api/market-intel/instruments/7/refresh"))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/market-intel/instruments/7/dragon-tiger?days=120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.range.days").value(120))
                .andExpect(jsonPath("$.data.records[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].buySeats[0].direction").value("BUY"))
                .andExpect(jsonPath("$.data.health.status").value("FRESH_PRIMARY"));
    }

    @Test void returns200ForAConfirmedEmptyDragonTigerWindow() throws Exception {
        mvc.perform(post("/api/market-intel/instruments/7/refresh"))
                .andExpect(status().isAccepted());

        mvc.perform(get("/api/market-intel/instruments/7/dragon-tiger?days=120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isEmpty())
                .andExpect(jsonPath("$.data.health.status").value("FRESH_PRIMARY"));
    }

    @Test void rejectsUnsupportedDragonTigerRange() throws Exception {
        mvc.perform(get("/api/market-intel/instruments/7/dragon-tiger?days=15"))
                .andExpect(status().isBadRequest());
    }

    private CapitalFlowData fixture(){LocalDateTime now=LocalDateTime.now();CapitalFlowPoint minute=point("MINUTE_1",now.minusMinutes(1),new BigDecimal("120000000"),new BigDecimal("18000000"),"minute");minute.setTradeVolume(new BigDecimal("81000"));minute.setCumulativeTradeAmount(new BigDecimal("120000000"));
        CapitalFlowPoint previous=point("DAY_1",now.minusDays(1),new BigDecimal("100000000"),new BigDecimal("20000000"),"daily-1");
        CapitalFlowPoint latest=point("DAY_1",now,new BigDecimal("180000000"),new BigDecimal("-30000000"),"daily-2");latest.setTradeVolume(new BigDecimal("1210000"));
        return new CapitalFlowData(Collections.singletonList(minute),Arrays.asList(previous,latest),new BigDecimal("3.21"),new BigDecimal("1.67"),Collections.emptyList(),"TEST");}
    private CapitalFlowPoint point(String granularity,LocalDateTime at,BigDecimal amount,BigDecimal flow,String hash){CapitalFlowPoint p=new CapitalFlowPoint();p.setInstrumentId(7L);p.setProviderCode("TEST");p.setGranularity(granularity);p.setDataDate(at.toLocalDate());p.setObservedAt(at);p.setPrice(new BigDecimal("100"));p.setIntervalTradeAmount(amount);p.setMainNetInflow(flow);p.setTurnoverRate(new BigDecimal("3.21"));p.setVolumeRatio(new BigDecimal("1.67"));p.setCalculationVersion("test-v1");p.setRetrievedAt(LocalDateTime.now());p.setPayloadHash(hash);p.setQualityStatus("COMPLETE");return p;}

    private DragonTigerRecord dragonTigerRecord() {
        DragonTigerSeat seat = new DragonTigerSeat();
        seat.setExternalTradeId("100373909");
        seat.setSeatCode("0");
        seat.setSeatName("机构专用");
        seat.setDirection("BUY");
        seat.setRank(1);
        seat.setBuyAmount(new BigDecimal("206268197.34"));
        seat.setInstitutional(true);
        seat.setRetrievedAt(LocalDateTime.now());
        seat.setPayloadHash("lhb-seat");
        DragonTigerRecord record = new DragonTigerRecord();
        record.setInstrumentId(7L);
        record.setProviderCode("TEST_DRAGON_TIGER");
        record.setTradeDate(LocalDate.now().minusDays(1));
        record.setExternalId("100373909");
        record.setReason("日跌幅偏离值达到7%的前5只证券");
        record.setNetAmount(new BigDecimal("-395870676.13"));
        record.setRetrievedAt(LocalDateTime.now());
        record.setPayloadHash("lhb-record");
        record.setQualityStatus("COMPLETE");
        record.setSeats(Collections.singletonList(seat));
        return record;
    }
}
