package com.finscope.service.research;

import com.finscope.dao.fetch.FetchRunRepository;
import com.finscope.dao.research.ResearchRunPlanRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ResearchStartupRecoveryServiceTest {
    @Test
    void marksInterruptedRunsAndFetchesAsFailedOnStartup() {
        ResearchRunRepository researchRunRepository = mock(ResearchRunRepository.class);
        ResearchRunPlanRepository researchRunPlanRepository = mock(ResearchRunPlanRepository.class);
        FetchRunRepository fetchRunRepository = mock(FetchRunRepository.class);
        ResearchRuntimeRepository runtimeRepository = mock(ResearchRuntimeRepository.class);
        ResearchStartupRecoveryService service = new ResearchStartupRecoveryService(
                researchRunRepository, researchRunPlanRepository, fetchRunRepository, runtimeRepository);

        service.recoverInterruptedRuns();

        verify(researchRunPlanRepository).recoverOpenStepsForInterruptedRuns(contains("interrupted"));
        verify(researchRunRepository).failRunningRuns(contains("interrupted"));
        verify(fetchRunRepository).failRunningRuns(contains("interrupted"));
        verify(runtimeRepository).interruptRunning(contains("interrupted"));
    }
}
