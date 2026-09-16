package com.finscope.service.quant.overnight;

import com.finscope.common.enums.overnight.OvernightMode;
import com.finscope.domain.quant.overnight.OvernightResearchInput;
import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.rpc.quant.PythonOvernightClient;
import com.finscope.service.strategy.holding.StockAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OvernightResearchServiceTest {
    @Test
    void holdingUsesLedgerCostAndRejectsMissingPosition() {
        var service = new OvernightResearchService();
        var client = mock(PythonOvernightClient.class);
        var accounts = mock(StockAccountService.class);
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "accounts", accounts);
        var account = new StockAccountSnapshot();
        var position = new StockPosition();
        position.setInstrumentCode("605058.SH");
        position.setAverageCost(new BigDecimal("33.31"));
        position.setQuantity(new BigDecimal("100"));
        position.setOpenedOn(LocalDate.of(2026, 9, 14));
        account.setPositions(List.of(position));
        when(accounts.snapshot()).thenReturn(account);
        var input = input(OvernightMode.AFTER_CLOSE_HOLDING);
        input.setCostBasis(1d);
        service.generate(input);
        assertEquals(33.31, input.getCostBasis());
        assertEquals(100d, input.getQuantity());
        assertEquals("15:00", input.getCutoff());
        verify(client).generate(input);
        account.setPositions(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.generate(input));
    }

    @Test
    void entryDoesNotReadPositionsOrAcceptPostCloseCutoff() {
        var service = new OvernightResearchService();
        var client = mock(PythonOvernightClient.class);
        var accounts = mock(StockAccountService.class);
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "accounts", accounts);
        var input = input(OvernightMode.TAIL_ENTRY);
        input.setCostBasis(1d);
        service.generate(input);
        assertNull(input.getCostBasis());
        assertEquals("605058.SH", input.getInstrumentCode());
        verifyNoInteractions(accounts);
        input.setCutoff("15:00");
        assertThrows(IllegalArgumentException.class, () -> service.generate(input));
        input.setCutoff("14:30");
        input.setCostBps(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> service.generate(input));
    }

    private OvernightResearchInput input(OvernightMode mode) {
        var input = new OvernightResearchInput();
        input.setInstrumentCode("605058");
        input.setSignalDate(LocalDate.of(2026, 9, 16));
        input.setMode(mode);
        input.setCutoff("14:30");
        return input;
    }
}
