package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarRefreshStep;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class RadarRefreshRunRepository {
    @Resource
    private RedisRadarCacheStore store;

    public RadarRefreshRun startRun(String runKey, String triggerType, LocalDateTime startedAt) {
        return store.update(state -> {
            RadarRefreshRun value = new RadarRefreshRun();
            value.setId(store.stableId("run", runKey));
            value.setRunKey(runKey);
            value.setTriggerType(triggerType);
            value.setStatus("RUNNING");
            value.setStartedAt(startedAt);
            state.getRuns().put(value.getId(), value);
            return value;
        });
    }

    public RadarRefreshStep startStep(Long runId, String stepCode, LocalDateTime startedAt) {
        return store.update(state -> {
            RadarRefreshStep value = new RadarRefreshStep();
            value.setId(store.stableId("run-step", runId + ":" + stepCode));
            value.setRunId(runId);
            value.setStepCode(stepCode);
            value.setStatus("RUNNING");
            value.setStartedAt(startedAt);
            List<RadarRefreshStep> values = state.getRunSteps().computeIfAbsent(runId,
                    ignored -> new ArrayList<RadarRefreshStep>());
            values.removeIf(existing -> stepCode.equals(existing.getStepCode()));
            values.add(value);
            return value;
        });
    }

    public RadarRefreshStep completeStep(Long runId, String stepCode, String status,
                                         int inputCount, int outputCount, String details, LocalDateTime completedAt) {
        return store.update(state -> {
            RadarRefreshStep value = findStep(state, runId, stepCode).orElseThrow(IllegalStateException::new);
            value.setStatus(status);
            value.setCompletedAt(completedAt);
            value.setInputCount(inputCount);
            value.setOutputCount(outputCount);
            value.setDetails(details);
            return value;
        });
    }

    public RadarRefreshRun completeRun(Long id, int sourceCount, int signalCount, int eventCount,
                                       String warning, LocalDateTime completedAt) {
        return store.update(state -> {
            RadarRefreshRun value = requireRun(state, id);
            value.setStatus("SUCCESS");
            value.setCompletedAt(completedAt);
            value.setSourceCount(sourceCount);
            value.setSignalCount(signalCount);
            value.setEventCount(eventCount);
            value.setWarning(emptyToNull(warning));
            value.setError(null);
            return value;
        });
    }

    public RadarRefreshRun failRun(Long id, String error, LocalDateTime completedAt) {
        return store.update(state -> {
            RadarRefreshRun value = requireRun(state, id);
            value.setStatus("FAILED");
            value.setCompletedAt(completedAt);
            value.setError(error);
            return value;
        });
    }

    public Optional<RadarRefreshRun> findLatestCompletedRun() {
        return store.read().getRuns().values().stream().filter(value -> "SUCCESS".equals(value.getStatus()))
                .max(Comparator.comparing(RadarRefreshRun::getCompletedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(RadarRefreshRun::getId));
    }

    public Optional<RadarRefreshRun> findLatestRun() {
        return store.read().getRuns().values().stream()
                .max(Comparator.comparing(RadarRefreshRun::getStartedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(RadarRefreshRun::getId));
    }

    public List<RadarRefreshStep> findSteps(Long runId) {
        List<RadarRefreshStep> values = new ArrayList<RadarRefreshStep>(store.read().getRunSteps()
                .getOrDefault(runId, java.util.Collections.emptyList()));
        values.sort(Comparator.comparing(RadarRefreshStep::getId));
        return values;
    }

    private RadarRefreshRun requireRun(RadarCacheState state, Long id) {
        RadarRefreshRun value = state.getRuns().get(id);
        if (value == null) {
            throw new IllegalStateException("雷达生产批次不存在: " + id);
        }
        return value;
    }

    private Optional<RadarRefreshStep> findStep(RadarCacheState state, Long runId, String stepCode) {
        return state.getRunSteps().getOrDefault(runId, java.util.Collections.emptyList()).stream()
                .filter(value -> stepCode.equals(value.getStepCode())).findFirst();
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
