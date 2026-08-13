package com.finscope.web.controller;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.common.enums.factorresearch.FactorLifecycleStatus;
import com.finscope.domain.factorresearch.ResearchFactorDefinition;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.service.factorresearch.CapitalFlowFreezeService;
import com.finscope.service.factorresearch.ResearchFactorCatalog;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FactorResearchController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class FactorResearchControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ResearchFactorCatalog catalog;
    @MockBean private CapitalFlowFreezeService freezeService;

    @Test
    void exposesProfessionalExploratoryFactorDefinition() throws Exception {
        when(catalog.get("capital", "MAIN_FLOW_SHARE", "1.0.0")).thenReturn(definition());

        mockMvc.perform(get("/api/factor-research/factors/capital/MAIN_FLOW_SHARE/versions/1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plainMeaning").value("主力净流入占当日成交额的比例"))
                .andExpect(jsonPath("$.data.status").value("EXPLORATORY"))
                .andExpect(jsonPath("$.data.interpretationBoundary").isNotEmpty())
                .andExpect(jsonPath("$.data.identity.namespace").value("capital"));
    }

    @Test
    void freezesCapitalFlowAndReturnsTheResultingDataset() throws Exception {
        QuantDataset dataset = new QuantDataset();
        dataset.setId(7L); dataset.setStatus("BLOCKED"); dataset.setQualitySummary("{\"issueCodes\":[\"backfilled\"]}");
        when(freezeService.freeze(eq(7L), eq(LocalDate.of(2024, 1, 1)),
                eq(LocalDate.of(2024, 6, 30)), eq(LocalDateTime.of(2026, 7, 15, 15, 30))))
                .thenReturn(dataset);

        mockMvc.perform(post("/api/factor-research/datasets/7/capital-flow-freeze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2024-01-01\",\"to\":\"2024-06-30\",\"asOfTime\":\"2026-07-15T15:30:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.qualitySummary").isNotEmpty());
    }

    @Test
    void rejectsMissingAsOfAndReversedRangeThroughCommonErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/factor-research/datasets/7/capital-flow-freeze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2024-01-01\",\"to\":\"2024-06-30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1002"));

        mockMvc.perform(post("/api/factor-research/datasets/7/capital-flow-freeze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2024-06-30\",\"to\":\"2024-01-01\",\"asOfTime\":\"2026-07-15T15:30:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FS-1002"));
    }

    @Test
    void returnsConflictWhenReadyDatasetCannotBeMutated() throws Exception {
        when(freezeService.freeze(eq(7L), eq(LocalDate.of(2024, 1, 1)),
                eq(LocalDate.of(2024, 6, 30)), eq(LocalDateTime.of(2026, 7, 15, 15, 30))))
                .thenThrow(new BusinessException(ErrorCode.BUSINESS_CONFLICT, "已就绪数据集不可原地修改，请创建新版本"));

        mockMvc.perform(post("/api/factor-research/datasets/7/capital-flow-freeze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"2024-01-01\",\"to\":\"2024-06-30\",\"asOfTime\":\"2026-07-15T15:30:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FS-2002"));
    }

    @Test
    void returnsNotFoundForUnknownFactorVersion() throws Exception {
        when(catalog.get("capital", "UNKNOWN", "1.0.0"))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "研究因子版本不存在"));

        mockMvc.perform(get("/api/factor-research/factors/capital/UNKNOWN/versions/1.0.0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FS-2001"));
    }

    private ResearchFactorDefinition definition() {
        return ResearchFactorDefinition.builder()
                .identity(new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"))
                .name("主力流入强度").category("资金行为").frequency("DAILY")
                .expectedDirection("POSITIVE").plainMeaning("主力净流入占当日成交额的比例")
                .hypothesis("持续的主力净流入可能对应短期需求压力")
                .economicRationale("用成交额归一化后比较不同规模标的的资金流强度")
                .interpretationBoundary("它描述资金流强度，不等于主力身份识别，也不构成买卖建议")
                .requiredFields(Arrays.asList("mainNetInflow", "amount", "availableAt"))
                .availableAtRule("availableAt 必须等于源数据 retrievedAt")
                .missingPolicy("任一必需字段缺失时返回 MISSING_INPUT")
                .calculationKey("mainNetInflow/amount").calculationVersion("main-flow-share-v1")
                .sourceType("FROZEN_CAPITAL_FLOW").sourceRef("market_capital_flow_snapshot.DAY_1")
                .evaluationPolicyCode("A1_POINT_IN_TIME").evaluationPolicyVersion("1.0.0")
                .status(FactorLifecycleStatus.EXPLORATORY).build();
    }
}
