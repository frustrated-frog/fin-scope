package com.finscope.domain.marketintel;

import com.finscope.domain.marketdata.MarketDataCapability;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonTigerContractTest {

    @Test
    void keepsSeatFactsAndExplicitLabelsWithoutInferringInvestorIdentity() {
        DragonTigerSeat seat = new DragonTigerSeat();
        seat.setDirection("BUY");
        seat.setRank(1);
        seat.setSeatName("机构专用");
        seat.setInstitutional(true);

        DragonTigerRecord record = new DragonTigerRecord();
        record.setInstrumentId(7L);
        record.setTradeDate(LocalDate.of(2026, 7, 15));
        record.setExternalId("100373909");
        record.setReason("日跌幅偏离值达到7%的前5只证券");
        record.setSeats(Collections.singletonList(seat));

        assertEquals("BUY", record.getSeats().get(0).getDirection());
        assertTrue(record.getSeats().get(0).isInstitutional());
        assertTrue(Arrays.asList(MarketDataCapability.values())
                .contains(MarketDataCapability.DRAGON_TIGER));
    }

    @Test
    void defensivelyCopiesAndSortsSeatsByDirectionAndRank() {
        DragonTigerSeat second = seat("BUY", 2, "买二");
        DragonTigerSeat first = seat("BUY", 1, "买一");
        DragonTigerSeat sell = seat("SELL", 1, "卖一");

        DragonTigerRecord record = new DragonTigerRecord();
        record.setSeats(Arrays.asList(second, sell, first));

        assertEquals(Arrays.asList(first, second), record.getBuySeats());
        assertEquals(Collections.singletonList(sell), record.getSellSeats());
        assertThrows(UnsupportedOperationException.class,
                () -> record.getBuySeats().add(seat("BUY", 3, "买三")));
    }

    private DragonTigerSeat seat(String direction, int rank, String name) {
        DragonTigerSeat seat = new DragonTigerSeat();
        seat.setDirection(direction);
        seat.setRank(rank);
        seat.setSeatName(name);
        return seat;
    }
}
