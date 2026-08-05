package com.finscope.service.radar;

import com.finscope.dao.radar.RadarRefreshRunRepository;
import com.finscope.domain.radar.RadarRefreshRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RadarHotspotRefreshService {
    private final RadarHotspotProductionPipeline pipeline;
    private final RadarRefreshRunRepository runs;
    private final Executor executor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public RadarHotspotRefreshService(RadarHotspotProductionPipeline pipeline,
                                      RadarRefreshRunRepository runs,
                                      @org.springframework.beans.factory.annotation.Qualifier("radarRefreshExecutor") Executor executor) {
        this(pipeline, runs, executor, Clock.systemDefaultZone());
    }

    RadarHotspotRefreshService(RadarHotspotProductionPipeline pipeline, RadarRefreshRunRepository runs,
                               Executor executor, Clock clock) {
        this.pipeline = pipeline; this.runs = runs; this.executor = executor; this.clock = clock;
    }

    public boolean requestRefresh() { return request("MANUAL"); }

    boolean requestScheduledRefresh() { return request("SCHEDULED"); }

    public boolean isRunning() { return running.get(); }

    public Optional<RadarRefreshRun> latestCompletedRun() { return runs.findLatestCompletedRun(); }

    LocalDateTime now() { return LocalDateTime.now(clock); }

    private boolean request(String triggerType) {
        if (!running.compareAndSet(false, true)) return false;
        try {
            executor.execute(() -> {
                try { pipeline.run("ALL", triggerType, now()); }
                catch (RuntimeException ignored) { }
                finally { running.set(false); }
            });
            return true;
        } catch (RuntimeException error) {
            running.set(false);
            return false;
        }
    }
}
