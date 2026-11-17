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
            closeRunningSteps(state, id, error, completedAt);
            return value;
        });
    }

    /** 由单实例生产入口在开始下一轮之前回收中断的任务；有效任务不受影响。 */
    public int failAbandonedRuns(LocalDateTime before, LocalDateTime now) {
        List<Long> ids = store.read().getRuns().values().stream()
                .filter(value -> "RUNNING".equals(value.getStatus()) && value.getStartedAt() != null
                        && value.getStartedAt().isBefore(before))
                .map(RadarRefreshRun::getId).toList();
        int recovered = 0;
        for (int start = 0; start < ids.size(); start += RadarCacheLayout.BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + RadarCacheLayout.BATCH_SIZE, ids.size()));
            recovered += store.update(state -> {
                RedisRadarCacheStore.prefetch(state.getRuns(), batch);
                RedisRadarCacheStore.prefetch(state.getRunSteps(), batch);
                int changed = 0;
                for (Long id : batch) {
                    RadarRefreshRun run = state.getRuns().get(id);
                    if (run != null && "RUNNING".equals(run.getStatus()) && run.getStartedAt().isBefore(before)) {
                        String reason = "生产任务中断或超过30分钟执行期限，已结束遗留状态";
                        run.setStatus("FAILED");
                        run.setCompletedAt(now);
                        run.setError(reason);
                        closeRunningSteps(state, id, reason, now);
                        changed++;
                    }
                }
                return changed;
            });
        }
        return recovered;
    }

    private void closeRunningSteps(RadarCacheState state, Long id, String error, LocalDateTime now) {
        for (RadarRefreshStep step : state.getRunSteps().getOrDefault(id, java.util.Collections.emptyList())) {
            if ("RUNNING".equals(step.getStatus())) {
                step.setStatus("FAILED");
                step.setCompletedAt(now);
                step.setError(error);
            }
        }
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
