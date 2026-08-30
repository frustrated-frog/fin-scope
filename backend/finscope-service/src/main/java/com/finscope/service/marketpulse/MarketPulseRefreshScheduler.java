package com.finscope.service.marketpulse;

import com.finscope.domain.marketpulse.MarketPulseRefreshResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/** 收盘后生成每日市场状态快照，并补偿应用停机期间错过的交易日。 */
@Service
@Slf4j
public class MarketPulseRefreshScheduler {
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private MarketPulseService service;
    private Clock clock = Clock.system(CHINA_ZONE);

    @Scheduled(cron = "${finscope.market-pulse.refresh-cron:0 30 15 * * MON-FRI}", zone = "Asia/Shanghai")
    public void refreshAfterClose() {
        LocalDate triggerDate = LocalDate.now(clock);
        long startedAt = System.currentTimeMillis();
        try {
            Optional<MarketPulseRefreshResult> result = service.refreshScheduled(triggerDate);
            logResult("AFTER_CLOSE", triggerDate, result, startedAt);
        } catch (RuntimeException error) {
            log.error("市场状态自动刷新失败 scene=AFTER_CLOSE triggerDate={} processed=0 failed=1 costMs={}",
                    triggerDate, System.currentTimeMillis() - startedAt, error);
        }
    }

    @Scheduled(initialDelayString = "${finscope.market-pulse.recovery-initial-delay-ms:30000}",
            fixedDelayString = "${finscope.market-pulse.recovery-interval-ms:3600000}")
    public void recoverMissedRefresh() {
        LocalDate triggerDate = LocalDate.now(clock);
        long startedAt = System.currentTimeMillis();
        try {
            Optional<MarketPulseRefreshResult> result = service.recoverMissing();
            logResult("RECOVERY", triggerDate, result, startedAt);
        } catch (RuntimeException error) {
            log.error("市场状态自动刷新失败 scene=RECOVERY triggerDate={} processed=0 failed=1 costMs={}",
                    triggerDate, System.currentTimeMillis() - startedAt, error);
        }
    }

    private void logResult(String scene, LocalDate triggerDate, Optional<MarketPulseRefreshResult> result,
                           long startedAt) {
        long costMs = System.currentTimeMillis() - startedAt;
        if (!result.isPresent()) {
            log.info("市场状态自动刷新跳过 scene={} triggerDate={} processed=0 failed=0 costMs={}",
                    scene, triggerDate, costMs);
            return;
        }
        log.info("市场状态自动刷新完成 scene={} triggerDate={} businessDate={} status={} "
                        + "processed=1 failed=0 costMs={}",
                scene, triggerDate, result.get().getBusinessDate(), result.get().getStatus(), costMs);
    }
}
