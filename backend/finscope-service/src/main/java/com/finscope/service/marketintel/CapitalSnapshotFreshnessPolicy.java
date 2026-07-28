package com.finscope.service.marketintel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 按 A 股交易时段判断资金事实是否仍可用于研究判断。 */
@Component
public class CapitalSnapshotFreshnessPolicy {
    private static final LocalTime OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime CLOSE = LocalTime.of(15, 0);
    private static final long MAX_FUTURE_CLOCK_SKEW_MINUTES = 5L;

    private final Clock clock;
    private final long maxLagMinutes;

    @Autowired
    public CapitalSnapshotFreshnessPolicy(
            @Value("${finscope.market-data.capital-max-lag-minutes:15}") long maxLagMinutes) {
        this(Clock.systemDefaultZone(), maxLagMinutes);
    }

    CapitalSnapshotFreshnessPolicy(Clock clock, long maxLagMinutes) {
        if (maxLagMinutes < 0L) throw new IllegalArgumentException("max lag must not be negative");
        this.clock = clock;
        this.maxLagMinutes = maxLagMinutes;
    }

    public boolean isFresh(LocalDateTime factAsOf) {
        return isFresh(factAsOf, LocalDateTime.now(clock));
    }

    boolean isFresh(LocalDateTime factAsOf, LocalDateTime now) {
        if (factAsOf == null || now == null
                || factAsOf.isAfter(now.plusMinutes(MAX_FUTURE_CLOCK_SKEW_MINUTES))) {
            return false;
        }
        return !factAsOf.isBefore(minimumExpectedAsOf(now));
    }

    LocalDateTime minimumExpectedAsOf(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        if (isWeekend(date) || time.isBefore(OPEN)) {
            return previousTradingDay(date).atTime(CLOSE).minusMinutes(maxLagMinutes);
        }
        if (!time.isAfter(MORNING_CLOSE)) {
            return now.minusMinutes(maxLagMinutes);
        }
        if (time.isBefore(AFTERNOON_OPEN)) {
            return date.atTime(MORNING_CLOSE).minusMinutes(maxLagMinutes);
        }
        if (!time.isAfter(CLOSE)) {
            return now.minusMinutes(maxLagMinutes);
        }
        return date.atTime(CLOSE).minusMinutes(maxLagMinutes);
    }

    private LocalDate previousTradingDay(LocalDate value) {
        LocalDate candidate = value.minusDays(1);
        while (isWeekend(candidate)) candidate = candidate.minusDays(1);
        return candidate;
    }

    private boolean isWeekend(LocalDate value) {
        return value.getDayOfWeek() == DayOfWeek.SATURDAY
                || value.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
