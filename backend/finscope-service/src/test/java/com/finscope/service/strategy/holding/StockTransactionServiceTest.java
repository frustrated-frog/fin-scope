package com.finscope.service.strategy.holding;

import com.finscope.dao.strategy.StockTransactionRepository;
import com.finscope.dao.strategy.StrategyHoldingRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.strategy.holding.StockTransaction;
import com.finscope.domain.strategy.holding.StockTransactionType;
import com.finscope.service.strategy.StrategyInstrumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockTransactionServiceTest {
    private StockTransactionRepository repository;
    private StockTransactionService service;

    @BeforeEach
    void setUp() {
        repository = mock(StockTransactionRepository.class);
        StrategyInstrumentResolver resolver = mock(StrategyInstrumentResolver.class);
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        instrument.setCode("600570");
        instrument.setName("恒生电子");
        when(resolver.resolve("600570", "STOCK")).thenReturn(instrument);
        when(repository.findAll(1000)).thenReturn(Collections.emptyList());
        service = new StockTransactionService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "instrumentResolver", resolver);
        ReflectionTestUtils.setField(service, "accountingService", new StockPositionAccountingService());
        ReflectionTestUtils.setField(service, "holdingRepository", mock(StrategyHoldingRepository.class));
    }

    @Test
    void returnsExistingEventForDuplicateClientRequest() {
        StockTransaction request = buy("same-request", "100");
        StockTransaction existing = buy("same-request", "100");
        existing.setId(88L);
        when(repository.findByClientRequestId("same-request")).thenReturn(Optional.of(existing));

        StockTransaction result = service.create("600570", request);

        assertSame(existing, result);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsBuyThatIsNotWholeBoardLot() {
        StockTransaction request = buy("odd-lot", "150");
        when(repository.findByClientRequestId("odd-lot")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create("600570", request));

        assertEquals("A 股买入数量必须是 100 股的整数倍", error.getMessage());
    }

    @Test
    void reclassifiesHistoricalBuyAsOpeningBalanceWithImmutableEvents() {
        StockTransaction original = buy("original-buy", "100");
        original.setId(9L);
        original.setInstrumentId(7L);
        original.setInstrumentCode("600570.SH");
        original.setInstrumentName("恒生电子");
        when(repository.findById(9L)).thenReturn(Optional.of(original));
        when(repository.findByClientRequestId(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            StockTransaction value = invocation.getArgument(0);
            value.setId(value.getType() == StockTransactionType.REVERSAL ? 10L : 11L);
            return value;
        });

        StockTransaction replacement = service.reclassifyAsOpeningBalance(
                9L, "repair-9", LocalDate.of(2026, 9, 4));

        assertEquals(StockTransactionType.OPENING_BALANCE, replacement.getType());
        assertEquals(LocalDate.of(2026, 8, 31), replacement.getTradeDate());
        assertEquals(new BigDecimal("100"), replacement.getQuantity());
        assertEquals(new BigDecimal("28.50"), replacement.getPrice());
        assertEquals("repair-9:opening", replacement.getClientRequestId());
        verify(repository, org.mockito.Mockito.times(2)).save(any());
    }

    private StockTransaction buy(String requestId, String quantity) {
        StockTransaction value = new StockTransaction();
        value.setClientRequestId(requestId);
        value.setType(StockTransactionType.BUY);
        value.setTradeDate(LocalDate.of(2026, 8, 31));
        value.setQuantity(new BigDecimal(quantity));
        value.setPrice(new BigDecimal("28.50"));
        value.setCommission(new BigDecimal("5"));
        value.setStampDuty(BigDecimal.ZERO);
        value.setTransferFee(BigDecimal.ZERO);
        value.setOtherFee(BigDecimal.ZERO);
        value.setCashAmount(BigDecimal.ZERO);
        return value;
    }
}
