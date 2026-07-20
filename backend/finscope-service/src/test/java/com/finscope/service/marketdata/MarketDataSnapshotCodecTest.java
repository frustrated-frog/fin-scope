package com.finscope.service.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataSnapshot;
import com.finscope.domain.marketintel.DragonTigerRecord;
import com.finscope.domain.marketintel.DragonTigerSeat;
import com.finscope.rpc.marketintel.DragonTigerData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataSnapshotCodecTest {
    private final MarketDataSnapshotCodec codec =
            new MarketDataSnapshotCodec(new ObjectMapper().findAndRegisterModules());

    @Test
    void dragonTigerRecordsAndSeatsRoundTripWithSchemaAndHashValidation() {
        DragonTigerData data = data();
        MarketDataSnapshot snapshot = codec.dragonTigerSnapshot(
                "DRAGON_TIGER:7:2026-03-19:2026-07-16",
                "EASTMONEY_DRAGON_TIGER", "EASTMONEY", data,
                LocalDateTime.of(2026, 7, 15, 15, 30),
                LocalDateTime.of(2026, 7, 16, 16, 0),
                LocalDateTime.of(2026, 7, 16, 16, 1));

        assertEquals(MarketDataCapability.DRAGON_TIGER, snapshot.getCapability());
        assertEquals(codec.dragonTigerSchemaVersion(), snapshot.getSchemaVersion());
        DragonTigerData restored = codec.decodeDragonTiger(snapshot).orElseThrow(AssertionError::new);
        assertEquals("100373909", restored.getRecords().get(0).getExternalId());
        assertEquals("机构专用", restored.getRecords().get(0).getBuySeats().get(0).getSeatName());
    }

    @Test
    void rejectsDragonTigerSnapshotsWithDamagedPayloadOrWrongCapability() {
        MarketDataSnapshot valid = codec.dragonTigerSnapshot(
                "scope", "EASTMONEY_DRAGON_TIGER", "EASTMONEY", data(),
                LocalDateTime.of(2026, 7, 15, 15, 30),
                LocalDateTime.of(2026, 7, 16, 16, 0),
                LocalDateTime.of(2026, 7, 16, 16, 1));
        MarketDataSnapshot damaged = new MarketDataSnapshot(
                valid.getCapability(), valid.getScopeKey(), valid.getProviderCode(),
                valid.getProviderFamily(), valid.getAsOf(), valid.getRetrievedAt(),
                valid.getPayloadJson() + " ", valid.getPayloadHash(),
                valid.getSchemaVersion(), valid.getUpdatedAt());
        MarketDataSnapshot wrongCapability = new MarketDataSnapshot(
                MarketDataCapability.SECTOR_CATALOG, valid.getScopeKey(), valid.getProviderCode(),
                valid.getProviderFamily(), valid.getAsOf(), valid.getRetrievedAt(),
                valid.getPayloadJson(), valid.getPayloadHash(),
                valid.getSchemaVersion(), valid.getUpdatedAt());

        assertFalse(codec.decodeDragonTiger(damaged).isPresent());
        assertFalse(codec.decodeDragonTiger(wrongCapability).isPresent());
        assertTrue(codec.decodeDragonTiger(valid).isPresent());
    }

    private DragonTigerData data() {
        DragonTigerSeat seat = new DragonTigerSeat();
        seat.setDirection("BUY");
        seat.setRank(1);
        seat.setSeatCode("0");
        seat.setSeatName("机构专用");
        seat.setInstitutional(true);
        seat.setRetrievedAt(LocalDateTime.of(2026, 7, 16, 16, 0));
        seat.setPayloadHash("seat");
        DragonTigerRecord record = new DragonTigerRecord();
        record.setInstrumentId(7L);
        record.setProviderCode("EASTMONEY_DRAGON_TIGER");
        record.setTradeDate(LocalDate.of(2026, 7, 15));
        record.setExternalId("100373909");
        record.setReason("日跌幅偏离值达到7%的前5只证券");
        record.setRetrievedAt(LocalDateTime.of(2026, 7, 16, 16, 0));
        record.setPayloadHash("record");
        record.setQualityStatus("COMPLETE");
        record.setSeats(Collections.singletonList(seat));
        return new DragonTigerData(Collections.singletonList(record), Collections.emptyList());
    }
}
