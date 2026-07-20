package com.finscope.web;

import com.finscope.dao.factorresearch.QuantCapitalFlowRepository;
import com.finscope.dao.factorresearch.QuantDatasetPartitionRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.marketintel.CapitalFlowRepository;
import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.factorresearch.FactorObservation;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.service.factorresearch.FactorCalculationContext;
import com.finscope.service.factorresearch.FactorProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-data/capital-factor-research/finance.db?foreign_keys=on",
        "finscope.data-root=target/test-data/capital-factor-research",
        "finscope.search.enabled=false",
        "finscope.llm.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CapitalFactorResearchIntegrationTest {
    @Resource private MockMvc mvc;
    @Resource private JdbcTemplate jdbc;
    @Resource private InstrumentRepository instruments;
    @Resource private CapitalFlowRepository sourceFlows;
    @Resource private QuantDatasetRepository datasets;
    @Resource private QuantMarketDataRepository marketData;
    @Resource private QuantCapitalFlowRepository frozenFlows;
    @Resource private QuantDatasetPartitionRepository partitions;
    @Resource private FactorProviderRegistry providers;

    private Instrument sh;
    private Instrument sz;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM quant_capital_flow_daily");
        jdbc.update("DELETE FROM quant_dataset_partition");
        jdbc.update("DELETE FROM quant_universe_member");
        jdbc.update("DELETE FROM quant_daily_bar");
        jdbc.update("DELETE FROM quant_fundamental_snapshot");
        jdbc.update("DELETE FROM quant_dataset_issue");
        jdbc.update("DELETE FROM quant_dataset");
        jdbc.update("DELETE FROM market_capital_flow_snapshot");
        jdbc.update("DELETE FROM watchlist_item");
        jdbc.update("DELETE FROM instrument");
        sh = instrument("600519", "SH", "贵州茅台");
        sz = instrument("000001", "SZ", "平安银行");
    }

    @Test
    void bridgesVersionedMarketIntelRowsIntoAuditableQuantFactors() throws Exception {
        LocalDate first = LocalDate.of(2026, 7, 1);
        LocalDate second = LocalDate.of(2026, 7, 2);
        QuantDataset readyCandidate = dataset("真实可用资金研究集");
        marketData.insertUniverseMembers(universe(readyCandidate.getId(), first));
        marketData.insertBars(bars(readyCandidate.getId(), first, second));

        sourceFlows.saveAll(Arrays.asList(
                flow(sh.getId(), first, first.atTime(15, 0), "50", "1000", "sh-old"),
                flow(sh.getId(), first, first.atTime(15, 10), "200", "1000", "sh-new"),
                flow(sz.getId(), first, first.atTime(15, 5), "100", "1000", "sz-first"),
                flow(sh.getId(), second, second.atTime(15, 5), "300", "1000", "sh-second"),
                flow(sz.getId(), second, second.atTime(15, 5), "50", "1000", "sz-second")));

        mvc.perform(post("/api/factor-research/datasets/{id}/capital-flow-freeze", readyCandidate.getId())
                        .contentType("application/json")
                        .content("{\"from\":\"2026-07-01\",\"to\":\"2026-07-02\",\"asOfTime\":\"2026-07-03T09:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.fingerprintVersion").value("quant-dataset-v2"))
                .andExpect(jsonPath("$.data.partitionManifest").value(org.hamcrest.Matchers.containsString("CAPITAL_FLOW_DAILY")));

        QuantDataset ready = datasets.findById(readyCandidate.getId()).orElseThrow(AssertionError::new);
        List<QuantCapitalFlowDaily> readyRows = frozenFlows.findByDatasetId(ready.getId());
        assertEquals(4, readyRows.size(), "same-day source versions must collapse to one PIT row");
        QuantCapitalFlowDaily shFirst = row(readyRows, first, "600519.SH");
        assertEquals(new BigDecimal("0.2000000000"), shFirst.getMainFlowShare());
        assertEquals(first.atTime(15, 10), shFirst.getAvailableAt());
        assertEquals("sh-new", shFirst.getSourceFingerprint());
        assertEquals("COMPLETE", partitions.findByDatasetId(ready.getId()).get(0).getQualityStatus());
        assertNotNull(ready.getFingerprint());
        assertFalse(ready.getFingerprint().trim().isEmpty());

        FactorObservation observation = providers.calculate("MAIN_FLOW_SHARE",
                new FactorCalculationContext(String.valueOf(ready.getId()), "600519.SH", first,
                        second.atTime(9, 30), Collections.<QuantDailyBar>emptyList(), null, shFirst));
        assertEquals(new BigDecimal("0.2000000000"), observation.getProcessedValue());
        assertEquals("COMPLETE", observation.getQualityStatus().name());

        mvc.perform(get("/api/factor-research/factors/capital/MAIN_FLOW_SHARE/versions/1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPLORATORY"))
                .andExpect(jsonPath("$.data.availableAtRule").value(org.hamcrest.Matchers.containsString("retrievedAt")))
                .andExpect(jsonPath("$.data.interpretationBoundary").value(org.hamcrest.Matchers.containsString("不构成投资建议")));

        LocalDate historical = LocalDate.of(2024, 1, 2);
        QuantDataset blockedCandidate = dataset("回填资金研究集");
        marketData.insertUniverseMembers(universe(blockedCandidate.getId(), historical));
        marketData.insertBars(bars(blockedCandidate.getId(), historical));
        sourceFlows.saveAll(Arrays.asList(
                flow(sh.getId(), historical, LocalDateTime.of(2026, 7, 14, 10, 0), "200", "1000", "sh-backfill"),
                flow(sz.getId(), historical, LocalDateTime.of(2026, 7, 14, 10, 0), "100", "1000", "sz-backfill")));

        mvc.perform(post("/api/factor-research/datasets/{id}/capital-flow-freeze", blockedCandidate.getId())
                        .contentType("application/json")
                        .content("{\"from\":\"2024-01-02\",\"to\":\"2024-01-02\",\"asOfTime\":\"2026-07-15T09:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.qualitySummary").value(org.hamcrest.Matchers.containsString("backfilled")));

        QuantDataset blocked = datasets.findById(blockedCandidate.getId()).orElseThrow(AssertionError::new);
        List<QuantCapitalFlowDaily> blockedRows = frozenFlows.findByDatasetId(blocked.getId());
        assertEquals(2, blockedRows.size(), "blocked provenance remains inspectable");
        assertTrue(blockedRows.stream().allMatch(value -> "POINT_IN_TIME_BLOCKED".equals(value.getQualityStatus())));
        assertNotEquals(ready.getFingerprint(), blocked.getFingerprint());
    }

    private Instrument instrument(String code, String market, String name) {
        Instrument value = new Instrument();
        value.setCode(code); value.setMarket(market); value.setType("STOCK"); value.setName(name);
        return instruments.save(value);
    }

    private QuantDataset dataset(String name) {
        QuantDataset value = new QuantDataset();
        value.setName(name); value.setMarket("CN_A"); value.setUniverseType("CUSTOM");
        value.setSourceType("MARKET_INTEL"); value.setDataKind("REAL"); value.setDatasetLevel("RESEARCH");
        value.setFingerprintVersion("quant-dataset-v2"); value.setPartitionManifest("[]");
        value.setStatus("BUILDING"); value.setQualitySummary("{}");
        return datasets.save(value);
    }

    private List<QuantUniverseMember> universe(Long datasetId, LocalDate date) {
        return Arrays.asList(member(datasetId, date, "600519.SH"), member(datasetId, date, "000001.SZ"));
    }

    private QuantUniverseMember member(Long datasetId, LocalDate date, String code) {
        QuantUniverseMember value = new QuantUniverseMember();
        value.setDatasetId(datasetId); value.setTradeDate(date); value.setInstrumentCode(code);
        value.setMember(true); value.setSourceKind("POINT_IN_TIME");
        return value;
    }

    private List<QuantDailyBar> bars(Long datasetId, LocalDate... dates) {
        List<QuantDailyBar> result = new ArrayList<QuantDailyBar>();
        for (LocalDate date : dates) {
            result.add(bar(datasetId, date, "600519.SH", "100"));
            result.add(bar(datasetId, date, "000001.SZ", "20"));
        }
        return result;
    }

    private QuantDailyBar bar(Long datasetId, LocalDate date, String code, String price) {
        QuantDailyBar value = new QuantDailyBar();
        BigDecimal p = new BigDecimal(price);
        value.setDatasetId(datasetId); value.setTradeDate(date); value.setInstrumentCode(code);
        value.setOpen(p); value.setHigh(p); value.setLow(p); value.setClose(p); value.setAdjustedClose(p);
        value.setVolume(new BigDecimal("10000")); value.setAmount(new BigDecimal("1000000"));
        value.setTradeStatus("NORMAL");
        return value;
    }

    private CapitalFlowPoint flow(Long instrumentId, LocalDate date, LocalDateTime retrievedAt,
                                  String mainNetInflow, String amount, String hash) {
        CapitalFlowPoint value = new CapitalFlowPoint();
        value.setInstrumentId(instrumentId); value.setProviderCode("INTEGRATION_FIXTURE");
        value.setGranularity("DAY_1"); value.setDataDate(date); value.setObservedAt(date.atTime(15, 0));
        value.setIntervalTradeAmount(new BigDecimal(amount));
        value.setMainNetInflow(new BigDecimal(mainNetInflow));
        value.setCalculationVersion("fixture-v1"); value.setRetrievedAt(retrievedAt);
        value.setPayloadHash(hash); value.setQualityStatus("COMPLETE");
        return value;
    }

    private QuantCapitalFlowDaily row(List<QuantCapitalFlowDaily> values, LocalDate date, String code) {
        return values.stream().filter(value -> date.equals(value.getTradeDate())
                        && code.equals(value.getInstrumentCode()))
                .findFirst().orElseThrow(AssertionError::new);
    }
}
