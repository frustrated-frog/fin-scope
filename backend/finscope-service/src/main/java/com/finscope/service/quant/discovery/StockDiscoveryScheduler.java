package com.finscope.service.quant.discovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@Slf4j
public class StockDiscoveryScheduler {
    @Resource
    private StockDiscoveryService service;
    @Resource
    private StockDiscoveryOutcomeService outcomeService;

    @Scheduled(cron = "${finscope.stock-discovery.cron:0 30 15 * * MON-FRI}", zone = "Asia/Shanghai")
    public void scheduleAfterClose() {
        service.schedule(LocalDate.now(ZoneId.of("Asia/Shanghai")), "SCHEDULED");
    }

    @Scheduled(initialDelay = 20000L, fixedDelay = 3600000L)
    public void recoverMissedRun() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        LocalDate candidate = now.toLocalDate();
        if (now.toLocalTime().isBefore(LocalTime.of(15, 30))) {
            candidate = candidate.minusDays(1);
        }
        LocalDate date = previousWeekday(candidate);
        service.schedule(date, "RECOVERY");
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

    private LocalDate previousWeekday(LocalDate today) {
        LocalDate value = today;
        while (value.getDayOfWeek() == DayOfWeek.SATURDAY
                || value.getDayOfWeek() == DayOfWeek.SUNDAY) {
            value = value.minusDays(1);
        }
        return value;
    }
}
