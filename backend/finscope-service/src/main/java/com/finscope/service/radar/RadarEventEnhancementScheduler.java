package com.finscope.service.radar;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class RadarEventEnhancementScheduler {
    private final RadarCanonicalTitleAgent radarCanonicalTitleAgent;
    private final RadarEvidenceOrchestrator evidence;
    private final RadarRepository repository;
    private final RadarSnapshotProjectionService snapshots;
    private final Executor executor;

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    @Autowired
    public RadarEventEnhancementScheduler(RadarCanonicalTitleAgent radarCanonicalTitleAgent,
                                          RadarEvidenceOrchestrator evidence,
                                          RadarRepository repository,
                                          RadarSnapshotProjectionService snapshots,
                                          @Qualifier("radarAgentExecutor") Executor executor) {
        this.radarCanonicalTitleAgent = radarCanonicalTitleAgent;
        this.evidence = evidence;
        this.repository = repository;
        this.snapshots = snapshots;
        this.executor = executor;
    }

    RadarEventEnhancementScheduler(RadarCanonicalTitleAgent radarCanonicalTitleAgent,
                                   RadarEvidenceOrchestrator evidence,
                                   RadarRepository repository,
                                   Executor executor) {
        this(radarCanonicalTitleAgent, evidence, repository, null, executor);
    }

    public void schedule(RadarEvent event, List<RadarSignal> signals, LocalDateTime now, boolean includeEvidence) {
        if (event == null || event.getId() == null) {
            return;
        }
        String key = event.getEventKey() == null ? String.valueOf(event.getId()) : event.getEventKey();
        if (!inFlight.add(key)) {
            return;
        }
        List<RadarSignal> snapshot = signals == null ? new ArrayList<RadarSignal>() : new ArrayList<RadarSignal>(signals);
        try {
            executor.execute(() -> enhance(event, snapshot, now, includeEvidence, key));
        } catch (RuntimeException ignored) {
            inFlight.remove(key);
        }
    }

    private void enhance(RadarEvent event, List<RadarSignal> signals, LocalDateTime now,
                         boolean includeEvidence, String key) {
        try {
            if (signals.size() > 1) {
                RadarCanonicalTitleAgent.Result title = radarCanonicalTitleAgent.generate(signals, event.getCanonicalTitle());
                if (title.isGenerated() && title.getTitle() != null && !title.getTitle().trim().isEmpty()) {
                    event.setCanonicalTitle(title.getTitle());
                }
            }
            if (includeEvidence) {
                RadarEvidenceOrchestrator.Outcome outcome = evidence.enrich(event, signals);
                if (!"CACHED".equals(outcome.getStatus()) && !"SKIPPED".equals(outcome.getStatus())) {
                    event.setEvidenceStatus(outcome.getStatus());
                    event.setEvidenceSummary(outcome.getSummary());
                    event.setEvidenceWarning(outcome.getWarning());
                    event.setEvidenceCount(outcome.getEvidenceCount());
                    event.setEvidenceSourceCount(outcome.getSourceCount());
                    if (outcome.getNextObservation() != null && !outcome.getNextObservation().trim().isEmpty()) {
                        event.setNextObservation(outcome.getNextObservation());
                    }
                    if (!"DEGRADED".equals(outcome.getStatus())) event.setEvidenceFingerprint(outcome.getFingerprint());
                    event.setEvidenceUpdatedAt(now);
                }
            }
            repository.updateEvidenceEnhancement(event);
            if (snapshots != null) {
                snapshots.republish();
            }
        } catch (RuntimeException ignored) {
            // 后台增强失败时保留规则结果，下一次刷新仍可重试。
        } finally {
            inFlight.remove(key);
        }
    }
}
