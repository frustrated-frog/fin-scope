package com.finscope.service.quant.overnight;

import com.finscope.domain.quant.overnight.OvernightAutomationContext;
import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.rpc.quant.PythonOvernightClient;
import com.finscope.service.strategy.holding.StockTransactionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OvernightAutomationSchedulerTest {
    @Test
    void automaticallySynchronizesOnlyRealOpenPositionsWithoutFetchingValuationQuotes() {
        StockTransactionService transactions = mock(StockTransactionService.class);
        PythonOvernightClient client = mock(PythonOvernightClient.class);
        StockPosition open = new StockPosition();
        open.setInstrumentCode("605058.SH");
        open.setQuantity(new BigDecimal("200"));
        open.setAverageCost(new BigDecimal("21.25"));
        open.setOpenedOn(LocalDate.of(2026, 9, 21));
        StockPosition closed = new StockPosition();
        StockAccountSnapshot account = new StockAccountSnapshot();
        account.setPositions(List.of(open, closed));
        when(transactions.account()).thenReturn(account);
        OvernightAutomationScheduler scheduler = new OvernightAutomationScheduler();
        ReflectionTestUtils.setField(scheduler, "transactions", transactions);
        ReflectionTestUtils.setField(scheduler, "client", client);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "candidateLimit", 6);
        ReflectionTestUtils.setField(scheduler, "tailCostBps", 20);
        ReflectionTestUtils.setField(scheduler, "holdingCostBps", 10);
        scheduler.synchronizeLedger();
        ArgumentCaptor<OvernightAutomationContext> argument = ArgumentCaptor.forClass(OvernightAutomationContext.class);
        verify(client).syncAutomationContext(argument.capture());
        OvernightAutomationContext context = argument.getValue();
        assertTrue(context.isEnabled());
        assertEquals(1, context.getPositions().size());
        assertEquals(open.getAverageCost(), context.getPositions().get(0).getAverageCost());
        assertEquals(open.getOpenedOn(), context.getPositions().get(0).getOpenedOn());
        assertEquals(10, context.getHoldingCostBps());
        doThrow(new IllegalStateException("offline")).when(client).syncAutomationContext(any());
        assertDoesNotThrow(scheduler::synchronizeLedger);
    }
}
