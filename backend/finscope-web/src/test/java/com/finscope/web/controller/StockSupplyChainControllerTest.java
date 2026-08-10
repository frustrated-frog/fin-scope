package com.finscope.web.controller;

import com.finscope.domain.supplychain.StockSupplyChainEvidence;
import com.finscope.domain.supplychain.StockSupplyChainNode;
import com.finscope.domain.supplychain.StockSupplyChainRefreshRun;
import com.finscope.domain.supplychain.StockSupplyChainSnapshot;
import com.finscope.service.supplychain.StockSupplyChainService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockSupplyChainController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class StockSupplyChainControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private StockSupplyChainService service;

    @Test
    void returnsThePersistedEvidenceMapWithoutFullSourceContent() throws Exception {
        StockSupplyChainSnapshot snapshot = new StockSupplyChainSnapshot();
        snapshot.setCompanyCode("688012");
        snapshot.setCompanyName("中微公司");
        snapshot.setSummary("公司连接上游零部件和下游晶圆制造");
        snapshot.setPosition("半导体前道设备");
        snapshot.setLimitations("部分客户未实名披露");
        snapshot.setEvidenceAsOf(LocalDate.of(2026, 3, 31));
        StockSupplyChainNode node = new StockSupplyChainNode();
        node.setLayer("UPSTREAM");
        node.setName("真空与射频零部件");
        node.setRelationType("SUPPLY");
        node.setDescription("关键设备零部件");
        node.setConfidence("HIGH");
        node.setEvidenceRefs(Arrays.asList("E1"));
        snapshot.setNodes(Arrays.asList(node));
        StockSupplyChainEvidence evidence = new StockSupplyChainEvidence();
        evidence.setEvidenceCode("E1");
        evidence.setTitle("年度报告");
        evidence.setUrl("https://example.com/report");
        evidence.setSource("example.com");
        evidence.setSourceTier("T1");
        evidence.setExcerpt(repeat("公开资料", 100));
        snapshot.setEvidence(Arrays.asList(evidence));
        StockSupplyChainRefreshRun run = new StockSupplyChainRefreshRun();
        run.setId(9L);
        run.setStatus("READY");
        run.setStage("COMPLETED");
        when(service.get("688012")).thenReturn(new StockSupplyChainService.StockSupplyChainView(
                "688012", "中微公司", snapshot, run));

        mockMvc.perform(get("/api/stocks/{code}/supply-chain", "688012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("688012"))
                .andExpect(jsonPath("$.data.snapshot.position").value("半导体前道设备"))
                .andExpect(jsonPath("$.data.snapshot.nodes[0].layer").value("UPSTREAM"))
                .andExpect(jsonPath("$.data.snapshot.evidence[0].evidenceCode").value("E1"))
                .andExpect(jsonPath("$.data.snapshot.evidence[0].excerpt").isString())
                .andExpect(jsonPath("$.data.refreshRun.status").value("READY"));
    }

    @Test
    void startsAnAsynchronousEvidenceRefresh() throws Exception {
        StockSupplyChainRefreshRun run = new StockSupplyChainRefreshRun();
        run.setId(10L);
        run.setStatus("RUNNING");
        run.setStage("QUEUED");
        when(service.refresh("688012")).thenReturn(run);

        mockMvc.perform(post("/api/stocks/{code}/supply-chain/refresh", "688012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.stage").value("QUEUED"));
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
