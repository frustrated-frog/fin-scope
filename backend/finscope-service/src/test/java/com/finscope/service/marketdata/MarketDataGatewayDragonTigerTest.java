package com.finscope.service.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.marketdata.MarketDataRefreshRunRepository;
import com.finscope.dao.marketdata.MarketDataSnapshotRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.domain.marketintel.DragonTigerRecord;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.DragonTigerData;
import com.finscope.rpc.marketintel.DragonTigerProvider;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataGatewayDragonTigerTest {
    @TempDir Path tempDir;
    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 16, 16, 0)
                    .toInstant(ZoneOffset.ofHours(8)), ZoneOffset.ofHours(8));
    private MarketDataSnapshotRepository snapshots;
    private MarketDataRefreshRunRepository runs;
    private MarketDataSnapshotCodec codec;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("dragon-tiger-gateway.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createTables(jdbc);
        snapshots = new MarketDataSnapshotRepository(jdbc);
        runs = new MarketDataRefreshRunRepository(jdbc);
        codec = new MarketDataSnapshotCodec(new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void routesDragonTigerThroughTheHealthyProvider() {
        DragonTigerGatewayResult result = gateway(provider(data())).fetchDragonTiger(
                instrument(), LocalDate.of(2026, 3, 19), LocalDate.of(2026, 7, 16));

        assertEquals(MarketDataQualityStatus.FRESH_PRIMARY, result.getQualityStatus());
        assertEquals("EASTMONEY_DRAGON_TIGER", result.getSourceCode());
        assertEquals(1, result.getData().getRecords().size());
    }

    @Test
    void successfulEmptySetIsFreshPrimary() {
        DragonTigerGatewayResult result = gateway(provider(new DragonTigerData(
                Collections.emptyList(), Collections.emptyList()))).fetchDragonTiger(
                instrument(), LocalDate.of(2026, 3, 19), LocalDate.of(2026, 7, 16));

        assertEquals(MarketDataQualityStatus.FRESH_PRIMARY, result.getQualityStatus());
        assertTrue(result.getData().getRecords().isEmpty());
    }

    @Test
    void fallsBackToStoredSnapshotWhenTheRollingWindowMovesToTheNextDay() {
        gateway(provider(data())).fetchDragonTiger(
                instrument(), LocalDate.of(2026, 3, 19), LocalDate.of(2026, 7, 16));
        FakeDragonTigerProvider failing = provider(data());
        failing.failure = new ProviderContractException("TIMEOUT", "upstream timeout", true);

        DragonTigerGatewayResult result = gateway(failing).fetchDragonTiger(
                instrument(), LocalDate.of(2026, 3, 20), LocalDate.of(2026, 7, 17));

        assertEquals(MarketDataQualityStatus.STALE_FALLBACK, result.getQualityStatus());
        assertEquals(1, result.getData().getRecords().size());
        assertTrue(result.getWarning().contains("最近一次成功"));
    }

    private MarketDataGateway gateway(DragonTigerProvider provider) {
        ProviderRequestGuard guard = new ProviderRequestGuard(clock, millis -> { },
                Duration.ZERO, 0, 3, Duration.ofSeconds(60));
        return new MarketDataGateway(Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.singletonList(provider),
                new ProviderRoutePolicy(guard), guard, snapshots, runs, codec,
                new QuoteQualityValidator(clock), new MarketDataSingleFlight(),
                new MarketDataGatewayProperties(30_000, 30, 800), Runnable::run, clock);
    }

    private FakeDragonTigerProvider provider(DragonTigerData data) {
        FakeDragonTigerProvider provider = new FakeDragonTigerProvider();
        provider.data = data;
        return provider;
    }

    private Instrument instrument() {
        Instrument value = new Instrument();
        value.setId(7L);
        value.setType("STOCK");
        value.setMarket("SZ");
        value.setCode("000021");
        return value;
    }

    private DragonTigerData data() {
        DragonTigerRecord record = new DragonTigerRecord();
        record.setInstrumentId(7L);
        record.setProviderCode("EASTMONEY_DRAGON_TIGER");
        record.setTradeDate(LocalDate.of(2026, 7, 15));
        record.setExternalId("100373909");
        record.setReason("日跌幅偏离值达到7%的前5只证券");
        record.setRetrievedAt(LocalDateTime.of(2026, 7, 16, 16, 0));
        record.setPayloadHash("record");
        record.setQualityStatus("COMPLETE");
        return new DragonTigerData(Collections.singletonList(record), Collections.emptyList());
    }

    private void createTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE market_data_snapshot(capability TEXT NOT NULL,scope_key TEXT NOT NULL,"
                + "provider_code TEXT NOT NULL,provider_family TEXT NOT NULL,as_of TEXT,retrieved_at TEXT NOT NULL,"
                + "payload_json TEXT NOT NULL,payload_hash TEXT NOT NULL,schema_version INTEGER NOT NULL,"
                + "updated_at TEXT NOT NULL,PRIMARY KEY(capability,scope_key))");
        jdbc.execute("CREATE TABLE market_data_refresh_run(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "capability TEXT NOT NULL,scope_summary TEXT NOT NULL,trigger_type TEXT NOT NULL,status TEXT NOT NULL,"
                + "started_at TEXT NOT NULL,finished_at TEXT,requested_count INTEGER NOT NULL DEFAULT 0,"
                + "fresh_count INTEGER NOT NULL DEFAULT 0,stale_count INTEGER NOT NULL DEFAULT 0,"
                + "failed_count INTEGER NOT NULL DEFAULT 0,selected_sources TEXT,warning_message TEXT)");
    }

    private static final class FakeDragonTigerProvider implements DragonTigerProvider {
        private DragonTigerData data;
        private RuntimeException failure;

        public String providerCode() { return "EASTMONEY_DRAGON_TIGER"; }
        public String providerFamily() { return "EASTMONEY"; }
        public Set<MarketDataCapability> capabilities() {
            return Collections.singleton(MarketDataCapability.DRAGON_TIGER);
        }
        public int priority() { return 10; }
        public int batchLimit() { return 1; }
        public Duration minimumInterval() { return Duration.ZERO; }
        public Duration timeout() { return Duration.ofSeconds(1); }
        public boolean supports(Instrument instrument) { return true; }
        public ProviderResult<DragonTigerData> fetch(
                Instrument instrument, LocalDate startDate, LocalDate endDate) {
            if (failure != null) throw failure;
            return ProviderResult.of(data, LocalDateTime.of(2026, 7, 16, 16, 0),
                    "dragon-tiger", data.getWarnings());
        }
    }
}
