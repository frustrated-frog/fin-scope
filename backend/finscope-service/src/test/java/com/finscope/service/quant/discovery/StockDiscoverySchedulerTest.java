package com.finscope.service.quant.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import java.time.LocalDate;

class StockDiscoverySchedulerTest {
    @Test
    void recoversExpiredHistoricalRunsBeforeSchedulingTheLatestBusinessDate() {
        StockDiscoveryService service = mock(StockDiscoveryService.class);
        StockDiscoveryScheduler scheduler = new StockDiscoveryScheduler();
        ReflectionTestUtils.setField(scheduler, "service", service);
        StockDiscoveryCalendarService calendar = mock(StockDiscoveryCalendarService.class);
        when(calendar.latestCompletedSession(any())).thenReturn(LocalDate.of(2026, 9, 30));
        ReflectionTestUtils.setField(scheduler, "calendar", calendar);

        scheduler.recoverMissedRun();

        var order = inOrder(service);
        order.verify(service).recoverExpiredRuns();
        order.verify(service).schedule(eq(LocalDate.of(2026, 9, 30)), eq("RECOVERY"));
    }

    @Test
    void unavailableCalendarDoesNotScheduleAnInventedWeekday() {
        StockDiscoveryService service = mock(StockDiscoveryService.class);
        StockDiscoveryCalendarService calendar = mock(StockDiscoveryCalendarService.class);
        StockDiscoveryScheduler scheduler = new StockDiscoveryScheduler();
        ReflectionTestUtils.setField(scheduler, "service", service);
        ReflectionTestUtils.setField(scheduler, "calendar", calendar);
        when(calendar.latestCompletedSession(any())).thenThrow(new IllegalStateException("offline"));
        scheduler.recoverMissedRun();
        scheduler.scheduleAfterClose();
        verify(service, never()).schedule(any(), any());
    }
}
