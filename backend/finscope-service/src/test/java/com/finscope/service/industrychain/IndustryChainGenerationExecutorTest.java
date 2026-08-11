package com.finscope.service.industrychain;

import com.finscope.dao.industrychain.IndustryChainRepository;
import com.finscope.domain.industrychain.IndustryChain;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainRevision;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndustryChainGenerationExecutorTest {

    @Test
    void publishesASuccessfulRevision() throws Exception {
        IndustryChainRepository repository = mock(IndustryChainRepository.class);
        IndustryChainEvidenceCollector collector = mock(IndustryChainEvidenceCollector.class);
        IndustryChainSynthesisAgent synthesis = mock(IndustryChainSynthesisAgent.class);
        IndustryChain chain = chain();
        IndustryChainRevision revision = revision();
        IndustryChainGraph graph = new IndustryChainGraph();
        when(collector.collect("AI算力")).thenReturn(Collections.emptyList());
        when(synthesis.synthesize("AI算力", Collections.emptyList())).thenReturn(graph);
        IndustryChainGenerationExecutor executor = new IndustryChainGenerationExecutor(
                repository, collector, synthesis, Runnable::run);

        executor.execute(chain, revision);

        verify(repository).publish(revision, graph);
        verify(repository, never()).fail(eq(revision), eq("SYNTHESIS_FAILED"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void marksOnlyTheNewRevisionFailedWhenSynthesisFails() throws Exception {
        IndustryChainRepository repository = mock(IndustryChainRepository.class);
        IndustryChainEvidenceCollector collector = mock(IndustryChainEvidenceCollector.class);
        IndustryChainSynthesisAgent synthesis = mock(IndustryChainSynthesisAgent.class);
        IndustryChain chain = chain();
        IndustryChainRevision revision = revision();
        when(collector.collect("AI算力")).thenReturn(Collections.emptyList());
        when(synthesis.synthesize("AI算力", Collections.emptyList()))
                .thenThrow(new IllegalArgumentException("bad output"));
        IndustryChainGenerationExecutor executor = new IndustryChainGenerationExecutor(
                repository, collector, synthesis, Runnable::run);

        executor.execute(chain, revision);

        verify(repository).fail(eq(revision), eq("SYNTHESIS_FAILED"), org.mockito.ArgumentMatchers.anyString());
        verify(repository, never()).publish(eq(revision), org.mockito.ArgumentMatchers.any());
    }

    private IndustryChain chain() {
        IndustryChain value = new IndustryChain();
        value.setId(7L);
        value.setName("AI算力");
        return value;
    }

    private IndustryChainRevision revision() {
        IndustryChainRevision value = new IndustryChainRevision();
        value.setId(11L);
        value.setChainId(7L);
        value.setStatus("RUNNING");
        return value;
    }
}
