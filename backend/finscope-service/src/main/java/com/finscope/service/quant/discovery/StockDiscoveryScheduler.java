package com.finscope.service.quant.discovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@Slf4j
public class StockDiscoveryScheduler {
    @Resource
    private StockDiscoveryService service;
    @Resource
    private StockDiscoveryOutcomeService outcomeService;
    @Resource
    private StockDiscoveryCalendarService calendar;

    @Scheduled(cron = "${finscope.stock-discovery.cron:0 30 15 * * MON-FRI}", zone = "Asia/Shanghai")
    public void scheduleAfterClose() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        try {
            if (calendar.isSession(date)) {
                service.schedule(date, "SCHEDULED");
            }
        } catch (RuntimeException error) {
            log.warn("股票发现交易日历暂不可用，等待恢复任务重试：{}", error.getClass().getSimpleName());
        }
    }

    @Scheduled(initialDelay = 20000L, fixedDelay = 60000L)
    public void recoverMissedRun() {
        service.recoverExpiredRuns();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        try {
            service.schedule(calendar.latestCompletedSession(now), "RECOVERY");
        } catch (RuntimeException error) {
            log.warn("股票发现补跑等待有效交易日历：{}", error.getClass().getSimpleName());
        }
    }

    @Scheduled(initialDelay = 45000L,
            fixedDelayString = "${finscope.stock-discovery.outcome-settlement-delay-ms:3600000}")
    public void settleMaturedOutcomes() {
        try {
            outcomeService.settlePending();
        } catch (RuntimeException error) {
            log.warn("股票发现真实结果结算批次失败，下个周期自动重试", error);
        }
    }

}
