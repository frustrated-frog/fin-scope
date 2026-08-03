package com.finscope.service.research;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.domain.research.ResearchRun;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchRunDeletionTest {
    @Test
    void deletesCompletedRun() {
        ResearchRunRepository repository = mock(ResearchRunRepository.class);
        ResearchRun run = run(15L, "COMPLETED");
        when(repository.findById(15L)).thenReturn(Optional.of(run));

        service(repository).deleteRun(15L);

        verify(repository).deleteById(15L);
    }

    @Test
    void blocksDeletingRunThatIsStillExecuting() {
        ResearchRunRepository repository = mock(ResearchRunRepository.class);
        when(repository.findById(16L)).thenReturn(Optional.of(run(16L, "RUNNING")));

        assertThrows(BusinessConflictException.class, () -> service(repository).deleteRun(16L));

        verify(repository, never()).deleteById(16L);
    }

    private ResearchService service(ResearchRunRepository repository) {
        ResearchService service = new ResearchService();
        ReflectionTestUtils.setField(service, "researchRunRepository", repository);
        return service;
    }

    private ResearchRun run(Long id, String status) {
        ResearchRun run = new ResearchRun();
        run.setId(id);
        run.setStatus(status);
        return run;
    }
}
