package com.finscope.service.radar;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.radar.RadarEventInterpretationRepository;
import com.finscope.dao.radar.RadarEvidenceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.cache.ViewRevisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class RadarEventInterpretationService {
    private final RadarEventInterpretationRepository interpretations;
    private final RadarRepository radar;
    private final RadarEvidenceRepository evidence;
    private final RadarEventInterpretationAgent agent;
    private final Executor executor;
    private final ViewRevisionService viewRevisions;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    @Autowired
    public RadarEventInterpretationService(RadarEventInterpretationRepository interpretations,
                                           RadarRepository radar,
                                           RadarEvidenceRepository evidence,
                                           RadarEventInterpretationAgent agent,
                                           @Qualifier("radarInterpretationExecutor") Executor executor,
                                           ViewRevisionService viewRevisions) {
        this.interpretations = interpretations; this.radar = radar; this.evidence = evidence;
        this.agent = agent; this.executor = executor; this.viewRevisions = viewRevisions;
    }

    public RadarEventInterpretationService(RadarEventInterpretationRepository interpretations,
                                           RadarRepository radar,
                                           RadarEvidenceRepository evidence,
                                           RadarEventInterpretationAgent agent,
                                           Executor executor) {
        this(interpretations, radar, evidence, agent, executor, null);
    }

    public RadarEventInterpretation request(Long eventId) {
        RadarEvent event = findEvent(eventId);
        List<RadarSignal> signals = radar.findSignalsByEventId(eventId);
        List<RadarEvidence> evidenceItems = evidence.findByEventId(eventId);
        String fingerprint = fingerprint(event, signals, evidenceItems);
        Optional<RadarEventInterpretation> existing = interpretations.findByEventFingerprint(eventId, fingerprint);
        String key = eventId + ":" + fingerprint;
        if (existing.isPresent() && !retryable(existing.get())) return existing.get();
        if (existing.isPresent() && !inFlight.add(key)) return existing.get();
        RadarEventInterpretation queued;
        if (existing.isPresent()) {
            queued = existing.get();
            resetForRetry(queued);
            interpretations.update(queued);
        } else {
            queued = interpretations.saveQueued(eventId, fingerprint);
        }
        if (!existing.isPresent() && !inFlight.add(key)) return queued;
        try {
            executor.execute(() -> complete(queued, event, signals, evidenceItems, key));
        } catch (RuntimeException error) {
            inFlight.remove(key);
            fail(queued, "EXECUTOR_REJECTED", "解读任务繁忙，请稍后重试");
        }
        return queued;
    }

    private boolean retryable(RadarEventInterpretation value) {
        return "FAILED".equals(value.getStatus()) || "UNAVAILABLE".equals(value.getStatus());
    }

    private void resetForRetry(RadarEventInterpretation value) {
        value.setStatus("QUEUED"); value.setResult(null);
        value.setFailureCode(null); value.setFailureMessage(null); value.setDurationMs(null);
        value.setStartedAt(null); value.setCompletedAt(null);
    }

    public Optional<RadarEventInterpretation> current(RadarEvent event, List<RadarSignal> signals,
                                                       List<RadarEvidence> evidenceItems) {
        if (event == null || event.getId() == null) return Optional.empty();
        String fingerprint = fingerprint(event, signals, evidenceItems);
        Optional<RadarEventInterpretation> matching = interpretations.findByEventFingerprint(event.getId(), fingerprint);
        if (matching.isPresent()) return matching;
        Optional<RadarEventInterpretation> latest = interpretations.findLatestByEventId(event.getId());
        if (latest.isPresent()) latest.get().setStale(true);
        return latest;
    }

    public Map<Long, RadarEventInterpretation> latestByEventIds(List<Long> eventIds) {
        return interpretations.findLatestByEventIds(eventIds);
    }

    String fingerprint(RadarEvent event, List<RadarSignal> signals, List<RadarEvidence> evidenceItems) {
        List<String> parts = new ArrayList<String>();
        parts.add("title=" + text(event == null ? null : event.getCanonicalTitle()));
        parts.add("summary=" + text(event == null ? null : event.getSummary()));
        List<RadarSignal> sortedSignals = new ArrayList<RadarSignal>(safe(signals));
        Collections.sort(sortedSignals, Comparator.comparing(value -> value.getId() == null ? 0L : value.getId()));
        for (RadarSignal signal : sortedSignals) {
            parts.add("signal=" + signal.getId() + "|" + text(signal.getContentHash()) + "|"
                    + text(signal.getTitle()) + "|" + text(signal.getContent()));
        }
        List<RadarEvidence> sortedEvidence = new ArrayList<RadarEvidence>(safe(evidenceItems));
        Collections.sort(sortedEvidence, Comparator.comparing(value -> value.getId() == null ? 0L : value.getId()));
        for (RadarEvidence item : sortedEvidence) {
            parts.add("evidence=" + item.getId() + "|" + text(item.getToolCode()) + "|"
                    + text(item.getTitle()) + "|" + text(item.getSummary()) + "|" + text(item.getUrl()));
        }
        return sha256(String.join("\n", parts));
    }

    private void complete(RadarEventInterpretation queued, RadarEvent event, List<RadarSignal> signals,
                          List<RadarEvidence> evidenceItems, String key) {
        long started = System.nanoTime();
        try {
            queued.setStatus("RUNNING"); queued.setStartedAt(LocalDateTime.now()); interpretations.update(queued);
            queued.setResult(agent.interpret(event, signals, evidenceItems));
            queued.setStatus("SUCCESS"); queued.setFailureCode(null); queued.setFailureMessage(null);
        } catch (RadarEventInterpretationAgent.InterpretationException error) {
            queued.setStatus("MODEL_DISABLED".equals(error.getCode()) ? "UNAVAILABLE" : "FAILED");
            queued.setFailureCode(error.getCode()); queued.setFailureMessage(safeMessage(error));
        } catch (RuntimeException error) {
            queued.setStatus("FAILED"); queued.setFailureCode("UNEXPECTED_RUNTIME_ERROR");
            queued.setFailureMessage("事件解读执行异常，请稍后重试");
        } finally {
            queued.setDurationMs((System.nanoTime() - started) / 1_000_000L);
            queued.setCompletedAt(LocalDateTime.now());
            try {
                interpretations.update(queued);
            } finally { inFlight.remove(key); }
        }
    }

    private void fail(RadarEventInterpretation value, String code, String message) {
        value.setStatus("FAILED"); value.setFailureCode(code); value.setFailureMessage(message);
        value.setCompletedAt(LocalDateTime.now()); interpretations.update(value);
    }

    private RadarEvent findEvent(Long eventId) {
        return radar.findEvent(eventId).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "雷达事件不存在"));
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private String safeMessage(Throwable error) {
        String value = error == null || error.getMessage() == null ? "事件解读不可用" : error.getMessage();
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? Collections.<T>emptyList() : values;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
