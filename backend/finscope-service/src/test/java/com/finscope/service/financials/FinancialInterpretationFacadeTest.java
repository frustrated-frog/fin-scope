package com.finscope.service.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.financials.FinancialAnalysisSnapshotRepository;
import com.finscope.dao.financials.FinancialInterpretationRepository;
import com.finscope.domain.agent.AgentTraceSubject;
import com.finscope.domain.agent.AgentNodeResult;
import com.finscope.domain.agent.AgentRunContext;
import com.finscope.domain.financials.FinancialAnalysisSnapshot;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.domain.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.instrument.Instrument;
import com.finscope.service.agent.AgentHarness;
import com.finscope.service.agent.AgentTraceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialInterpretationFacadeTest {
    private FinancialQueryService query;
    private FinancialAnalysisSnapshotRepository snapshots;
    private FinancialInterpretationRepository interpretations;
    private FinancialAnalysisPreflight preflight;
    private FinancialEvidencePacketAssembler assembler;
    private FinancialInterpretationAgent agent;
    private AgentTraceService traces;
    private FinancialInterpretationFacade facade;

    @BeforeEach
    void setUp() {
        query = mock(FinancialQueryService.class);
        snapshots = mock(FinancialAnalysisSnapshotRepository.class);
        interpretations = mock(FinancialInterpretationRepository.class);
        preflight = mock(FinancialAnalysisPreflight.class);
        assembler = mock(FinancialEvidencePacketAssembler.class);
        agent = mock(FinancialInterpretationAgent.class);
        traces = mock(AgentTraceService.class);
        when(query.view(9L)).thenReturn(view());
        when(query.listReports(7L)).thenReturn(Collections.singletonList(view().getReport()));
        when(preflight.ensureCurrent(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assembler.assemble(any(), any())).thenReturn(packet());
        when(snapshots.saveOrReuse(any())).thenAnswer(invocation -> {
            FinancialAnalysisSnapshot value = invocation.getArgument(0);
            value.setId(31L);
            return value;
        });
        when(interpretations.findRunningByReport(9L)).thenReturn(Optional.empty());
        when(interpretations.findReusable(any())).thenReturn(Optional.empty());
        when(interpretations.save(any())).thenAnswer(invocation -> {
            FinancialInterpretation value = invocation.getArgument(0);
            value.setId(41L);
            return value;
        });
        when(agent.modelName()).thenReturn("test-model");
        when(agent.interpretWithMetrics(any()))
                .thenReturn(new FinancialInterpretationAgent.Execution(success(), 1));
        facade = new FinancialInterpretationFacade(query, snapshots, interpretations, preflight,
                assembler, agent, new AgentHarness(), traces, new ObjectMapper(), Runnable::run);
    }

    @Test
    void reusesSameGenerationKeyUnlessForceIsRequested() {
        FinancialInterpretation cached = new FinancialInterpretation();
        cached.setId(77L);
        cached.setStatus("SUCCESS");
        when(interpretations.findReusable(any())).thenReturn(Optional.of(cached));

        FinancialInterpretation result = facade.request(9L, false);

        assertEquals(77L, result.getId());
        verify(interpretations, never()).save(any());
    }

    @Test
    void forceCreatesVersionCompletesItAndRecordsGenericTraceSubject() {
        FinancialInterpretation result = facade.request(9L, true);

        assertEquals(41L, result.getId());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(31L, result.getSnapshotId());
        verify(interpretations).save(any());
        verify(interpretations, org.mockito.Mockito.atLeast(2)).update(any());
        ArgumentCaptor<AgentTraceSubject> subject = ArgumentCaptor.forClass(AgentTraceSubject.class);
        ArgumentCaptor<AgentRunContext> context = ArgumentCaptor.forClass(AgentRunContext.class);
        ArgumentCaptor<AgentNodeResult> node = ArgumentCaptor.forClass(AgentNodeResult.class);
        verify(traces).recordNode(subject.capture(), context.capture(), any(), node.capture(), anyLong(), any());
        assertEquals("FINANCIAL_INTERPRETATION", subject.getValue().getType());
        assertEquals(41L, subject.getValue().getId());
        assertEquals(1, context.getValue().getLlmCallCount());
        assertEquals("SUCCESS", node.getValue().getStatus());
        assertFalse(node.getValue().isFallbackUsed());
    }

    @Test
    void recordsRuleFallbackWithoutPretendingThatTheModelWasCalled() {
        FinancialInterpretation fallback = success();
        fallback.setStatus("FALLBACK");
        fallback.setGenerationMode("RULE");
        fallback.setFailureCode("LLM_NOT_CONFIGURED");
        when(agent.interpretWithMetrics(any()))
                .thenReturn(new FinancialInterpretationAgent.Execution(fallback, 0));

        FinancialInterpretation result = facade.request(9L, true);

        assertEquals("FALLBACK", result.getStatus());
        ArgumentCaptor<AgentRunContext> context = ArgumentCaptor.forClass(AgentRunContext.class);
        ArgumentCaptor<AgentNodeResult> node = ArgumentCaptor.forClass(AgentNodeResult.class);
        verify(traces).recordNode(any(), context.capture(), any(), node.capture(), anyLong(), any());
        assertEquals(0, context.getValue().getLlmCallCount());
        assertEquals("FALLBACK", node.getValue().getStatus());
        assertTrue(node.getValue().isFallbackUsed());
        assertEquals("LLM_NOT_CONFIGURED", node.getValue().getFallbackReason());
    }

    private FinancialReportView view() {
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        instrument.setCode("600519");
        instrument.setMarket("SH");
        FinancialReport report = new FinancialReport();
        report.setId(9L);
        report.setInstrumentId(7L);
        report.setPeriodEnd(LocalDate.of(2025, 12, 31));
        report.setReportType(FinancialReportType.ANNUAL);
        report.setScope("CONSOLIDATED");
        report.setQualityStatus(FinancialQualityStatus.FRESH);
        FinancialReportView value = new FinancialReportView();
        value.setInstrument(instrument);
        value.setReport(report);
        return value;
    }

    private FinancialEvidencePacket packet() {
        FinancialEvidencePacket value = new FinancialEvidencePacket();
        value.setReportId(9L);
        value.setAlgorithmVersion("financial-analysis-v2");
        value.setPromptVersion("financial-interpret-v1");
        value.setSourceHash("source-hash");
        value.setInputHash("input-hash");
        value.setPayloadJson("{\"evidence\":[]}");
        value.setQualityCeiling("HIGH");
        return value;
    }

    private FinancialInterpretation success() {
        FinancialInterpretation value = new FinancialInterpretation();
        value.setStatus("SUCCESS");
        value.setGenerationMode("LLM");
        value.setModelName("test-model");
        value.setResult(FinancialInterpretation.Result.fallback("可核查结论"));
        return value;
    }
}
