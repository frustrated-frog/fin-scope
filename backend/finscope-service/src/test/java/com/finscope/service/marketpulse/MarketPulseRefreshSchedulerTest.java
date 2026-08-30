package com.finscope.service.marketpulse;

import com.finscope.domain.marketpulse.MarketPulseRefreshResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketPulseRefreshSchedulerTest {
    private MarketPulseRefreshScheduler scheduler;
    private MarketPulseService service;

    @BeforeEach
    void setUp() {
        scheduler = new MarketPulseRefreshScheduler();
        service = mock(MarketPulseService.class);
        ReflectionTestUtils.setField(scheduler, "service", service);
        ReflectionTestUtils.setField(scheduler, "clock", Clock.fixed(
                Instant.parse("2026-08-21T07:30:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void afterCloseRefreshUsesTheCurrentShanghaiDate() {
        MarketPulseRefreshResult result = refreshResult(LocalDate.of(2026, 8, 21));
        when(service.refreshScheduled(LocalDate.of(2026, 8, 21))).thenReturn(Optional.of(result));

        scheduler.refreshAfterClose();

        verify(service).refreshScheduled(LocalDate.of(2026, 8, 21));
    }

    @Test
    void hourlyRecoveryChecksForAMissingFrozenSnapshot() {
        MarketPulseRefreshResult result = refreshResult(LocalDate.of(2026, 8, 21));
        when(service.recoverMissing()).thenReturn(Optional.of(result));

        scheduler.recoverMissedRefresh();

        verify(service).recoverMissing();
    }

    @Test
    void schedulingFailuresRemainAtTheJobBoundaryForTheNextRecovery() {
        doThrow(new IllegalStateException("行情源暂不可用"))
                .when(service).refreshScheduled(LocalDate.of(2026, 8, 21));
        doThrow(new IllegalStateException("数据库暂不可用")).when(service).recoverMissing();

        assertDoesNotThrow(scheduler::refreshAfterClose);
        assertDoesNotThrow(scheduler::recoverMissedRefresh);
    }

    private MarketPulseRefreshResult refreshResult(LocalDate businessDate) {
        MarketPulseRefreshResult value = new MarketPulseRefreshResult();
        value.setBusinessDate(businessDate);
        value.setStatus("SUCCEEDED");
        return value;
    }
}
