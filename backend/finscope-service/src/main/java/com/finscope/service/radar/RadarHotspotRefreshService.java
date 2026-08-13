package com.finscope.service.radar;

import com.finscope.dao.radar.RadarRefreshRunRepository;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarInterpretationBatchMessage;
import com.finscope.domain.radar.RadarInterpretationBatchPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RadarHotspotRefreshService {
    private static final Logger log = LoggerFactory.getLogger(RadarHotspotRefreshService.class);
    private final RadarHotspotProductionPipeline pipeline;
    private final RadarRefreshRunRepository runs;
    private final Executor executor;
    private final RadarSnapshotProjectionService snapshots;
    private final RadarInterpretationBatchPublisher interpretationPublisher;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public RadarHotspotRefreshService(RadarHotspotProductionPipeline pipeline,
                                      RadarRefreshRunRepository runs,
                                      RadarSnapshotProjectionService snapshots,
                                      RadarInterpretationBatchPublisher interpretationPublisher,
                                      @org.springframework.beans.factory.annotation.Qualifier("radarRefreshExecutor") Executor executor) {
        this(pipeline, runs, snapshots, interpretationPublisher, executor, Clock.systemDefaultZone());
    }

    RadarHotspotRefreshService(RadarHotspotProductionPipeline pipeline, RadarRefreshRunRepository runs,
                               Executor executor, Clock clock) {
        this(pipeline, runs, null, message -> { }, executor, clock);
    }

    RadarHotspotRefreshService(RadarHotspotProductionPipeline pipeline, RadarRefreshRunRepository runs,
                               RadarSnapshotProjectionService snapshots, Executor executor, Clock clock) {
        this(pipeline, runs, snapshots, message -> { }, executor, clock);
    }

    RadarHotspotRefreshService(RadarHotspotProductionPipeline pipeline, RadarRefreshRunRepository runs,
                               RadarSnapshotProjectionService snapshots,
                               RadarInterpretationBatchPublisher interpretationPublisher,
                               Executor executor, Clock clock) {
        this.pipeline = pipeline; this.runs = runs; this.snapshots = snapshots;
        this.interpretationPublisher = interpretationPublisher;
        this.executor = executor; this.clock = clock;
    }

    public boolean requestRefresh() {
        return request("MANUAL");
    }

    public boolean requestScheduledRefresh() {
        return request("SCHEDULED");
    }

    public boolean isRunning() {
        return running.get();
    }

    public Optional<RadarRefreshRun> latestCompletedRun() {
        return runs.findLatestCompletedRun();
    }

    /** 最近一次批次（任意状态），供页面区分正在生产、成功与失败。 */
    public Optional<RadarRefreshRun> latestRun() { return runs.findLatestRun(); }

    LocalDateTime now() { return LocalDateTime.now(clock); }

    private boolean request(String triggerType) {
        if (!running.compareAndSet(false, true)) return false;
        try {
            executor.execute(() -> {
                try {
                    RadarHotspotProductionPipeline.ProductionResult result = pipeline.run("ALL", triggerType, now());
                    if (result != null && snapshots != null && snapshots.prewarm(result.getEvents(), result.getRun())) {
                        publishInterpretations(result);
                    }
                }
                catch (RuntimeException error) {
                    log.error("雷达热点生产批次失败，trigger={}", triggerType, error);
                } finally {
                    running.set(false);
                }
            });
            return true;
        } catch (RuntimeException error) {
            log.error("雷达热点生产任务提交失败，trigger={}", triggerType, error);
            running.set(false);
            return false;
        }
    }

    private void publishInterpretations(RadarHotspotProductionPipeline.ProductionResult result) {
        List<Long> eventIds = new ArrayList<>();
        for (RadarEvent event : result.getEvents()) {
            if (event != null && event.getId() != null) {
                eventIds.add(event.getId());
            }
            if (eventIds.size() >= RadarInterpretationBatchMessage.MAX_EVENT_COUNT) {
                break;
            }
        }
        if (eventIds.isEmpty()) {
            return;
        }
        try {
            RadarRefreshRun run = result.getRun();
            interpretationPublisher.publish(new RadarInterpretationBatchMessage(
                    run == null ? null : run.getRunKey(), run == null ? null : run.getCompletedAt(), eventIds));
        } catch (RuntimeException error) {
            log.warn("雷达榜单已发布，但 Kafka 预解读消息发送失败，runKey={}", result.getRun() == null ? null : result.getRun().getRunKey(), error);
        }
    }

}
