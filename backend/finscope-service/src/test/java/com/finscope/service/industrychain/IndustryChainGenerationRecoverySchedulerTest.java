package com.finscope.service.industrychain;

import com.finscope.dao.industrychain.IndustryChainRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class IndustryChainGenerationRecoverySchedulerTest {
    @Test
    void disabledRecoveryDoesNotScanOrSubmitGenerationJobs() {
        IndustryChainRepository repository = mock(IndustryChainRepository.class);
        IndustryChainGenerationExecutor executor = mock(IndustryChainGenerationExecutor.class);
        IndustryChainGenerationRecoveryScheduler scheduler =
                new IndustryChainGenerationRecoveryScheduler(repository, executor);
        ReflectionTestUtils.setField(scheduler, "recoveryModelEnabled", false);

        scheduler.recover();

        verifyNoInteractions(repository, executor);
    }
}
