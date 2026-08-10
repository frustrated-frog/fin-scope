package com.finscope.service.supplychain;

import com.finscope.dao.supplychain.StockSupplyChainRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.supplychain.StockSupplyChainRefreshRun;
import com.finscope.domain.supplychain.StockSupplyChainSnapshot;
import com.finscope.service.research.evidence.ResearchEvidenceAcquisitionResult;
import com.finscope.service.search.evidence.SearchEvidence;
import com.finscope.service.search.evidence.SearchEvidenceBatch;
import com.finscope.service.search.evidence.SearchEvidenceContentService;
import com.finscope.service.search.evidence.SearchEvidenceGateway;
import com.finscope.service.search.evidence.SearchEvidenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

class StockSupplyChainRefreshExecutorTest {
    private StockSupplyChainRepository repository;
    private SearchEvidenceGateway search;
    private SearchEvidenceContentService content;
    private StockSupplyChainSynthesisAgent synthesis;
    private StockSupplyChainRefreshExecutor executor;

    @BeforeEach
    void setUp() {
        repository = mock(StockSupplyChainRepository.class);
        search = mock(SearchEvidenceGateway.class);
        content = mock(SearchEvidenceContentService.class);
        synthesis = mock(StockSupplyChainSynthesisAgent.class);
        Executor direct = Runnable::run;
        executor = new StockSupplyChainRefreshExecutor(repository, search, content, synthesis, direct);
    }

    @Test
    void freezesPublicEvidenceAndPersistsOnlyTheValidatedSnapshot() throws Exception {
        SearchEvidence hit = new SearchEvidence();
        hit.setTitle("中微公司年度报告");
        hit.setUrl("https://example.com/report");
        hit.setSourceDomain("example.com");
        hit.setSourceTier("T1");
        hit.setPublishedAt("2026-03-31");
        hit.setContent("搜索摘要");
        when(search.search(any(SearchEvidenceRequest.class))).thenReturn(new SearchEvidenceBatch(
                Arrays.asList(hit), Collections.emptyList(), false));
        when(content.acquire(eq(hit), any(String.class), eq("中微公司"), eq(true)))
                .thenReturn(new ResearchEvidenceAcquisitionResult(
                        "完整年报内容", "搜索摘要", "FULL_TEXT", "ok", "SUCCESS", 6));
        StockSupplyChainSnapshot synthesized = new StockSupplyChainSnapshot();
        when(synthesis.synthesize(eq("中微公司"), eq("688012"), anyList()))
                .thenReturn(synthesized);
        StockSupplyChainRefreshRun run = run();

        executor.execute(instrument(), run);

        ArgumentCaptor<SearchEvidenceRequest> request = ArgumentCaptor.forClass(SearchEvidenceRequest.class);
        verify(search).search(request.capture());
        assertTrue(request.getValue().getQuery().contains("中微公司"));
        assertTrue(request.getValue().getQuery().contains("供应商"));
        verify(repository).replaceSnapshotAndComplete(eq(synthesized), eq(run));
        assertTrue(synthesized.getInstrumentId() == 1L);
    }

    @Test
    void marksTheRunFailedWithoutReplacingThePreviousSnapshot() {
        when(search.search(any(SearchEvidenceRequest.class))).thenReturn(new SearchEvidenceBatch(
                Collections.emptyList(), Collections.emptyList(), true));
        StockSupplyChainRefreshRun run = run();

        executor.execute(instrument(), run);

        verify(repository, never()).replaceSnapshotAndComplete(any(), any());
        verify(repository, atLeastOnce()).updateRun(run);
        assertTrue("FAILED".equals(run.getStatus()));
        assertTrue(run.getMessage().contains("保留原产业链快照"));
    }

    private Instrument instrument() {
        Instrument value = new Instrument();
        value.setId(1L);
        value.setCode("688012");
        value.setName("中微公司");
        value.setType("STOCK");
        return value;
    }

    private StockSupplyChainRefreshRun run() {
        StockSupplyChainRefreshRun value = new StockSupplyChainRefreshRun();
        value.setId(9L);
        value.setInstrumentId(1L);
        value.setStatus("RUNNING");
        value.setStage("QUEUED");
        return value;
    }
}
