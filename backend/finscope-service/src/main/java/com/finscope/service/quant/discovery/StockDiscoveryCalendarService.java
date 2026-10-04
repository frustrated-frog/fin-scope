package com.finscope.service.quant.discovery;

import com.finscope.rpc.quote.PythonTradingCalendarClient;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

@Service
public class StockDiscoveryCalendarService {
    private static final LocalTime AFTER_CLOSE = LocalTime.of(15, 30);
    @Resource
    private PythonTradingCalendarClient calendar;

    public boolean isSession(LocalDate date) {
        return date.equals(calendar.previousSession(date.plusDays(1)));
    }

    public LocalDate latestCompletedSession(ZonedDateTime now) {
        LocalDate before = now.toLocalDate();
        if (!now.toLocalTime().isBefore(AFTER_CLOSE)) {
            before = before.plusDays(1);
        }
        return calendar.previousSession(before);
    }

    public String nextScheduledAt(ZonedDateTime now) {
        LocalDate after = now.toLocalDate();
        if (now.toLocalTime().isBefore(AFTER_CLOSE)) {
            after = after.minusDays(1);
        }
        return calendar.nextSession(after).atTime(AFTER_CLOSE).atZone(now.getZone()).toOffsetDateTime().toString();
    }
}
