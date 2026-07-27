package com.finscope.service.research;

import com.finscope.dao.fetch.FetchRunRepository;
import com.finscope.dao.research.ResearchRunPlanRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.research.agent.ResearchAgentRepository;
import com.finscope.dao.research.mission.ResearchMissionRepository;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ResearchStartupRecoveryService {
    private final ResearchRunRepository researchRunRepository;
    private final ResearchRunPlanRepository researchRunPlanRepository;
    private final FetchRunRepository fetchRunRepository;
    private final ResearchRuntimeRepository researchRuntimeRepository;
    private final ResearchMissionRepository researchMissionRepository;
    private final ResearchAgentRepository researchAgentRepository;
    private boolean recovered;

    public ResearchStartupRecoveryService(ResearchRunRepository researchRunRepository,
                                          ResearchRunPlanRepository researchRunPlanRepository,
                                          FetchRunRepository fetchRunRepository,
                                          ResearchRuntimeRepository researchRuntimeRepository,
                                          ResearchMissionRepository researchMissionRepository,
                                          ResearchAgentRepository researchAgentRepository) {
        this.researchRunRepository = researchRunRepository;
        this.researchRunPlanRepository = researchRunPlanRepository;
        this.fetchRunRepository = fetchRunRepository;
        this.researchRuntimeRepository = researchRuntimeRepository;
        this.researchMissionRepository = researchMissionRepository;
        this.researchAgentRepository = researchAgentRepository;
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
        researchRuntimeRepository.interruptRunning(message);
        researchMissionRepository.interruptRunning(message);
        researchAgentRepository.interruptRunning(message);
        researchRunRepository.failRunningRuns(message);
        fetchRunRepository.failRunningRuns(message);
    }
}
