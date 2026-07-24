package com.finscope.service.marketdata;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** A 股交易时段及盘中降级数据年龄边界。 */
@Component
public class MarketTradingSession {
    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

    private final Clock clock;
    private final long maxIntradayFallbackAgeSeconds;

    @Autowired
    public MarketTradingSession(
            @Value("${finscope.market-data.max-intraday-fallback-age-seconds:120}")
            long maxIntradayFallbackAgeSeconds) {
        this(Clock.systemDefaultZone(), maxIntradayFallbackAgeSeconds);
    }

    MarketTradingSession(Clock clock, long maxIntradayFallbackAgeSeconds) {
        if (maxIntradayFallbackAgeSeconds < 0L) {
            throw new IllegalArgumentException("fallback age must not be negative");
        }
        this.clock = clock;
        this.maxIntradayFallbackAgeSeconds = maxIntradayFallbackAgeSeconds;
    }

    public boolean isOpenNow() {
        return isOpen(LocalDateTime.now(clock));
    }

    public boolean isOpen(LocalDateTime value) {
        if (value == null || value.getDayOfWeek() == DayOfWeek.SATURDAY
                || value.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = value.toLocalTime();
        return between(time, MORNING_OPEN, MORNING_CLOSE)
                || between(time, AFTERNOON_OPEN, AFTERNOON_CLOSE);
    }

    public boolean canServeFallback(LocalDateTime retrievedAt, LocalDateTime now) {
        if (retrievedAt == null || now == null || retrievedAt.isAfter(now)) return false;
        if (!isOpen(now)) return true;
        return Duration.between(retrievedAt, now).getSeconds()
                <= maxIntradayFallbackAgeSeconds;
    }

    private boolean between(LocalTime value, LocalTime start, LocalTime end) {
        return !value.isBefore(start) && !value.isAfter(end);
    }
}
