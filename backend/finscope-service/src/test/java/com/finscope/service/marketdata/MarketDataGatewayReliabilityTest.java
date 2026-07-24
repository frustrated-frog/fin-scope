package com.finscope.service.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.marketdata.MarketDataRefreshRunRepository;
import com.finscope.dao.marketdata.MarketDataSnapshotRepository;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.quote.QuoteAdapter;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MarketDataGatewayReliabilityTest {
    @TempDir Path tempDir;
    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 14, 10, 0).toInstant(ZoneOffset.ofHours(8)),
            ZoneOffset.ofHours(8));
    private ScenarioQuoteAdapter primary;
    private ScenarioQuoteAdapter backup;
    private MarketDataGateway gateway;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("reliability.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE market_data_snapshot(capability TEXT NOT NULL,scope_key TEXT NOT NULL,"
                + "provider_code TEXT NOT NULL,provider_family TEXT NOT NULL,as_of TEXT,retrieved_at TEXT NOT NULL,"
                + "payload_json TEXT NOT NULL,payload_hash TEXT NOT NULL,schema_version INTEGER NOT NULL,"
                + "updated_at TEXT NOT NULL,PRIMARY KEY(capability,scope_key))");
        jdbc.execute("CREATE TABLE market_data_refresh_run(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "capability TEXT NOT NULL,scope_summary TEXT NOT NULL,trigger_type TEXT NOT NULL,status TEXT NOT NULL,"
                + "started_at TEXT NOT NULL,finished_at TEXT,requested_count INTEGER NOT NULL DEFAULT 0,"
                + "fresh_count INTEGER NOT NULL DEFAULT 0,stale_count INTEGER NOT NULL DEFAULT 0,"
                + "failed_count INTEGER NOT NULL DEFAULT 0,selected_sources TEXT,warning_message TEXT)");
        ProviderRequestGuard guard = new ProviderRequestGuard(clock, millis -> { },
                Duration.ZERO, 0, 10_000, Duration.ofSeconds(1));
        primary = new ScenarioQuoteAdapter("TENCENT_STOCK", "TENCENT", 10);
        backup = new ScenarioQuoteAdapter("SINA_STOCK", "SINA", 20);
        gateway = new MarketDataGateway(Arrays.<QuoteAdapter>asList(primary, backup),
                new ProviderRoutePolicy(guard), guard,
                new MarketDataSnapshotRepository(jdbc), new MarketDataRefreshRunRepository(jdbc),
                new MarketDataSnapshotCodec(new ObjectMapper().findAndRegisterModules()),
                new QuoteQualityValidator(clock), new MarketDataSingleFlight(),
                new MarketDataGatewayProperties(0, 0, 100), Runnable::run, clock);
    }

    @Test
    void everyRefreshReturnsUsefulDataAfterFirstSuccessAcrossOneThousandFailureCombinations() {
        primary.scenario = 0;
        backup.scenario = 1;
        gateway.fetchQuotes("STOCK", Collections.singletonList("600519"), true);

        for (int i = 0; i < 1_000; i++) {
            primary.scenario = i % 4;
            backup.scenario = (i * 31 + 1) % 4;

            QuoteGatewayResult result = gateway.fetchQuotes(
                    "STOCK", Collections.singletonList("600519"), true);

            assertFalse(result.getQuotes().isEmpty(), "iteration=" + i);
            assertNotEquals(MarketDataQualityStatus.UNAVAILABLE, result.getQualityStatus(), "iteration=" + i);
            assertTrue(result.getQuotes().stream().allMatch(quote -> quote.getQualityStatus() != null),
                    "iteration=" + i);
            assertTrue(result.getQuotes().stream().allMatch(Quote::isValid), "iteration=" + i);
        }
    }

    @Test
    void maintenanceDeletesOldAuditsAtThirtyDays() {
        MarketDataRefreshRunRepository runs = mock(MarketDataRefreshRunRepository.class);
        MarketDataMaintenanceService maintenance = new MarketDataMaintenanceService(runs, clock);

        maintenance.cleanup();

        verify(runs).deleteFinishedBefore(LocalDateTime.of(2026, 6, 14, 10, 0));
    }

    private static final class ScenarioQuoteAdapter implements QuoteAdapter {
        private final String code;
        private final String family;
        private final int priority;
        private volatile int scenario;

        private ScenarioQuoteAdapter(String code, String family, int priority) {
            this.code = code;
            this.family = family;
            this.priority = priority;
        }

        public String providerCode() { return code; }
        public String providerFamily() { return family; }
        public Set<MarketDataCapability> capabilities() {
            return Collections.singleton(MarketDataCapability.REALTIME_STOCK_QUOTE);
        }
        public int priority() { return priority; }
        public int batchLimit() { return 100; }
        public Duration minimumInterval() { return Duration.ZERO; }
        public Duration timeout() { return Duration.ofMillis(50); }
        public boolean supports(String instrumentType) { return "STOCK".equals(instrumentType); }

        public List<Quote> fetch(List<String> codes) {
            if (scenario == 1) throw new ProviderContractException("TIMEOUT", "模拟超时", true);
            if (scenario == 3) return Collections.emptyList();
            Quote quote = quote(codes.get(0));
            if (scenario == 2) {
                quote.setHigh(90.0);
                quote.setLow(110.0);
            }
            return Collections.singletonList(quote);
        }

        public ProviderResult<List<Quote>> fetchResult(List<String> codes) throws Exception {
            List<Quote> values = fetch(codes);
            return ProviderResult.of(values, LocalDateTime.of(2026, 7, 14, 10, 0),
                    ProviderResult.hashOf(values), Collections.<String>emptyList());
        }

        private Quote quote(String instrumentCode) {
            Quote quote = new Quote();
            quote.setInstrumentCode(instrumentCode);
            quote.setPrice(100.0);
            quote.setPreviousClose(99.0);
            quote.setLow(98.0);
            quote.setHigh(102.0);
            quote.setQuoteTime(LocalDateTime.of(2026, 7, 14, 10, 0));
            quote.setValid(true);
            return quote;
        }
    }
}
