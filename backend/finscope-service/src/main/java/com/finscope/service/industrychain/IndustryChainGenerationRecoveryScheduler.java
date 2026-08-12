package com.finscope.service.industrychain;

import com.finscope.dao.industrychain.IndustryChainRepository;
import com.finscope.domain.industrychain.IndustryChainRevision;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 恢复 Kafka 未投递、消费者崩溃或租约中断的产业图谱任务。 */
@Service
public class IndustryChainGenerationRecoveryScheduler {
    private final IndustryChainRepository repository;
    private final IndustryChainGenerationExecutor executor;

    public IndustryChainGenerationRecoveryScheduler(IndustryChainRepository repository,
                                                     IndustryChainGenerationExecutor executor) {
        this.repository = repository;
        this.executor = executor;
    }

    @Scheduled(initialDelayString = "${finscope.industry-chain.recovery-initial-delay-ms:60000}",
            fixedDelayString = "${finscope.industry-chain.recovery-interval-ms:60000}")
    public void recover() {
        for (IndustryChainRevision revision : repository.findRecoverableGenerations()) {
            executor.schedule(revision.getChainId(), revision.getId());
        }
    }
}
