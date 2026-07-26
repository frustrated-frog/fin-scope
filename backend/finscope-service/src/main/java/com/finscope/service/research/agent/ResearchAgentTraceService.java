package com.finscope.service.research.agent;

import com.finscope.dao.research.agent.ResearchAgentRepository;
import com.finscope.domain.research.agent.ResearchAgentTraceView;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ResearchAgentTraceService {
    private final ResearchAgentRepository repository;
    private final ResearchTrajectoryEvaluator evaluator;

    public ResearchAgentTraceService(ResearchAgentRepository repository,
                                     ResearchTrajectoryEvaluator evaluator) {
        this.repository = repository;
        this.evaluator = evaluator;
    }

    public Optional<ResearchAgentTraceView> findTrace(Long runId) {
        if (!repository.findState(runId).isPresent()) {
            return Optional.empty();
        }
        ResearchAgentTraceView trace = repository.findTrace(runId);
        trace.setTrajectoryMetrics(evaluator.evaluate(trace));
        return Optional.of(trace);
    }
}
