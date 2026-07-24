package com.finscope.service.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.marketdata.MarketDataRefreshRunRepository;
import com.finscope.dao.marketdata.MarketDataSnapshotRepository;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.domain.marketdata.MarketDataSnapshot;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import com.finscope.rpc.quote.QuoteAdapter;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataGatewayQuoteTest {
    @TempDir Path tempDir;
    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 14, 10, 0).toInstant(ZoneOffset.ofHours(8)), ZoneOffset.ofHours(8));
    private final ExecutorService providerExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService callers = Executors.newFixedThreadPool(2);
    private FakeQuoteAdapter primary;
    private FakeQuoteAdapter backup;
    private MarketDataGateway gateway;
    private MarketDataSnapshotRepository snapshots;
    private MarketDataRefreshRunRepository runs;
    private MarketDataSnapshotCodec codec;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("gateway.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createTables(jdbc);
        snapshots = new MarketDataSnapshotRepository(jdbc);
        runs = new MarketDataRefreshRunRepository(jdbc);
        primary = new FakeQuoteAdapter("TENCENT_STOCK", "TENCENT", 10);
        backup = new FakeQuoteAdapter("SINA_STOCK", "SINA", 20);
        codec = new MarketDataSnapshotCodec(new ObjectMapper().findAndRegisterModules());
        gateway = gatewayFor(Arrays.<QuoteAdapter>asList(primary, backup));
    }

    @AfterEach
    void tearDown() {
        providerExecutor.shutdownNow();
        callers.shutdownNow();
    }

    @Test
    void hedgesSlowPrimaryAndMergesOnlyMissingSymbolsFromFallback() {
        primary.delayMillis = 120;
        primary.result = Collections.singletonList(valid("600519", 1500.0));
        backup.result = Collections.singletonList(valid("000001", 10.0));

        QuoteGatewayResult result = gateway.fetchQuotes(
                "STOCK", Arrays.asList("600519", "000001"), true);

        assertEquals(Arrays.asList("600519", "000001"), codes(result.getQuotes()));
        assertEquals(MarketDataQualityStatus.FRESH_FALLBACK, result.getQualityStatus());
        assertEquals("TENCENT_STOCK", result.getQuotes().get(0).getSourceCode());
        assertEquals("SINA_STOCK", result.getQuotes().get(1).getSourceCode());
    }

    @Test
    void switchesToBackupFundValuationProviderWhenPrimaryFails() {
        FakeQuoteAdapter fundPrimary = FakeQuoteAdapter.fund(
                "EASTMONEY_FUND_VALUATION", 10);
        FakeQuoteAdapter fundBackup = FakeQuoteAdapter.fund(
                "EASTMONEY_FUND_VALUATION_BACKUP", 20);
        fundPrimary.failure = new ProviderContractException("CONNECTION_ERROR", "primary down", true);
        fundBackup.result = Collections.singletonList(validFund("021894", 2.6322));

        Quote quote = gatewayFor(Arrays.<QuoteAdapter>asList(fundPrimary, fundBackup))
                .fetchQuotes("FUND", Collections.singletonList("021894"), true)
                .getQuotes().get(0);

        assertEquals(MarketDataQualityStatus.FRESH_FALLBACK, quote.getQualityStatus());
        assertEquals("EASTMONEY_FUND_VALUATION_BACKUP", quote.getSourceCode());
        assertEquals(2.6322, quote.getPrice());
        assertTrue(quote.getWarning().contains("备用数据源"));
    }

    @Test
    void prefersBackupIntradayEstimateWhenPrimaryOnlyHasConfirmedNav() {
        FakeQuoteAdapter fundPrimary = FakeQuoteAdapter.fund(
                "EASTMONEY_FUND_VALUATION", 10);
        FakeQuoteAdapter fundBackup = FakeQuoteAdapter.fund(
                "EASTMONEY_FUND_VALUATION_BACKUP", 20);
        fundPrimary.result = Collections.singletonList(confirmedFund("021894"));
        fundBackup.result = Collections.singletonList(validFund("021894", 2.6322));

        Quote quote = gatewayFor(Arrays.<QuoteAdapter>asList(fundPrimary, fundBackup))
                .fetchQuotes("FUND", Collections.singletonList("021894"), true)
                .getQuotes().get(0);

        assertEquals("EASTMONEY_FUND_VALUATION_BACKUP", quote.getSourceCode());
        assertEquals(2.6322, quote.getPrice());
        assertEquals(MarketDataQualityStatus.FRESH_FALLBACK, quote.getQualityStatus());
    }

    @Test
    void startsConfirmedNavHedgeWhileBothFundValuationProvidersAreStillBlocked() {
        FakeQuoteAdapter fundPrimary = FakeQuoteAdapter.fund(
                "EASTMONEY_FUND_VALUATION", 10);
        FakeQuoteAdapter fundBackup = FakeQuoteAdapter.fund(
                "EASTMONEY_FUND_VALUATION_BACKUP", 20);
        FakeQuoteAdapter confirmedNav = FakeQuoteAdapter.fund(
                "EASTMONEY_FUND_CONFIRMED_NAV", 30);
        fundPrimary.block();
        fundBackup.block();
        confirmedNav.result = Collections.singletonList(confirmedFund("021894"));

        long started = System.nanoTime();
        Quote quote = gatewayFor(Arrays.<QuoteAdapter>asList(
                        fundPrimary, fundBackup, confirmedNav))
                .fetchQuotes("FUND", Collections.singletonList("021894"), true)
                .getQuotes().get(0);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals(MarketDataQualityStatus.FRESH_FALLBACK, quote.getQualityStatus());
        assertEquals("EASTMONEY_FUND_CONFIRMED_NAV", quote.getSourceCode());
        assertEquals(2.6222, quote.getConfirmedNav());
        assertEquals("2026-07-13", quote.getConfirmedNavDate());
        assertNull(quote.getPrice());
        assertTrue(elapsedMillis < 500L);
    }

    @Test
    void timesOutOneProviderAtItsDeclaredLimitAndStartsFallbackImmediately() {
        primary.block();
        primary.timeout = Duration.ofMillis(40);
        backup.result = Collections.singletonList(valid("600519", 1500.0));

        long started = System.nanoTime();
        Quote quote = gatewayFor(Arrays.<QuoteAdapter>asList(primary, backup), 500, 800)
                .fetchQuotes("STOCK", Collections.singletonList("600519"), true)
                .getQuotes().get(0);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals("SINA_STOCK", quote.getSourceCode());
        assertEquals(MarketDataQualityStatus.FRESH_FALLBACK, quote.getQualityStatus());
        assertEquals(1, backup.calls.get());
        assertTrue(elapsedMillis < 350L, "fallback should not wait for the hedge delay");
    }

    @Test
    void providerTimeoutStartsWhenQueuedTaskActuallyBegins() throws Exception {
        QueueingExecutor queue = new QueueingExecutor();
        primary.timeout = Duration.ofMillis(40);
        primary.result = Collections.singletonList(valid("600519", 1500.0));
        CompletableFuture<QuoteGatewayResult> result = CompletableFuture.supplyAsync(
                () -> gatewayFor(Collections.<QuoteAdapter>singletonList(primary),
                        30, 500, queue).fetchQuotes(
                        "STOCK", Collections.singletonList("600519"), true), callers);

        Thread.sleep(100L);
        assertFalse(result.isDone(), "executor queue time must not consume provider timeout");
        queue.runNext();

        assertEquals(MarketDataQualityStatus.FRESH_PRIMARY,
                result.get(1, TimeUnit.SECONDS).getQualityStatus());
    }

    @Test
    void rejectsInvalidFreshPayloadAndReturnsPersistedLastGoodWithWarning() {
        primary.result = Collections.singletonList(invalidHighLow("600519"));
        backup.failure = new ProviderContractException("TIMEOUT", "timeout", true);
        Quote lastGood = valid("600519", 1498.0);
        lastGood.setSourceCode("TENCENT_STOCK");
        lastGood.setAsOf(LocalDateTime.of(2026, 7, 14, 9, 58));
        lastGood.setRetrievedAt(LocalDateTime.of(2026, 7, 14, 9, 58, 5));
        snapshots.upsert(codec.quoteSnapshot(MarketDataCapability.REALTIME_STOCK_QUOTE,
                "STOCK:600519", "TENCENT_STOCK", "TENCENT", lastGood,
                lastGood.getRetrievedAt(), LocalDateTime.of(2026, 7, 14, 9, 58, 5)));

        Quote quote = gateway.fetchQuotes("STOCK", Collections.singletonList("600519"), true)
                .getQuotes().get(0);

        assertEquals(MarketDataQualityStatus.STALE_FALLBACK, quote.getQualityStatus());
        assertEquals(1498.0, quote.getPrice());
        assertTrue(quote.getWarning().contains("正在显示"));
    }

    @Test
    void concurrentRefreshesShareOneProviderFlight() throws Exception {
        primary.block();
        backup.failure = new ProviderContractException("TIMEOUT", "timeout", true);
        CompletableFuture<QuoteGatewayResult> first = CompletableFuture.supplyAsync(
                () -> gateway.fetchQuotes("STOCK", Collections.singletonList("600519"), true), callers);
        assertTrue(primary.started.await(1, TimeUnit.SECONDS));
        CompletableFuture<QuoteGatewayResult> second = CompletableFuture.supplyAsync(
                () -> gateway.fetchQuotes("STOCK", Collections.singletonList("600519"), true), callers);
        primary.release(Collections.singletonList(valid("600519", 1500.0)));

        first.get(1, TimeUnit.SECONDS);
        second.get(1, TimeUnit.SECONDS);
        assertEquals(1, primary.calls.get());
    }

    @Test
    void corruptedSnapshotCannotBecomeAStaleFallback() {
        snapshots.upsert(new MarketDataSnapshot(MarketDataCapability.REALTIME_STOCK_QUOTE,
                "STOCK:600519", "TENCENT_STOCK", "TENCENT",
                LocalDateTime.of(2026, 7, 14, 9, 58), LocalDateTime.of(2026, 7, 14, 9, 58, 5),
                "{broken-json}", "wrong-hash", codec.quoteSchemaVersion(),
                LocalDateTime.of(2026, 7, 14, 9, 58, 5)));
        primary.failure = new ProviderContractException("TIMEOUT", "timeout", true);
        backup.failure = new ProviderContractException("TIMEOUT", "timeout", true);

        QuoteGatewayResult result = gateway.fetchQuotes(
                "STOCK", Collections.singletonList("600519"), true);

        assertEquals(MarketDataQualityStatus.UNAVAILABLE, result.getQualityStatus());
        assertEquals(MarketDataQualityStatus.UNAVAILABLE, result.getQuotes().get(0).getQualityStatus());
    }

    private Quote valid(String code, double price) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        quote.setPrice(price);
        quote.setPreviousClose(price - 1);
        quote.setLow(price - 2);
        quote.setHigh(price + 2);
        quote.setQuoteTime(LocalDateTime.of(2026, 7, 14, 10, 0));
        quote.setValid(true);
        return quote;
    }

    private Quote invalidHighLow(String code) {
        Quote quote = valid(code, 100.0);
        quote.setHigh(90.0);
        quote.setLow(110.0);
        return quote;
    }

    private Quote validFund(String code, double estimate) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        quote.setPrice(estimate);
        quote.setChangePct(0.38);
        quote.setConfirmedNav(2.6222);
        quote.setConfirmedNavDate("2026-07-21");
        quote.setConfirmedNavChangePct(14.6);
        quote.setQuoteTime(LocalDateTime.of(2026, 7, 14, 10, 0));
        quote.setValid(true);
        return quote;
    }

    private Quote confirmedFund(String code) {
        Quote quote = new Quote();
        quote.setInstrumentCode(code);
        quote.setConfirmedNav(2.6222);
        quote.setConfirmedNavDate("2026-07-13");
        quote.setConfirmedNavChangePct(14.6);
        quote.setQuoteTime(LocalDateTime.of(2026, 7, 13, 15, 0));
        quote.setValid(true);
        return quote;
    }

    private MarketDataGateway gatewayFor(List<QuoteAdapter> adapters) {
        return gatewayFor(adapters, 30, 800);
    }

    private MarketDataGateway gatewayFor(List<QuoteAdapter> adapters,
                                         long hedgeDelayMs, long requestBudgetMs) {
        return gatewayFor(adapters, hedgeDelayMs, requestBudgetMs, providerExecutor);
    }

    private MarketDataGateway gatewayFor(List<QuoteAdapter> adapters,
                                         long hedgeDelayMs, long requestBudgetMs,
                                         Executor executor) {
        ProviderRequestGuard guard = new ProviderRequestGuard(clock, millis -> { },
                Duration.ZERO, 0, 3, Duration.ofSeconds(60));
        return new MarketDataGateway(adapters, new ProviderRoutePolicy(guard), guard,
                snapshots, runs, codec, new QuoteQualityValidator(clock),
                new MarketDataSingleFlight(), new MarketDataGatewayProperties(
                        30_000, hedgeDelayMs, requestBudgetMs),
                executor, clock);
    }

    private List<String> codes(List<Quote> quotes) {
        return quotes.stream().map(Quote::getInstrumentCode).collect(Collectors.toList());
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

    private static final class FakeQuoteAdapter implements QuoteAdapter {
        private final String code;
        private final String family;
        private final int priority;
        private final String instrumentType;
        private final MarketDataCapability capability;
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private volatile CountDownLatch release;
        private volatile List<Quote> result = new ArrayList<Quote>();
        private volatile RuntimeException failure;
        private volatile long delayMillis;
        private volatile Duration timeout = Duration.ofMillis(500);

        private FakeQuoteAdapter(String code, String family, int priority) {
            this(code, family, priority, "STOCK", MarketDataCapability.REALTIME_STOCK_QUOTE);
        }

        private FakeQuoteAdapter(String code, String family, int priority,
                                 String instrumentType, MarketDataCapability capability) {
            this.code = code;
            this.family = family;
            this.priority = priority;
            this.instrumentType = instrumentType;
            this.capability = capability;
        }

        static FakeQuoteAdapter fund(String code, int priority) {
            return new FakeQuoteAdapter(code, "EASTMONEY", priority,
                    "FUND", MarketDataCapability.REALTIME_FUND_ESTIMATE);
        }

        void block() { release = new CountDownLatch(1); }
        void release(List<Quote> quotes) { result = quotes; release.countDown(); }

        public String providerCode() { return code; }
        public String providerFamily() { return family; }
        public Set<MarketDataCapability> capabilities() {
            return Collections.singleton(capability);
        }
        public int priority() { return priority; }
        public int batchLimit() { return 100; }
        public Duration minimumInterval() { return Duration.ZERO; }
        public Duration timeout() { return timeout; }
        public boolean supports(String type) { return instrumentType.equals(type); }
        public boolean isTerminalFallback() {
            return "EASTMONEY_FUND_CONFIRMED_NAV".equals(code);
        }
        public List<Quote> fetch(List<String> codes) throws Exception {
            calls.incrementAndGet();
            started.countDown();
            CountDownLatch blocker = release;
            if (blocker != null) blocker.await(1, TimeUnit.SECONDS);
            if (delayMillis > 0) Thread.sleep(delayMillis);
            if (failure != null) throw failure;
            return result;
        }
    }

    private static final class QueueingExecutor implements Executor {
        private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();

        public void execute(Runnable command) { tasks.add(command); }

        void runNext() {
            Runnable task = tasks.poll();
            if (task == null) throw new AssertionError("expected a queued provider task");
            new Thread(task, "queued-provider-test").start();
        }
    }
}
