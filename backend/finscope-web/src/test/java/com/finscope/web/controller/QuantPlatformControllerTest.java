package com.finscope.web.controller;

import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantDataSyncRun;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.domain.quant.factor.FactorDefinition;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import com.finscope.domain.quant.strategy.QuantStrategyVersion;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.service.quant.data.QuantMarketDataSyncService;
import com.finscope.service.quant.experiment.QuantExperimentService;
import com.finscope.service.quant.factor.FactorRegistry;
import com.finscope.service.quant.factor.DatasetFactorAnalysisService;
import com.finscope.service.quant.strategy.QuantStrategyService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest({QuantDatasetController.class, QuantController.class})
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class QuantPlatformControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private QuantDatasetService datasets;
    @MockBean private QuantMarketDataSyncService marketDataSync;
    @MockBean private FactorRegistry factors;
    @MockBean private DatasetFactorAnalysisService factorAnalysis;
    @MockBean private QuantStrategyService strategies;
    @MockBean private QuantExperimentService experiments;

    @Test
    void listsTheAuditableFactorCatalog() throws Exception {
        when(factors.list()).thenReturn(Collections.singletonList(new FactorDefinition(
                "EP", "盈利收益率", "价值", "HIGH", "市盈率倒数", 0, true)));

        mockMvc.perform(get("/api/quant/factors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("EP"))
                .andExpect(jsonPath("$.data[0].pointInTime").value(true));
    }

    @Test
    void createsLearningDatasetWithExplicitDataKind() throws Exception {
        QuantDataset value = new QuantDataset(); value.setId(7L); value.setName("多因子学习样本");
        value.setDataKind("LEARNING_SAMPLE"); value.setStatus("READY");
        when(datasets.createLearningSample(anyString())).thenReturn(value);

        mockMvc.perform(post("/api/quant/datasets/learning-sample")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"多因子学习样本\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.dataKind").value("LEARNING_SAMPLE"))
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    void createsAnEmptyRealDatasetBeforeControlledImports() throws Exception {
        QuantDataset value = new QuantDataset(); value.setId(8L); value.setName("我的真实研究集"); value.setDataKind("REAL"); value.setStatus("EMPTY");
        when(datasets.create("我的真实研究集", "REAL")).thenReturn(value);
        mockMvc.perform(post("/api/quant/datasets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"我的真实研究集\",\"dataKind\":\"REAL\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.dataKind").value("REAL")).andExpect(jsonPath("$.data.status").value("EMPTY"));
    }

    @Test
    void keepsDraftConfirmationAndExperimentExecutionSeparate() throws Exception {
        QuantStrategyDraft draft = new QuantStrategyDraft(); draft.setId(11L); draft.setStatus("VALIDATED");
        when(strategies.generateDraft(anyLong(), anyString())).thenReturn(draft);
        QuantStrategyVersion version = new QuantStrategyVersion(); version.setId(12L); version.setName("质量价值");
        when(strategies.confirm(11L)).thenReturn(version);
        when(strategies.getVersion(12L)).thenReturn(version);
        QuantExperiment experiment = new QuantExperiment(); experiment.setId(13L); experiment.setStatus("QUEUED");
        when(experiments.create(12L)).thenReturn(experiment);

        mockMvc.perform(post("/api/quant/strategy-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":7,\"prompt\":\"低估值高质量策略\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("VALIDATED"));
        mockMvc.perform(post("/api/quant/strategy-drafts/11/confirm"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/quant/strategies/12")).andExpect(jsonPath("$.data.id").value(12));
        mockMvc.perform(get("/api/quant/strategies/12")).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(12));
        mockMvc.perform(post("/api/quant/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategyVersionId\":12}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void exposesControlledMarketDataImportsAndRejectsMissingRequiredBodyFields() throws Exception {
        QuantDataset value = new QuantDataset(); value.setId(7L); value.setStatus("READY");
        when(datasets.importBars(org.mockito.ArgumentMatchers.eq(7L), anyList())).thenReturn(value);
        mockMvc.perform(post("/api/quant/datasets/7/bars").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"));
        mockMvc.perform(post("/api/quant/experiments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startsAndListsAuditableDatasetMarketDataSyncs() throws Exception {
        QuantDataSyncRun run = new QuantDataSyncRun(19L, 7L, "MANUAL", "SUCCESS",
                30, 30, 0, 600, 0, "EASTMONEY_DIRECT", null,
                LocalDateTime.of(2026, 7, 20, 14, 0), LocalDateTime.of(2026, 7, 20, 14, 2));
        when(marketDataSync.sync(7L, "MANUAL")).thenReturn(run);
        when(marketDataSync.runs(7L)).thenReturn(Collections.singletonList(run));

        mockMvc.perform(post("/api/quant/datasets/7/market-data-sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.insertedRows").value(600));
        mockMvc.perform(get("/api/quant/datasets/7/market-data-sync-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceSummary").value("EASTMONEY_DIRECT"));
    }
}
