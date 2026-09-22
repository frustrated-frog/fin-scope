package com.finscope.service.quant.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class StockDiscoverySchedulerTest {
    @Test
    void recoversExpiredHistoricalRunsBeforeSchedulingTheLatestBusinessDate() {
        StockDiscoveryService service = mock(StockDiscoveryService.class);
        StockDiscoveryScheduler scheduler = new StockDiscoveryScheduler();
        ReflectionTestUtils.setField(scheduler, "service", service);

        scheduler.recoverMissedRun();

        var order = inOrder(service);
        order.verify(service).recoverExpiredRuns();
        order.verify(service).schedule(any(), eq("RECOVERY"));
    }
}
