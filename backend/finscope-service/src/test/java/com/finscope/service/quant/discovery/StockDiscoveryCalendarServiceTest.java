package com.finscope.service.quant.discovery;

import com.finscope.rpc.quote.PythonTradingCalendarClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockDiscoveryCalendarServiceTest {
    @Test
    void holidayRecoveryAndNextScheduleUseTheSameVerifiedCalendar() {
        PythonTradingCalendarClient client = mock(PythonTradingCalendarClient.class);
        StockDiscoveryCalendarService calendar = new StockDiscoveryCalendarService();
        ReflectionTestUtils.setField(calendar, "calendar", client);
        LocalDate closed = LocalDate.of(2026, 10, 5);
        LocalDate prior = LocalDate.of(2026, 9, 30);
        when(client.previousSession(closed.plusDays(1))).thenReturn(prior);
        when(client.nextSession(closed)).thenReturn(LocalDate.of(2026, 10, 8));
        ZonedDateTime now = ZonedDateTime.parse("2026-10-05T16:00:00+08:00[Asia/Shanghai]");
        assertFalse(calendar.isSession(closed));
        assertEquals(prior, calendar.latestCompletedSession(now));
        assertEquals("2026-10-08T15:30+08:00", calendar.nextScheduledAt(now));
        when(client.previousSession(closed)).thenReturn(prior);
        assertEquals(prior, calendar.latestCompletedSession(now.withHour(14)));
    }
}
