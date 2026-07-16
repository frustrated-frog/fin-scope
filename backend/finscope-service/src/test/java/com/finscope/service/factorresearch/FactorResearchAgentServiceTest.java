package com.finscope.service.factorresearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.factorresearch.FactorResearchAgentRunRepository;
import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorResearchAgentRun;
import com.finscope.domain.factorresearch.ResearchDraft;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.factor.FactorAnalysis;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.service.quant.factor.DatasetFactorAnalysisService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FactorResearchAgentServiceTest {
    private final FactorResearchAgentRunRepository runs = mock(FactorResearchAgentRunRepository.class);
    private final AgentRunRepository traces = mock(AgentRunRepository.class);
    private final QuantDatasetService datasets = mock(QuantDatasetService.class);
    private final DatasetFactorAnalysisService diagnostics = mock(DatasetFactorAnalysisService.class);
    private final ResearchDraftService drafts = mock(ResearchDraftService.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);
    private final FactorResearchAgentService service = new FactorResearchAgentService(
            runs, traces, datasets, diagnostics, new ResearchFactorCatalog(), drafts, json, clock);

    @Test
    void createsAnApprovalRequiredReadOnlyPlanWithHardBudgets() {
        QuantDataset dataset = dataset(); when(datasets.get(7L)).thenReturn(dataset);
        when(runs.save(any())).thenAnswer(invocation -> { FactorResearchAgentRun value = invocation.getArgument(0); value.setId(9L); return value; });

        FactorResearchAgentRun value = service.createPlan(7L,
                new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"), null, "方向可靠吗？");

        assertEquals("AWAITING_APPROVAL", value.getStatus());
        assertEquals(4, value.getMaxToolCalls()); assertEquals(0, value.getMaxLlmCalls());
        assertTrue(value.getAllowedTools().contains("run_factor_diagnostics"));
        verify(diagnostics, never()).analyze(anyLong(), anyString());
    }

    @Test
    void refusesToPlanAgainstAMutableDatasetOrAttachADraftToAnotherFactor() {
        QuantDataset building = dataset(); building.setStatus("BUILDING");
        when(datasets.get(7L)).thenReturn(building);
        assertThrows(RuntimeException.class, () -> service.createPlan(7L,
                new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"), null, "test"));

        when(datasets.get(7L)).thenReturn(dataset());
        ResearchDraft draft = new ResearchDraft();
        draft.setId(3L); draft.setFactor(new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"));
        when(drafts.get(3L)).thenReturn(draft);
        assertThrows(RuntimeException.class, () -> service.createPlan(7L,
                new FactorIdentity("quant", "EP", "1.0.0"), 3L, "test"));
        verify(runs, never()).save(argThat(value -> Long.valueOf(3L).equals(value.getResearchDraftId())));
    }

    @Test
    void approvedRunUsesOnlyWhitelistedReadsAndPersistsEvidenceHashAndTrace() {
        FactorResearchAgentRun run = new FactorResearchAgentRun(); run.setId(9L); run.setDatasetId(7L);
        run.setDatasetFingerprint("sha"); run.setFactor(new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"));
        run.setMaxToolCalls(4); run.setMaxLlmCalls(0); run.setMaxRunSeconds(60); run.setStatus("RUNNING");
        when(runs.transition(eq(9L), anyString(), anyString(), any())).thenReturn(true);
        when(runs.findById(9L)).thenReturn(Optional.of(run)); when(traces.findBySubject(anyString(), eq(9L))).thenReturn(Collections.emptyList());
        when(datasets.get(7L)).thenReturn(dataset()); when(datasets.availableFactorCodes(7L)).thenReturn(Collections.singleton("MAIN_FLOW_SHARE"));
        FactorAnalysis analysis = new FactorAnalysis(); analysis.setFactorCode("MAIN_FLOW_SHARE"); analysis.setSampleCount(80);
        analysis.setDirectionAdjustedIcMean(0.04); analysis.setConclusion("SUPPORTED"); analysis.setValidationEligible(true);
        analysis.setCaveats(Collections.singletonList("仍需样本外检验")); when(diagnostics.analyze(7L, "MAIN_FLOW_SHARE")).thenReturn(analysis);

        service.approveAndRun(9L);

        verify(diagnostics).analyze(7L, "MAIN_FLOW_SHARE");
        verify(runs).complete(eq(9L), eq("COMPLETED"), eq(3), contains("diagnostics"), argThat(value -> value.length() == 64),
                contains("SUPPORTED"), eq("POLICY_REVIEW_COMPLETE"), any());
        verify(traces, times(4)).record(any(com.finscope.domain.agent.AgentRun.class));
    }

    @Test
    void stopsCleanlyWhenTheApprovedToolBudgetIsExhausted() {
        FactorResearchAgentRun run = new FactorResearchAgentRun(); run.setId(9L); run.setDatasetId(7L);
        run.setDatasetFingerprint("sha");
        run.setFactor(new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"));
        run.setMaxToolCalls(2); run.setMaxRunSeconds(60); run.setStatus("RUNNING");
        when(runs.transition(eq(9L), anyString(), anyString(), any())).thenReturn(true);
        when(runs.findById(9L)).thenReturn(Optional.of(run)); when(traces.findBySubject(anyString(), eq(9L))).thenReturn(Collections.emptyList());
        when(datasets.get(7L)).thenReturn(dataset()); when(datasets.availableFactorCodes(7L)).thenReturn(Collections.singleton("MAIN_FLOW_SHARE"));

        service.approveAndRun(9L);

        verify(diagnostics, never()).analyze(anyLong(), anyString());
        verify(runs).complete(eq(9L), eq("BUDGET_EXHAUSTED"), eq(2), eq("{}"), eq(""), eq("{}"),
                eq("TOOL_BUDGET_EXHAUSTED"), any());
    }

    @Test
    void failsClosedWhenTheApprovedDatasetFingerprintHasChanged() {
        FactorResearchAgentRun run = new FactorResearchAgentRun(); run.setId(9L); run.setDatasetId(7L);
        run.setDatasetFingerprint("approved-sha");
        run.setFactor(new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0"));
        run.setMaxToolCalls(4); run.setMaxRunSeconds(60); run.setStatus("RUNNING");
        QuantDataset changed = dataset(); changed.setFingerprint("changed-sha");
        when(runs.transition(eq(9L), anyString(), anyString(), any())).thenReturn(true);
        when(runs.findById(9L)).thenReturn(Optional.of(run));
        when(datasets.get(7L)).thenReturn(changed);

        assertThrows(RuntimeException.class, () -> service.approveAndRun(9L));

        verify(diagnostics, never()).analyze(anyLong(), anyString());
        verify(runs).complete(eq(9L), eq("FAILED"), eq(1), eq("{}"), eq(""), eq("{}"),
                eq("DATASET_FINGERPRINT_CHANGED"), any());
    }

    private QuantDataset dataset() { QuantDataset value = new QuantDataset(); value.setId(7L); value.setStatus("READY"); value.setDataKind("REAL"); value.setDatasetLevel("RESEARCH"); value.setFingerprint("sha"); return value; }
}
