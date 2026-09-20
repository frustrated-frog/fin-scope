package com.finscope.service.investmentobservation;

import com.finscope.domain.investmentobservation.ReactionRefreshResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReactionRefreshSchedulerTest {
    @Test
    void dispatchesOffSchedulerThreadAndSkipsOverlapThenRecoversFromRejection() {
        ReactionRefreshService service = mock(ReactionRefreshService.class);
        when(service.refreshPending()).thenReturn(new ReactionRefreshResult());
        ReactionRefreshScheduler scheduler = new ReactionRefreshScheduler();
        ReactionDiscoveryService discovery = mock(ReactionDiscoveryService.class);
        when(discovery.discover()).thenReturn(new com.finscope.domain.investmentobservation.ReactionDiscoveryStatus());
        ReflectionTestUtils.setField(scheduler, "discovery", discovery);
        AtomicReference<Runnable> submitted = new AtomicReference<>();
        ReflectionTestUtils.setField(scheduler, "refreshService", service);
        ReflectionTestUtils.setField(scheduler, "executor", (Executor) submitted::set);
        scheduler.refreshAfterClose();
        Runnable first = submitted.get();
        assertNotNull(first);
        scheduler.refreshAfterClose();
        assertSame(first, submitted.get());
        verifyNoInteractions(service);
        first.run();
        verify(service).refreshPending();
        verify(discovery).discover();
        ReflectionTestUtils.setField(scheduler, "executor", (Executor) task -> {
            throw new RejectedExecutionException("full");
        });
        assertDoesNotThrow(scheduler::refreshAfterClose);
        ReflectionTestUtils.setField(scheduler, "executor", (Executor) Runnable::run);
        scheduler.refreshAfterClose();
        verify(service, times(2)).refreshPending();
    }
}
