package com.finscope.web.controller;

import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.ResearchDraft;
import com.finscope.service.factorresearch.CapitalResearchDraftCommand;
import com.finscope.service.factorresearch.ResearchDraftService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResearchDraftController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class ResearchDraftControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ResearchDraftService service;

    @Test
    void createsAndReadsAContextOnlyCapitalResearchDraft() throws Exception {
        ResearchDraft draft = draft();
        when(service.createFromCapitalSignal(any(CapitalResearchDraftCommand.class))).thenReturn(draft);
        when(service.get(9L)).thenReturn(draft);

        mockMvc.perform(post("/api/factor-research/research-drafts/from-capital-signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instrumentCode\":\"600519.SH\",\"instrumentName\":\"贵州茅台\","
                                + "\"observedAt\":\"2026-07-15T15:00:00\",\"signalCode\":\"PRICE_FLOW_DIVERGENCE\","
                                + "\"snapshotId\":42,\"snapshotFingerprint\":\"snapshot-fingerprint\","
                                + "\"evidenceRefs\":[\"snapshot:42\",\"daily-flow:2026-07-15\"],"
                                + "\"objectiveTags\":[\"PRICE_FLOW_DIVERGENCE\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/factor-research/research-drafts/9"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.factor.code").value("MAIN_FLOW_SHARE"))
                .andExpect(jsonPath("$.evaluationMode").value("CROSS_SECTIONAL_FACTOR_STUDY"));

        ArgumentCaptor<CapitalResearchDraftCommand> command = ArgumentCaptor.forClass(CapitalResearchDraftCommand.class);
        org.mockito.Mockito.verify(service).createFromCapitalSignal(command.capture());
        assertEquals(Arrays.asList("snapshot:42", "daily-flow:2026-07-15"), command.getValue().getEvidenceRefs());

        mockMvc.perform(get("/api/factor-research/research-drafts/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentCode").value("600519.SH"));
    }

    private ResearchDraft draft() {
        ResearchDraft value = new ResearchDraft();
        value.setId(9L); value.setSourceType("CAPITAL_BEHAVIOR");
        value.setInstrumentCode("600519.SH"); value.setInstrumentName("贵州茅台");
        value.setObservedAt(LocalDateTime.of(2026, 7, 15, 15, 0));
        value.setSignalCode("PRICE_FLOW_DIVERGENCE");
        value.setFactor(new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"));
        value.setSnapshotId(42L); value.setSnapshotFingerprint("snapshot-fingerprint");
        value.setEvidenceRefs(Arrays.asList("snapshot:42", "daily-flow:2026-07-15"));
        value.setObjectiveTags(Arrays.asList("PRICE_FLOW_DIVERGENCE"));
        value.setEvaluationMode("CROSS_SECTIONAL_FACTOR_STUDY"); value.setStatus("DRAFT");
        value.setRequiredNextSteps(Arrays.asList("冻结资金数据"));
        value.setCreatedAt(LocalDateTime.of(2026, 7, 16, 1, 0));
        return value;
    }
}
