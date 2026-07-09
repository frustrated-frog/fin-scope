package com.finscope.service.research;

import com.finscope.dao.fetch.FetchRunRepository;
import com.finscope.dao.research.ResearchRunPlanRepository;
import com.finscope.dao.research.ResearchRunRepository;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ResearchStartupRecoveryService {
    private final ResearchRunRepository researchRunRepository;
    private final ResearchRunPlanRepository researchRunPlanRepository;
    private final FetchRunRepository fetchRunRepository;
    private boolean recovered;

    public ResearchStartupRecoveryService(ResearchRunRepository researchRunRepository,
                                          ResearchRunPlanRepository researchRunPlanRepository,
                                          FetchRunRepository fetchRunRepository) {
        this.researchRunRepository = researchRunRepository;
        this.researchRunPlanRepository = researchRunPlanRepository;
        this.fetchRunRepository = fetchRunRepository;
    }

    @EventListener(ContextRefreshedEvent.class)
    public synchronized void onContextRefreshed() {
        if (recovered) {
            return;
        }
        recovered = true;
        recoverInterruptedRuns();
    }

    public void recoverInterruptedRuns() {
        String message = "Run was interrupted by process shutdown before completion.";
        researchRunPlanRepository.recoverOpenStepsForInterruptedRuns(message);
        researchRunRepository.failRunningRuns(message);
        fetchRunRepository.failRunningRuns(message);
    }
}
