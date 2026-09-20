package com.finscope.service.investmentobservation;

import com.finscope.domain.investmentobservation.ReactionDiscoveryStatus;
import com.finscope.domain.investmentobservation.ReactionRefreshResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class ReactionRefreshScheduler {
    @Resource
    private ReactionRefreshService refreshService;
    @Resource
    private ReactionDiscoveryService discovery;
    private volatile ReactionDiscoveryStatus lastStatus =
            new ReactionDiscoveryStatus();
    @Resource(name = "investmentReactionExecutor")
    private Executor executor;
    private final AtomicBoolean pending = new AtomicBoolean();

    @Scheduled(fixedDelay = 300000, initialDelay = 5000)
    public void refreshAfterClose() {
        if (!pending.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::refreshBatch);
        } catch (RuntimeException ex) {
            pending.set(false);
            log.warn("reaction refresh dispatch rejected; next schedule will retry", ex);
        }
    }

    public ReactionDiscoveryStatus status() {
        var result = new ReactionDiscoveryStatus();
        var snapshot = lastStatus;
        result.setRunning(pending.get());
        result.setLastCompletedAt(snapshot.getLastCompletedAt());
        result.setCaptured(snapshot.getCaptured());
        result.setResolved(snapshot.getResolved());
        result.setMessage(snapshot.getMessage());
        return result;
    }

    private void refreshBatch() {
        try {
            try {
                lastStatus = discovery.discover();
            } catch (RuntimeException ex) {
                var failed = new ReactionDiscoveryStatus();
                failed.setLastCompletedAt(lastStatus.getLastCompletedAt());
                failed.setMessage("自动发现暂不可用，系统将在下一轮重试；现有行情样本仍继续更新");
                lastStatus = failed;
                log.warn("reaction discovery failed", ex);
            }
            ReactionRefreshResult result = refreshService.refreshPending();
            if (result.getRefreshed() > 0 || result.getFailed() > 0) {
                log.info("reaction refresh completed refreshed={} failed={}", result.getRefreshed(), result.getFailed());
            }
        } catch (Exception ex) {
            log.error("reaction scheduled refresh failed; next run will retry", ex);
        } finally {
            pending.set(false);
        }
    }
}
