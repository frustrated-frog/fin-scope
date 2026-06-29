package com.finscope.service.agent;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.agent.AgentRun;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentRunService {
    private final AgentRunRepository agentRunRepository;

    public AgentRunService(AgentRunRepository agentRunRepository) {
        this.agentRunRepository = agentRunRepository;
    }

    public List<AgentRun> latest(int limit) {
        return agentRunRepository.latest(limit);
    }

    public List<AgentRun> findByResearchRunId(Long researchRunId) {
        return agentRunRepository.findByResearchRunId(researchRunId);
    }
}
