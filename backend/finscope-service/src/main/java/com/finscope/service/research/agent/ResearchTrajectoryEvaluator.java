package com.finscope.service.research.agent;

import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchAgentTraceView;
import com.finscope.domain.research.agent.ResearchAgentTrajectoryMetrics;
import com.finscope.domain.research.agent.ResearchToolObservation;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ResearchTrajectoryEvaluator {
    public ResearchAgentTrajectoryMetrics evaluate(ResearchAgentTraceView trace) {
        ResearchAgentTrajectoryMetrics result = new ResearchAgentTrajectoryMetrics();
        List<ResearchAgentDecision> decisions = trace == null
                ? java.util.Collections.<ResearchAgentDecision>emptyList() : trace.getDecisions();
        List<ResearchToolObservation> observations = trace == null
                ? java.util.Collections.<ResearchToolObservation>emptyList() : trace.getObservations();
        result.setDecisionCount(decisions.size());
        result.setObservationCount(observations.size());
        if (decisions.isEmpty()) {
            return result;
        }

        int valid = 0;
        int fallback = 0;
        int toolCalls = 0;
        int duplicates = 0;
        int replans = 0;
        int successfulReplans = 0;
        int finishes = 0;
        boolean firstFinishAccepted = false;
        Set<String> fingerprints = new HashSet<String>();
        Map<Long, Integer> iterationByDecision = new HashMap<Long, Integer>();
        int maxIteration = 0;
        for (ResearchAgentDecision decision : decisions) {
            maxIteration = Math.max(maxIteration, decision.getIteration());
            if (decision.getId() != null) iterationByDecision.put(decision.getId(), decision.getIteration());
        }
        for (ResearchAgentDecision decision : decisions) {
            if (decision.getDecisionType() != null && decision.getStatus() != null) valid++;
            if ("DETERMINISTIC".equals(decision.getDecisionMode())) fallback++;
            if ("TOOL_CALL".equals(decision.getDecisionType())) {
                toolCalls++;
                if (decision.getActionFingerprint() != null && !fingerprints.add(decision.getActionFingerprint())) {
                    duplicates++;
                }
            }
            if ("PLAN_PATCH".equals(decision.getDecisionType())) {
                replans++;
                if (hasSuccessfulOutcomeAfter(decision.getIteration(), decisions, observations, iterationByDecision)) {
                    successfulReplans++;
                }
            }
            if ("FINISH".equals(decision.getDecisionType())) {
                if (finishes == 0) firstFinishAccepted = "COMPLETED".equals(decision.getStatus());
                finishes++;
            }
        }
        int followed = 0;
        int noProgress = 0;
        for (ResearchToolObservation observation : observations) {
            Integer iteration = iterationByDecision.get(observation.getDecisionId());
            if (iteration != null && maxIteration > iteration) followed++;
            if ("NO_PROGRESS".equals(observation.getStatus())) noProgress++;
        }
        result.setDecisionValidityRate(rate(valid, decisions.size()));
        result.setObservationFollowupRate(observations.isEmpty() ? 1D : rate(followed, observations.size()));
        result.setDuplicateActionRate(rate(duplicates, toolCalls));
        result.setNoProgressRate(rate(noProgress, observations.size()));
        result.setReplanSuccessRate(replans == 0 ? 1D : rate(successfulReplans, replans));
        result.setFinishFirstPassRate(finishes == 0 ? 0D : firstFinishAccepted ? 1D : 0D);
        result.setFallbackRate(rate(fallback, decisions.size()));
        result.setQualityScore(quality(result));
        return result;
    }

    private boolean hasSuccessfulOutcomeAfter(int iteration,
                                              List<ResearchAgentDecision> decisions,
                                              List<ResearchToolObservation> observations,
                                              Map<Long, Integer> iterationByDecision) {
        for (ResearchToolObservation observation : observations) {
            Integer observedIteration = iterationByDecision.get(observation.getDecisionId());
            if (observedIteration != null && observedIteration > iteration
                    && "SUCCESS".equals(observation.getStatus())) return true;
        }
        for (ResearchAgentDecision decision : decisions) {
            if (decision.getIteration() > iteration && "FINISH".equals(decision.getDecisionType())
                    && "COMPLETED".equals(decision.getStatus())) return true;
        }
        return false;
    }

    private int quality(ResearchAgentTrajectoryMetrics value) {
        double score = 100D
                - (1D - value.getDecisionValidityRate()) * 20D
                - (1D - value.getObservationFollowupRate()) * 10D
                - value.getDuplicateActionRate() * 20D
                - value.getNoProgressRate() * 20D
                - (1D - value.getReplanSuccessRate()) * 5D
                - (1D - value.getFinishFirstPassRate()) * 5D
                - value.getFallbackRate() * 20D;
        return (int) Math.round(Math.max(0D, Math.min(100D, score)));
    }

    private double rate(int numerator, int denominator) {
        return denominator <= 0 ? 0D : (double) numerator / (double) denominator;
    }
}
