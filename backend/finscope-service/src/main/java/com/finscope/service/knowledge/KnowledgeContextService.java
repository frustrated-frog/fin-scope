package com.finscope.service.knowledge;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.LearningTask;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import com.finscope.common.exception.BizErrorCode;

/**
 * Resolves bounded read context for a knowledge command.
 *
 * <p>Clients identify the learning task, never an arbitrary event. This keeps
 * evidence selection inside the same trust boundary used by completion.</p>
 */
@Service
public class KnowledgeContextService {
    private final LearningTaskRepository tasks;
    private final EvidenceItemRepository evidence;

    public KnowledgeContextService(LearningTaskRepository tasks,
                                   EvidenceItemRepository evidence) {
        this.tasks = tasks;
        this.evidence = evidence;
    }

    public List<EvidenceItem> evidenceForTask(long taskId) {
        LearningTask task = tasks.findById(taskId)
                .orElseThrow(() -> new BusinessException(BizErrorCode.LEARNING_TASK_NOT_FOUND));
        if (task.getEventId() == null) {
            return Collections.emptyList();
        }
        return evidence.findByEventId(task.getEventId());
    }
}
