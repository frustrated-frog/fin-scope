package com.finscope.service.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.marketdata.MarketDataRefreshRunRepository;
import com.finscope.dao.marketdata.MarketDataSnapshotRepository;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import com.finscope.rpc.quote.SectorMarketProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataGatewaySectorCatalogTest {
    @TempDir Path tempDir;
    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 14, 10, 0).toInstant(ZoneOffset.ofHours(8)), ZoneOffset.ofHours(8));
    private MarketDataSnapshotRepository snapshots;
    private MarketDataRefreshRunRepository runs;
    private MarketDataSnapshotCodec codec;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("sector-gateway.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createTables(jdbc);
        snapshots = new MarketDataSnapshotRepository(jdbc);
        runs = new MarketDataRefreshRunRepository(jdbc);
        codec = new MarketDataSnapshotCodec(new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void suspiciousCoverageDropDoesNotOverwriteLastGoodCatalog() {
        FakeSectorProvider provider = new FakeSectorProvider("EASTMONEY_SECTOR", 10);
        provider.entries = entries(10);
        MarketDataGateway gateway = gateway(provider);
        assertEquals(10, gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, true)
                .getSnapshot().getEntries().size());

        provider.entries = entries(6);
        SectorCatalogGatewayResult degraded = gateway.fetchSectorCatalog(SectorCategory.INDUSTRY, true);

        assertEquals(MarketDataQualityStatus.STALE_FALLBACK, degraded.getQualityStatus());
        assertEquals(10, degraded.getSnapshot().getEntries().size());
        assertTrue(degraded.getWarning().contains("数量异常下降"));
        assertEquals(10, codec.decodeSectorCatalog(snapshots.find(
                MarketDataCapability.SECTOR_CATALOG, "SECTOR_CATALOG:INDUSTRY").get())
                .get().getEntries().size());
    }

    @Test
    void sourceFamilyCutoverEstablishesANewCoverageBaseline() {
        FakeSectorProvider legacy = new FakeSectorProvider("SINA_SECTOR_CATALOG", "SINA", 10);
        legacy.entries = entries(149);
        assertEquals(149, gateway(legacy).fetchSectorCatalog(SectorCategory.INDUSTRY, true)
                .getSnapshot().getEntries().size());

        FakeSectorProvider tonghuashun = new FakeSectorProvider(
                "PYTHON_TONGHUASHUN_SECTOR", "TONGHUASHUN", 10);
        tonghuashun.entries = entries(90);

        SectorCatalogGatewayResult switched = gateway(tonghuashun)
                .fetchSectorCatalog(SectorCategory.INDUSTRY, true);

        assertEquals(MarketDataQualityStatus.FRESH_PRIMARY, switched.getQualityStatus());
        assertEquals("PYTHON_TONGHUASHUN_SECTOR", switched.getSourceCode());
        assertEquals(90, switched.getSnapshot().getEntries().size());
        assertEquals("TONGHUASHUN", snapshots.find(
                MarketDataCapability.SECTOR_CATALOG, "SECTOR_CATALOG:INDUSTRY")
                .get().getProviderFamily());
    }

    @Test
    void persistedCatalogRemainsAvailableAfterGatewayRestart() {
        FakeSectorProvider provider = new FakeSectorProvider("EASTMONEY_SECTOR", 10);
        provider.entries = entries(5);
        gateway(provider).fetchSectorCatalog(SectorCategory.INDUSTRY, true);
        provider.failure = new ProviderContractException("TIMEOUT", "upstream timeout", true);

        SectorCatalogGatewayResult recovered = gateway(provider)
                .fetchSectorCatalog(SectorCategory.INDUSTRY, true);

        assertEquals(MarketDataQualityStatus.STALE_FALLBACK, recovered.getQualityStatus());
        assertEquals(5, recovered.getSnapshot().getEntries().size());
        assertTrue(recovered.getWarning().contains("最近一次成功"));
        assertTrue(recovered.getStaleAgeSeconds() >= 0L);
    }

    @Test
    void rejectsLegacyNonThsCodesInsteadOfMixingThemIntoTheLastGoodCatalog() {
        FakeSectorProvider tonghuashun = new FakeSectorProvider(
                "PYTHON_TONGHUASHUN_SECTOR", "TONGHUASHUN", 10);
        tonghuashun.entries = entries(5);
        MarketDataGateway initial = gateway(tonghuashun);
        initial.fetchSectorCatalog(SectorCategory.INDUSTRY, true);

        tonghuashun.failure = new ProviderContractException("TIMEOUT", "upstream timeout", false);
        FakeSectorProvider sina = new FakeSectorProvider("SINA_SECTOR", "SINA", 20);
        SectorMarketEntry refreshed = new SectorMarketEntry();
        refreshed.setCode("SINA:new_blhy");
        refreshed.setName("板块1");
        refreshed.setCategory(SectorCategory.INDUSTRY);
        refreshed.setChangePct(9.9d);
        SectorMarketEntry independent = new SectorMarketEntry();
        independent.setCode("SINA:new_unknown");
        independent.setName("新浪独立行业");
        independent.setCategory(SectorCategory.INDUSTRY);
        independent.setChangePct(8.8d);
        sina.entries = java.util.Arrays.asList(refreshed, independent);

        SectorCatalogGatewayResult result = gateway(tonghuashun, sina)
                .fetchSectorCatalog(SectorCategory.INDUSTRY, true);

        assertEquals(MarketDataQualityStatus.STALE_FALLBACK, result.getQualityStatus());
        assertEquals("PYTHON_TONGHUASHUN_SECTOR", result.getSourceCode());
        assertEquals(5, result.getSnapshot().getEntries().size());
        assertEquals("881001", result.getSnapshot().getEntries().get(0).getCode());
        assertFalse(result.getWarning().contains("BK 编码"));
    }

    private MarketDataGateway gateway(SectorMarketProvider provider) {
        return gateway(new SectorMarketProvider[]{provider});
    }

    private MarketDataGateway gateway(SectorMarketProvider... providers) {
        ProviderRequestGuard guard = new ProviderRequestGuard(clock, millis -> { },
                Duration.ZERO, 0, 3, Duration.ofSeconds(60));
        return new MarketDataGateway(Collections.emptyList(), java.util.Arrays.asList(providers),
                new ProviderRoutePolicy(guard), guard, snapshots, runs, codec,
                new QuoteQualityValidator(clock), new MarketDataSingleFlight(),
                new MarketDataGatewayProperties(30_000, 30, 800), Runnable::run, clock);
    }

    private List<SectorMarketEntry> entries(int count) {
        List<SectorMarketEntry> values = new ArrayList<SectorMarketEntry>();
        for (int index = 1; index <= count; index++) {
            SectorMarketEntry entry = new SectorMarketEntry();
            entry.setCode(String.format("881%03d", index));
            entry.setName("板块" + index);
            entry.setCategory(SectorCategory.INDUSTRY);
            entry.setChangePct((double) index);
            values.add(entry);
        }
        return values;
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

    private final class FakeSectorProvider implements SectorMarketProvider {
        private final String code;
        private final int priority;
        private final AtomicInteger calls = new AtomicInteger();
        private List<SectorMarketEntry> entries = Collections.emptyList();
        private RuntimeException failure;

        private final String family;

        private FakeSectorProvider(String code, int priority) {
            this(code, "EASTMONEY", priority);
        }

        private FakeSectorProvider(String code, String family, int priority) {
            this.code = code;
            this.family = family;
            this.priority = priority;
        }

        public String providerCode() { return code; }
        public String providerFamily() { return family; }
        public Set<MarketDataCapability> capabilities() {
            return Collections.singleton(MarketDataCapability.SECTOR_CATALOG);
        }
        public int priority() { return priority; }
        public int batchLimit() { return 1; }
        public Duration minimumInterval() { return Duration.ZERO; }
        public Duration timeout() { return Duration.ofSeconds(1); }
        public boolean supports(SectorCategory category) { return true; }
        public SectorMarketSnapshot fetch(SectorCategory category) {
            calls.incrementAndGet();
            if (failure != null) throw failure;
            return new SectorMarketSnapshot(category, code, LocalDateTime.now(clock), "fingerprint",
                    entries, Collections.<String>emptyList());
        }
    }
}
