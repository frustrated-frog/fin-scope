package com.finscope.service.radar;

import com.finscope.dao.radar.RadarPairDecisionRepository;
import com.finscope.domain.radar.RadarPairDecision;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class RadarPairDecisionScheduler {
    private final RadarEventMatchAgent agent;
    private final RadarPairDecisionRepository repository;
    private final Executor executor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public RadarPairDecisionScheduler(RadarEventMatchAgent agent,
                                      RadarPairDecisionRepository repository,
                                      @Qualifier("radarAgentExecutor") Executor executor) {
        this.agent = agent;
        this.repository = repository;
        this.executor = executor;
    }

    public void schedule(RadarSignal left, RadarSignal right,
                         String leftFingerprint, String rightFingerprint) {
        String pairKey = RadarPairDecision.pairKey(leftFingerprint, rightFingerprint);
        if (!inFlight.add(pairKey)) return;
        try {
            executor.execute(() -> decideAndStore(left, right, leftFingerprint, rightFingerprint, pairKey));
        } catch (RuntimeException ignored) {
            inFlight.remove(pairKey);
        }
    }

    private void decideAndStore(RadarSignal left, RadarSignal right,
                                String leftFingerprint, String rightFingerprint, String pairKey) {
        try {
            RadarEventMatchAgent.Decision decision = agent.decide(left, right);
            if (!"AGENT".equals(decision.getSource())) return;
            RadarPairDecision stored = new RadarPairDecision();
            stored.setPairKey(pairKey);
            if (leftFingerprint.compareTo(rightFingerprint) <= 0) {
                stored.setLeftFingerprint(leftFingerprint);
                stored.setRightFingerprint(rightFingerprint);
            } else {
                stored.setLeftFingerprint(rightFingerprint);
                stored.setRightFingerprint(leftFingerprint);
            }
            stored.setSameEvent(decision.isSameEvent());
            stored.setConfidence(decision.getConfidence());
            stored.setReason(decision.getReason());
            stored.setDecisionSource(decision.getSource());
            repository.save(stored);
        } catch (RuntimeException ignored) {
            // Agent 或缓存失败只影响后续聚合质量，不能影响实时雷达。
        } finally {
            inFlight.remove(pairKey);
        }
    }
}
