package com.finscope.service.knowledge;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.LearningTask;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeContextServiceTest {
    private final LearningTaskRepository tasks = mock(LearningTaskRepository.class);
    private final EvidenceItemRepository evidence = mock(EvidenceItemRepository.class);
    private final KnowledgeContextService service = new KnowledgeContextService(tasks, evidence);

    @Test
    void returnsOnlyEvidenceFromTheTasksSourceEvent() {
        LearningTask task = new LearningTask();
        task.setId(7L);
        task.setEventId(12L);
        EvidenceItem item = new EvidenceItem();
        item.setId(21L);
        when(tasks.findById(7L)).thenReturn(Optional.of(task));
        when(evidence.findByEventId(12L)).thenReturn(Collections.singletonList(item));

        assertEquals(Collections.singletonList(item), service.evidenceForTask(7L));
        verify(evidence).findByEventId(12L);
    }

    @Test
    void rejectsUnknownTasksAndReturnsEmptyForManualTasksWithoutAnEvent() {
        when(tasks.findById(404L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.evidenceForTask(404L));

        LearningTask manual = new LearningTask();
        manual.setId(8L);
        when(tasks.findById(8L)).thenReturn(Optional.of(manual));
        assertEquals(Collections.emptyList(), service.evidenceForTask(8L));
    }
}
