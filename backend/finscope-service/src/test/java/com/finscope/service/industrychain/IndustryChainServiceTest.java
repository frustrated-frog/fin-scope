package com.finscope.service.industrychain;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.dao.industrychain.IndustryChainRepository;
import com.finscope.domain.industrychain.IndustryChain;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainRevision;
import com.finscope.domain.industrychain.IndustryChainStructureAssessment;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndustryChainServiceTest {

    @Test
    void createsNormalizesAndQueuesANewChain() {
        IndustryChainRepository repository = mock(IndustryChainRepository.class);
        IndustryChainGenerationExecutor executor = mock(IndustryChainGenerationExecutor.class);
        IndustryChainStructureAssessor assessor = mock(IndustryChainStructureAssessor.class);
        IndustryChain chain = chain(7L, "AI 算力");
        IndustryChainRevision revision = revision(11L, chain.getId(), LocalDateTime.now());
        when(repository.findByNormalizedName("ai 算力")).thenReturn(Optional.empty());
        when(repository.createChain("AI 算力", "ai 算力")).thenReturn(chain);
        when(repository.createRevision(7L)).thenReturn(revision);
        when(assessor.assess(null)).thenReturn(assessment("BUILDING"));
        IndustryChainService service = new IndustryChainService(repository, executor, assessor);

        IndustryChainService.Workspace workspace = service.create("  AI   算力  ");

        assertEquals(chain, workspace.getChain());
        assertEquals(revision, workspace.getRevision());
        assertEquals("BUILDING", workspace.getStructure().getStatus());
        verify(executor).schedule(chain, revision);
    }

    @Test
    void reusesAnExistingChainButRejectsADuplicateActiveRevision() {
        IndustryChainRepository repository = mock(IndustryChainRepository.class);
        IndustryChainGenerationExecutor executor = mock(IndustryChainGenerationExecutor.class);
        IndustryChainStructureAssessor assessor = mock(IndustryChainStructureAssessor.class);
        IndustryChain chain = chain(7L, "AI算力");
        when(repository.findByNormalizedName("ai算力")).thenReturn(Optional.of(chain));
        when(repository.activeRevision(7L)).thenReturn(Optional.of(revision(
                12L, 7L, LocalDateTime.now())));
        IndustryChainService service = new IndustryChainService(repository, executor, assessor);

        assertThrows(BusinessConflictException.class, () -> service.create("AI算力"));
        verify(repository, never()).createChain(any(), any());
    }

    @Test
    void expiresAStaleRevisionBeforeStartingARefresh() {
        IndustryChainRepository repository = mock(IndustryChainRepository.class);
        IndustryChainGenerationExecutor executor = mock(IndustryChainGenerationExecutor.class);
        IndustryChainStructureAssessor assessor = mock(IndustryChainStructureAssessor.class);
        IndustryChain chain = chain(7L, "AI算力");
        IndustryChainRevision stale = revision(12L, 7L, LocalDateTime.now().minusHours(1));
        IndustryChainRevision fresh = revision(13L, 7L, LocalDateTime.now());
        when(repository.findChain(7L)).thenReturn(Optional.of(chain));
        when(repository.activeRevision(7L)).thenReturn(Optional.of(stale));
        when(repository.createRevision(7L)).thenReturn(fresh);
        IndustryChainService service = new IndustryChainService(repository, executor, assessor);

        IndustryChainRevision result = service.refresh(7L);

        assertEquals(fresh, result);
        verify(repository).fail(eq(stale), eq("STALE_REVISION_EXPIRED"), any());
        verify(executor).schedule(chain, fresh);
    }

    @Test
    void exposesPublishedGraphStructureAssessmentInWorkspace() {
        IndustryChainRepository repository = mock(IndustryChainRepository.class);
        IndustryChainGenerationExecutor executor = mock(IndustryChainGenerationExecutor.class);
        IndustryChainStructureAssessor assessor = mock(IndustryChainStructureAssessor.class);
        IndustryChain chain = chain(7L, "机器人");
        IndustryChainGraph graph = new IndustryChainGraph();
        IndustryChainStructureAssessment assessment = assessment("ENRICHMENT_RECOMMENDED");
        when(repository.findChain(7L)).thenReturn(Optional.of(chain));
        when(repository.findPublishedGraph(7L)).thenReturn(Optional.of(graph));
        when(assessor.assess(graph)).thenReturn(assessment);
        IndustryChainService service = new IndustryChainService(repository, executor, assessor);

        IndustryChainService.Workspace workspace = service.get(7L);

        assertEquals(assessment, workspace.getStructure());
    }

    private IndustryChainStructureAssessment assessment(String status) {
        IndustryChainStructureAssessment value = new IndustryChainStructureAssessment();
        value.setStatus(status);
        return value;
    }

    private IndustryChain chain(Long id, String name) {
        IndustryChain value = new IndustryChain();
        value.setId(id);
        value.setName(name);
        value.setNormalizedName(name.toLowerCase());
        return value;
    }

    private IndustryChainRevision revision(Long id, Long chainId, LocalDateTime createdAt) {
        IndustryChainRevision value = new IndustryChainRevision();
        value.setId(id);
        value.setChainId(chainId);
        value.setStatus("RUNNING");
        value.setStage("QUEUED");
        value.setCreatedAt(createdAt);
        return value;
    }
}
