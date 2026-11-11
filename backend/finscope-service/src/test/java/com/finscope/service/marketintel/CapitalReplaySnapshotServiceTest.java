package com.finscope.service.marketintel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.marketdata.MarketDataSnapshotRepository;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.CapitalReplayData;
import com.finscope.service.marketdata.MarketDataSnapshotCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CapitalReplaySnapshotServiceTest {
    private final MarketDataSnapshotRepository repository = mock(MarketDataSnapshotRepository.class);
    private final MarketDataSnapshotCodec codec = new MarketDataSnapshotCodec(new ObjectMapper().findAndRegisterModules());
    private final Map<String, MarketDataSnapshot> database = new HashMap<>();
    private final LocalDate day = LocalDate.of(2026, 11, 11);
    private CapitalReplaySnapshotService service;

    @BeforeEach
    void setUp() {
        service = service();
        when(repository.find(eq(MarketDataCapability.CAPITAL_REPLAY_1M), anyString()))
                .thenAnswer(call -> Optional.ofNullable(database.get(call.getArgument(1))));
        doAnswer(call -> {
            MarketDataSnapshot snapshot = call.getArgument(0);
            database.put(snapshot.getScopeKey(), snapshot);
            return null;
        }).when(repository).upsert(any());
    }

    @Test
    void retainsCompletePriceAndFlowEvidenceAcrossServiceRecreationAndOnlineFailure() {
        CapitalFlowPoint first = point(day.atTime(9, 31), "50", "1000");
        CapitalFlowPoint second = point(day.atTime(9, 32), "51", "2000");
        service.retain(40L, new CapitalReplayData(List.of(first, second), List.of(first, second), List.of()), day);

        CapitalReplayData restored = service().retain(40L, empty(), day);
        assertEquals(2, restored.getPrices().size());
        assertEquals(2, restored.getFlows().size());
        assertEquals(new BigDecimal("51"), restored.getPrices().get(1).getPrice());
        assertTrue(restored.getWarnings().contains("CAPITAL_REPLAY_SNAPSHOT_FALLBACK"));
    }

    @Test
    void sparseRefreshCannotEraseCompleteMinutesOrReplaceMinuteTurnoverWithMissingValue() {
        CapitalFlowPoint first = point(day.atTime(9, 31), "50", "1000");
        CapitalFlowPoint second = point(day.atTime(9, 32), "51", "2000");
        service.retain(40L, new CapitalReplayData(List.of(first, second), List.of(first, second), List.of()), day);
        CapitalFlowPoint partial = point(day.atTime(9, 32), "51", "2000");
        partial.setIntervalTradeAmount(null);
        partial.setLargeNetInflow(null);
        service.retain(40L, new CapitalReplayData(List.of(partial), List.of(partial), List.of()), day);

        CapitalReplayData restored = service().retain(40L, empty(), day);
        assertEquals(2, restored.getPrices().size());
        assertEquals(new BigDecimal("2000"), restored.getPrices().get(1).getIntervalTradeAmount());
        assertEquals(2, restored.getFlows().size());
    }

    @Test
    void freshCompleteMinuteUpdatesItsOwnDateWithoutCrossingInstrumentsOrInventingHistory() {
        service.retain(40L, new CapitalReplayData(List.of(point(day.atTime(9, 31), "50", "1000")), List.of(), List.of()), day);
        CapitalFlowPoint updated = point(day.atTime(9, 31), "52", "1500");
        service.retain(40L, new CapitalReplayData(List.of(updated), List.of(), List.of()), day);
        assertEquals(new BigDecimal("52"), service.retain(40L, empty(), day).getPrices().get(0).getPrice());
        assertTrue(service.retain(41L, empty(), day).getPrices().isEmpty());
        assertTrue(service.retain(40L, empty(), day.minusDays(1)).getPrices().isEmpty());
    }

    @Test
    void refusesDamagedOrWrongSchemaSnapshots() {
        MarketDataSnapshot valid = codec.capitalReplaySnapshot("40:" + day,
                new CapitalReplayData(List.of(point(day.atTime(9, 31), "50", "1000")), List.of(), List.of()),
                day.atTime(9, 31), day.atTime(16, 0));
        MarketDataSnapshot damaged = new MarketDataSnapshot(valid.getCapability(), valid.getScopeKey(),
                valid.getProviderCode(), valid.getProviderFamily(), valid.getAsOf(), valid.getRetrievedAt(),
                valid.getPayloadJson() + " ", valid.getPayloadHash(), valid.getSchemaVersion(), valid.getUpdatedAt());
        assertTrue(codec.decodeCapitalReplay(damaged).isEmpty());
        MarketDataSnapshot wrongSchema = new MarketDataSnapshot(valid.getCapability(), valid.getScopeKey(),
                valid.getProviderCode(), valid.getProviderFamily(), valid.getAsOf(), valid.getRetrievedAt(),
                valid.getPayloadJson(), valid.getPayloadHash(), 99, valid.getUpdatedAt());
        assertTrue(codec.decodeCapitalReplay(wrongSchema).isEmpty());
    }

    @Test
    void failedSnapshotWriteDoesNotDiscardFreshEvidence() {
        doThrow(new IllegalStateException("database unavailable")).when(repository).upsert(any());
        CapitalReplayData result = service.retain(40L,
                new CapitalReplayData(List.of(point(day.atTime(9, 31), "50", "1000")), List.of(), List.of()), day);
        assertEquals(1, result.getPrices().size());
        assertTrue(result.getWarnings().contains("CAPITAL_REPLAY_SNAPSHOT_UNAVAILABLE"));
    }

    private CapitalReplaySnapshotService service() {
        CapitalReplaySnapshotService value = new CapitalReplaySnapshotService();
        ReflectionTestUtils.setField(value, "repository", repository);
        ReflectionTestUtils.setField(value, "codec", codec);
        return value;
    }

    private CapitalReplayData empty() {
        return new CapitalReplayData(List.of(), List.of(), List.of());
    }

    private CapitalFlowPoint point(LocalDateTime time, String price, String amount) {
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setObservedAt(time);
        point.setDataDate(time.toLocalDate());
        point.setProviderCode("EASTMONEY_CAPITAL_FLOW");
        point.setPrice(new BigDecimal(price));
        point.setIntervalTradeAmount(new BigDecimal(amount));
        point.setLargeNetInflow(BigDecimal.TEN);
        point.setSuperLargeNetInflow(BigDecimal.ONE);
        return point;
    }
}
