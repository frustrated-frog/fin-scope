package com.finscope.service.radar;

import com.finscope.dao.radar.RadarRefreshRunRepository;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.service.cache.ViewRevisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RadarHotspotRefreshService {
    private static final Logger log = LoggerFactory.getLogger(RadarHotspotRefreshService.class);
    private final RadarHotspotProductionPipeline pipeline;
    private final RadarRefreshRunRepository runs;
    private final Executor executor;
    private final ViewRevisionService viewRevisions;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public RadarHotspotRefreshService(RadarHotspotProductionPipeline pipeline,
                                      RadarRefreshRunRepository runs,
                                      ViewRevisionService viewRevisions,
                                      @org.springframework.beans.factory.annotation.Qualifier("radarRefreshExecutor") Executor executor) {
        this(pipeline, runs, viewRevisions, executor, Clock.systemDefaultZone());
    }

    RadarHotspotRefreshService(RadarHotspotProductionPipeline pipeline, RadarRefreshRunRepository runs,
                               Executor executor, Clock clock) {
        this(pipeline, runs, null, executor, clock);
    }

    RadarHotspotRefreshService(RadarHotspotProductionPipeline pipeline, RadarRefreshRunRepository runs,
                               ViewRevisionService viewRevisions, Executor executor, Clock clock) {
        this.pipeline = pipeline; this.runs = runs; this.viewRevisions = viewRevisions; this.executor = executor; this.clock = clock;
    }

    public boolean requestRefresh() { return request("MANUAL"); }

    public boolean requestScheduledRefresh() { return request("SCHEDULED"); }

    public boolean isRunning() { return running.get(); }

    public Optional<RadarRefreshRun> latestCompletedRun() { return runs.findLatestCompletedRun(); }

    /** 最近一次批次（任意状态），供页面区分正在生产、成功与失败。 */
    public Optional<RadarRefreshRun> latestRun() { return runs.findLatestRun(); }

    LocalDateTime now() { return LocalDateTime.now(clock); }

    private boolean request(String triggerType) {
        if (!running.compareAndSet(false, true)) return false;
        try {
            executor.execute(() -> {
                try {
                    RadarHotspotProductionPipeline.ProductionResult result = pipeline.run("ALL", triggerType, now());
                    if (result != null && viewRevisions != null) {
                        LocalDateTime completedAt = result.getRun() == null || result.getRun().getCompletedAt() == null
                                ? now() : result.getRun().getCompletedAt();
                        invalidatePageSnapshots(completedAt);
                    }
                }
                catch (RuntimeException error) {
                    log.error("雷达热点生产批次失败，trigger={}", triggerType, error);
                    // persistEvents 是独立事务；后续步骤失败时数据库仍可能已有本轮已提交事件。
                    invalidatePageSnapshots(now());
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

    private void invalidatePageSnapshots(LocalDateTime completedAt) {
        if (viewRevisions == null) return;
        viewRevisions.invalidate("radar", completedAt);
        viewRevisions.invalidate("dashboard", completedAt);
    }
}
