package com.finscope.service.quant.discovery;

import com.finscope.dao.quant.StockDiscoveryRepository;
import com.finscope.domain.quant.discovery.StockDiscoveryEventPublisher;
import com.finscope.domain.quant.discovery.StockDiscoveryReport;
import com.finscope.domain.quant.discovery.StockDiscoveryRequestedEvent;
import com.finscope.domain.quant.discovery.StockDiscoveryRun;
import com.finscope.rpc.quant.PythonStockDiscoveryClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockDiscoveryServiceTest {
    @Test
    void dispatchesKafkaFallbackWithoutBlockingTheSchedulingThread() {
        StockDiscoveryRepository repository = mock(StockDiscoveryRepository.class);
        StockDiscoveryEventPublisher publisher = mock(StockDiscoveryEventPublisher.class);
        PythonStockDiscoveryClient client = mock(PythonStockDiscoveryClient.class);
        Executor executor = mock(Executor.class);
        StockDiscoveryRun run = run("CREATED");
        StockDiscoveryReport report = new StockDiscoveryReport();
        when(repository.createIfAbsent(any(), any(), any(Double.class), any(), any())).thenReturn(run);
        when(repository.findById(7L)).thenReturn(Optional.of(run));
        when(repository.tryMarkRunning(eq(7L), anyString())).thenReturn(true);
        when(publisher.publish(any())).thenReturn(false);
        when(client.discover(LocalDate.of(2026, 8, 14), 6000d, StockDiscoveryService.POLICY_VERSION))
                .thenReturn(report);
        StockDiscoveryService service = service(repository, publisher, client);
        ReflectionTestUtils.setField(service, "fallbackExecutor", executor);

        service.schedule(LocalDate.of(2026, 8, 14), "RECOVERY");

        verify(executor).execute(any(Runnable.class));
        verify(client, never()).discover(any(), any(Double.class), any());
    }

    @Test
    void fallbackWorkerExecutesAndFreezesTheReport() {
        StockDiscoveryRepository repository = mock(StockDiscoveryRepository.class);
        StockDiscoveryEventPublisher publisher = mock(StockDiscoveryEventPublisher.class);
        PythonStockDiscoveryClient client = mock(PythonStockDiscoveryClient.class);
        StockDiscoveryRun run = run("CREATED");
        StockDiscoveryReport report = new StockDiscoveryReport();
        when(repository.createIfAbsent(any(), any(), any(Double.class), any(), any())).thenReturn(run);
        when(repository.findById(7L)).thenReturn(Optional.of(run));
        when(repository.tryMarkRunning(eq(7L), anyString())).thenReturn(true);
        when(publisher.publish(any())).thenReturn(false);
        when(client.discover(LocalDate.of(2026, 8, 14), 6000d, StockDiscoveryService.POLICY_VERSION))
                .thenReturn(report);
        StockDiscoveryService service = service(repository, publisher, client);
        ReflectionTestUtils.setField(service, "fallbackExecutor", (Executor) Runnable::run);

        service.schedule(LocalDate.of(2026, 8, 14), "RECOVERY");

        verify(repository).tryMarkRunning(eq(7L), anyString());
        verify(repository).complete(eq(7L), anyString(), eq(report));
    }

    @Test
    void keepsSucceededBusinessDateIdempotent() {
        StockDiscoveryRepository repository = mock(StockDiscoveryRepository.class);
        StockDiscoveryEventPublisher publisher = mock(StockDiscoveryEventPublisher.class);
        PythonStockDiscoveryClient client = mock(PythonStockDiscoveryClient.class);
        StockDiscoveryRun run = run("SUCCEEDED");
        when(repository.createIfAbsent(any(), any(), any(Double.class), any(), any())).thenReturn(run);
        StockDiscoveryService service = service(repository, publisher, client);

        StockDiscoveryRun result = service.schedule(LocalDate.of(2026, 8, 14), "RECOVERY");

        assertEquals(7L, result.getId());
        verify(publisher, never()).publish(any());
        verify(client, never()).discover(any(), any(Double.class), any());
    }

    @Test
    void preservesFailedRunAndRethrowsForListenerRetry() {
        StockDiscoveryRepository repository = mock(StockDiscoveryRepository.class);
        StockDiscoveryEventPublisher publisher = mock(StockDiscoveryEventPublisher.class);
        PythonStockDiscoveryClient client = mock(PythonStockDiscoveryClient.class);
        StockDiscoveryRun run = run("FAILED");
        when(repository.findById(7L)).thenReturn(Optional.of(run));
        when(repository.tryMarkRunning(eq(7L), anyString())).thenReturn(true);
        when(client.discover(any(), any(Double.class), any())).thenThrow(new IllegalStateException("provider timeout"));
        StockDiscoveryService service = service(repository, publisher, client);
        StockDiscoveryRequestedEvent event = new StockDiscoveryRequestedEvent();
        event.setRunId(7L);
        event.setBusinessDate("2026-08-14");
        event.setBudget(6000d);
        event.setPolicyVersion(StockDiscoveryService.POLICY_VERSION);

        assertThrows(IllegalStateException.class, () -> service.execute(event));
        verify(repository).fail(eq(7L), anyString(), eq("provider timeout"));
    }

    @Test
    void ignoresDuplicateExecutionWhenTheRunWasAlreadyClaimed() {
        StockDiscoveryRepository repository = mock(StockDiscoveryRepository.class);
        StockDiscoveryEventPublisher publisher = mock(StockDiscoveryEventPublisher.class);
        PythonStockDiscoveryClient client = mock(PythonStockDiscoveryClient.class);
        StockDiscoveryRun run = run("RUNNING");
        when(repository.findById(7L)).thenReturn(Optional.of(run));
        when(repository.tryMarkRunning(eq(7L), anyString())).thenReturn(false);
        StockDiscoveryService service = service(repository, publisher, client);
        StockDiscoveryRequestedEvent event = new StockDiscoveryRequestedEvent();
        event.setRunId(7L);
        event.setBusinessDate("2026-08-14");
        event.setBudget(6000d);
        event.setPolicyVersion(StockDiscoveryService.POLICY_VERSION);

        service.execute(event);

        verify(client, never()).discover(any(), any(Double.class), any());
        verify(repository, never()).complete(any(), anyString(), any());
        verify(repository, never()).fail(any(), anyString(), anyString());
    }

    private StockDiscoveryService service(StockDiscoveryRepository repository,
                                          StockDiscoveryEventPublisher publisher,
                                          PythonStockDiscoveryClient client) {
        StockDiscoveryService service = new StockDiscoveryService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "publisher", publisher);
        ReflectionTestUtils.setField(service, "client", client);
        return service;
    }

    private StockDiscoveryRun run(String status) {
        StockDiscoveryRun run = new StockDiscoveryRun();
        run.setId(7L);
        run.setRunKey("2026-08-14:" + StockDiscoveryService.POLICY_VERSION);
        run.setBusinessDate(LocalDate.of(2026, 8, 14));
        run.setBudget(6000d);
        run.setPolicyVersion(StockDiscoveryService.POLICY_VERSION);
        run.setStatus(status);
        return run;
    }
}
