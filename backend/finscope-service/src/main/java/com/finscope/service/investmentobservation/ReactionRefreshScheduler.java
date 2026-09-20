package com.finscope.service.investmentobservation;

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
    @Resource(name = "investmentReactionExecutor")
    private Executor executor;
    private final AtomicBoolean pending = new AtomicBoolean();

    @Scheduled(cron = "${finscope.investment-reaction.refresh-cron:0 */20 16-23 * * MON-FRI}", zone = "Asia/Shanghai")
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

    private void refreshBatch() {
        try {
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
