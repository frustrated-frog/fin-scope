package com.finscope.service.supplychain;

import com.finscope.common.exception.BusinessConflictException;
import com.finscope.dao.supplychain.StockSupplyChainRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.supplychain.StockSupplyChainRefreshRun;
import com.finscope.domain.supplychain.StockSupplyChainSnapshot;
import com.finscope.service.strategy.StrategyInstrumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockSupplyChainServiceTest {
    private StrategyInstrumentResolver resolver;
    private StockSupplyChainRepository repository;
    private StockSupplyChainRefreshExecutor executor;
    private StockSupplyChainService service;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        resolver = mock(StrategyInstrumentResolver.class);
        repository = mock(StockSupplyChainRepository.class);
        executor = mock(StockSupplyChainRefreshExecutor.class);
        service = new StockSupplyChainService(resolver, repository, executor);
        instrument = new Instrument();
        instrument.setId(7L);
        instrument.setCode("688012");
        instrument.setName("中微公司");
        instrument.setType("STOCK");
        when(resolver.resolve("688012", "STOCK")).thenReturn(instrument);
    }

    @Test
    void readsAnEmptyViewForAStockThatHasNotBeenResearched() {
        when(repository.findSnapshot(7L)).thenReturn(Optional.empty());
        when(repository.latestRun(7L)).thenReturn(Optional.empty());

        StockSupplyChainService.StockSupplyChainView result = service.get("688012");

        assertEquals("688012", result.getCode());
        assertEquals("中微公司", result.getName());
        assertNull(result.getSnapshot());
        assertNull(result.getRefreshRun());
        verify(resolver).resolve("688012", "STOCK");
    }

    @Test
    void createsAndSchedulesARefreshForFundHeldStocksToo() {
        StockSupplyChainRefreshRun run = running(LocalDateTime.now());
        when(repository.activeRun(7L)).thenReturn(Optional.empty());
        when(repository.createRun(7L)).thenReturn(run);

        StockSupplyChainRefreshRun result = service.refresh("688012");

        assertEquals(run, result);
        verify(executor).schedule(instrument, run);
    }

    @Test
    void rejectsAnotherRefreshWhileTheCurrentLeaseIsActive() {
        when(repository.activeRun(7L)).thenReturn(Optional.of(running(LocalDateTime.now())));

        assertThrows(BusinessConflictException.class, () -> service.refresh("688012"));
    }

    @Test
    void expiresAnInterruptedRefreshAndStartsANewRun() {
        StockSupplyChainRefreshRun stale = running(LocalDateTime.now().minusMinutes(31));
        StockSupplyChainRefreshRun next = running(LocalDateTime.now());
        when(repository.activeRun(7L)).thenReturn(Optional.of(stale));
        when(repository.createRun(7L)).thenReturn(next);

        service.refresh("688012");

        assertEquals("FAILED", stale.getStatus());
        assertEquals("STALE_RUN_EXPIRED", stale.getErrorCode());
        verify(repository).updateRun(stale);
        verify(executor).schedule(instrument, next);
    }

    @Test
    void reportsQueueRejectionWithoutDeletingTheStoredSnapshot() {
        StockSupplyChainRefreshRun run = running(LocalDateTime.now());
        when(repository.activeRun(7L)).thenReturn(Optional.empty());
        when(repository.createRun(7L)).thenReturn(run);
        org.mockito.Mockito.doThrow(new IllegalStateException("queue full"))
                .when(executor).schedule(instrument, run);

        StockSupplyChainRefreshRun result = service.refresh("688012");

        assertEquals("FAILED", result.getStatus());
        assertEquals("QUEUE_REJECTED", result.getErrorCode());
        verify(repository).updateRun(run);
    }

    private StockSupplyChainRefreshRun running(LocalDateTime createdAt) {
        StockSupplyChainRefreshRun value = new StockSupplyChainRefreshRun();
        value.setId(9L);
        value.setInstrumentId(7L);
        value.setStatus("RUNNING");
        value.setStage("QUEUED");
        value.setCreatedAt(createdAt);
        return value;
    }
}
