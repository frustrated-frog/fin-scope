package com.finscope.service.strategy.holding;

import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.domain.strategy.holding.StockTransaction;
import com.finscope.domain.strategy.holding.StockTransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockPositionAccountingServiceTest {
    private final StockPositionAccountingService service = new StockPositionAccountingService();

    @Test
    void replaysWeightedCostPartialSellDividendAndBonusShares() {
        StockAccountSnapshot account = service.replay(Arrays.asList(
                event(1, StockTransactionType.OPENING_BALANCE, "100", "10", "0"),
                event(2, StockTransactionType.BUY, "100", "12", "10"),
                event(3, StockTransactionType.SELL, "100", "13", "7"),
                dividendEvent(4, "50"),
                event(5, StockTransactionType.BONUS_SHARE, "20", "0", "0")));

        StockPosition position = account.getPositions().get(0);
        assertMoney("120", position.getQuantity());
        assertMoney("1105", position.getTotalCost());
        assertMoney("9.20833333", position.getAverageCost());
        assertMoney("188", position.getRealizedProfit());
        assertMoney("50", position.getDividendIncome());
        assertMoney("133", account.getCash());
    }

    @Test
    void excludesReversedEventFromReplay() {
        StockTransaction buy = event(1, StockTransactionType.BUY, "100", "10", "5");
        StockTransaction reversal = cashEvent(2, StockTransactionType.REVERSAL, "0");
        reversal.setReversalOfId(1L);

        StockAccountSnapshot account = service.replay(Arrays.asList(buy, reversal));

        assertEquals(Collections.emptyList(), account.getPositions());
        assertMoney("0", account.getCash());
    }

    @Test
    void openingBalanceKeepsCashUnregisteredAndCapturesEntryDate() {
        StockTransaction opening = event(1, StockTransactionType.OPENING_BALANCE,
                "100", "32.49", "0");
        opening.setTradeDate(LocalDate.of(2026, 7, 15));

        StockAccountSnapshot account = service.replay(Collections.singletonList(opening));

        assertMoney("0", account.getCash());
        assertEquals(false, account.isCashTracked());
        assertEquals(LocalDate.of(2026, 7, 15), account.getPositions().get(0).getOpenedOn());
        assertEquals(true, account.getPositions().get(0).isOpeningBalance());
    }

    @Test
    void rejectsSellingMoreThanCurrentPosition() {
        assertThrows(IllegalArgumentException.class, () -> service.replay(Arrays.asList(
                event(1, StockTransactionType.BUY, "100", "10", "0"),
                event(2, StockTransactionType.SELL, "200", "11", "0"))));
    }

    private StockTransaction event(long id, StockTransactionType type, String quantity,
                                   String price, String fees) {
        StockTransaction value = cashEvent(id, type, "0");
        value.setInstrumentId(9L);
        value.setInstrumentCode("600570.SH");
        value.setInstrumentName("恒生电子");
        value.setQuantity(new BigDecimal(quantity));
        value.setPrice(new BigDecimal(price));
        value.setCommission(new BigDecimal(fees));
        return value;
    }

    private StockTransaction cashEvent(long id, StockTransactionType type, String cashAmount) {
        StockTransaction value = new StockTransaction();
        value.setId(id);
        value.setClientRequestId("event-" + id);
        value.setType(type);
        value.setTradeDate(LocalDate.of(2026, 8, (int) id));
        value.setCommission(BigDecimal.ZERO);
        value.setStampDuty(BigDecimal.ZERO);
        value.setTransferFee(BigDecimal.ZERO);
        value.setOtherFee(BigDecimal.ZERO);
        value.setCashAmount(new BigDecimal(cashAmount));
        return value;
    }

    private StockTransaction dividendEvent(long id, String cashAmount) {
        StockTransaction value = cashEvent(id, StockTransactionType.CASH_DIVIDEND, cashAmount);
        value.setInstrumentId(9L);
        value.setInstrumentCode("600570.SH");
        value.setInstrumentName("恒生电子");
        return value;
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
