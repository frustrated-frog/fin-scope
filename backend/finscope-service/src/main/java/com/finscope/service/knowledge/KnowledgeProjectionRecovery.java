package com.finscope.service.knowledge;

import com.finscope.dao.knowledge.KnowledgeProjectionJobRepository;
import com.finscope.domain.knowledge.KnowledgeProjectionJob;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeProjectionRecovery {
    static final int BATCH_SIZE = 50;

    private final KnowledgeProjectionJobRepository jobs;
    private final KnowledgeVaultProjector projector;

    public KnowledgeProjectionRecovery(KnowledgeProjectionJobRepository jobs,
                                       KnowledgeVaultProjector projector) {
        this.jobs = jobs;
        this.projector = projector;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        recover();
    }

    public void recover() {
        for (KnowledgeProjectionJob job : jobs.findRecoverable(BATCH_SIZE)) {
            projector.project(job.getId());
        }
    }
}
