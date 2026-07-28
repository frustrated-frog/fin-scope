package com.finscope.service.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.marketdata.MarketDataRefreshRunRepository;
import com.finscope.dao.marketdata.MarketDataSnapshotRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataQualityStatus;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.rpc.marketintel.CapitalFlowProvider;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MarketDataGatewayCapitalFlowTest {
    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 14, 10, 0).toInstant(ZoneOffset.ofHours(8)), ZoneOffset.ofHours(8));

    @Test
    void switchesToHealthyBackupWithoutExposingProviderSelection() {
        FakeCapitalProvider primary = new FakeCapitalProvider("PRIMARY_FLOW", "PRIMARY", 10);
        primary.failure = new ProviderContractException("TIMEOUT", "primary timeout", true);
        FakeCapitalProvider backup = new FakeCapitalProvider("BACKUP_FLOW", "BACKUP", 20);
        backup.data = data("BACKUP_FLOW");
        MarketDataGateway gateway = gateway(primary, backup);

        CapitalFlowGatewayResult result = gateway.fetchCapitalFlow(instrument(), LocalDate.of(2026, 7, 14));

        assertEquals(MarketDataQualityStatus.FRESH_FALLBACK, result.getQualityStatus());
        assertEquals("BACKUP_FLOW", result.getSourceCode());
        assertEquals(1, result.getData().getMinutePoints().size());
        assertTrue(result.getWarning().contains("自动切换备用数据源"));
    }

    @Test
    void appliesProviderTimeoutBeforeTryingTheNextCapitalFlowSource() {
        FakeCapitalProvider primary = new FakeCapitalProvider("PRIMARY_FLOW", "PRIMARY", 10);
        primary.delayMillis = 500;
        primary.timeout = Duration.ofMillis(40);
        FakeCapitalProvider backup = new FakeCapitalProvider("BACKUP_FLOW", "BACKUP", 20);
        backup.data = data("BACKUP_FLOW");
        ExecutorService providerExecutor = Executors.newFixedThreadPool(2);
        try {
            long started = System.nanoTime();
            CapitalFlowGatewayResult result = gateway(providerExecutor, primary, backup)
                    .fetchCapitalFlow(instrument(), LocalDate.of(2026, 7, 14));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertEquals("BACKUP_FLOW", result.getSourceCode());
            assertTrue(elapsedMillis < 300L);
        } finally {
            providerExecutor.shutdownNow();
        }
    }

    @Test
    void sharesOneRequestBudgetAcrossAllSequentialProviders() {
        FakeCapitalProvider primary = new FakeCapitalProvider("PRIMARY_FLOW", "PRIMARY", 10);
        primary.delayMillis = 500;
        primary.timeout = Duration.ofSeconds(1);
        FakeCapitalProvider backup = new FakeCapitalProvider("BACKUP_FLOW", "BACKUP", 20);
        backup.delayMillis = 500;
        backup.timeout = Duration.ofSeconds(1);
        backup.data = data("BACKUP_FLOW");
        ExecutorService providerExecutor = Executors.newFixedThreadPool(2);
        try {
            MarketDataGateway gateway = gateway(providerExecutor, 100, primary, backup);
            long started = System.nanoTime();
            CapitalFlowGatewayResult result = gateway
                    .fetchCapitalFlow(instrument(), LocalDate.of(2026, 7, 14));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertEquals(MarketDataQualityStatus.UNAVAILABLE, result.getQualityStatus());
            assertEquals(0, backup.calls.get(), "budget exhaustion must not start another provider");
            assertTrue(elapsedMillis < 300L);
        } finally {
            providerExecutor.shutdownNow();
        }
    }

    @Test
    void supportsALongerCapitalBudgetWithoutSlowingOtherQuoteRequests() {
        MarketDataGatewayProperties properties = new MarketDataGatewayProperties(30_000, 30, 5_000, 20_000);

        assertEquals(5_000, properties.getRequestBudgetMs());
        assertEquals(20_000, properties.getCapitalRequestBudgetMs());
    }

    @Test
    void acceptsIndependentDailyFlowAsDegradedFallbackWhenMinuteFlowIsUnavailable() {
        FakeCapitalProvider primary = new FakeCapitalProvider("PRIMARY_FLOW", "EASTMONEY", 10);
        primary.failure = new ProviderContractException("CONNECTION_ERROR", "push2 unavailable", false);
        FakeCapitalProvider backup = new FakeCapitalProvider("SINA_DAILY_FLOW", "SINA", 20);
        backup.data = dailyData("SINA_DAILY_FLOW");
        MarketDataGateway gateway = gateway(primary, backup);

        CapitalFlowGatewayResult result = gateway.fetchCapitalFlow(
                instrument(), LocalDate.of(2026, 7, 14));

        assertEquals(MarketDataQualityStatus.FRESH_FALLBACK, result.getQualityStatus());
        assertEquals("SINA_DAILY_FLOW", result.getSourceCode());
        assertEquals(0, result.getData().getMinutePoints().size());
        assertEquals(1, result.getData().getDailyPoints().size());
        assertTrue(result.getWarning().contains("分钟资金流暂不可用"));
    }

    private MarketDataGateway gateway(CapitalFlowProvider... providers) {
        return gateway(Runnable::run, providers);
    }

    private MarketDataGateway gateway(Executor executor, CapitalFlowProvider... providers) {
        return gateway(executor, 800, providers);
    }

    private MarketDataGateway gateway(Executor executor, long requestBudgetMs,
                                      CapitalFlowProvider... providers) {
        ProviderRequestGuard guard = new ProviderRequestGuard(clock, millis -> { },
                Duration.ZERO, 0, 3, Duration.ofSeconds(60));
        return new MarketDataGateway(Collections.emptyList(), Collections.emptyList(), Arrays.asList(providers),
                new ProviderRoutePolicy(guard), guard, mock(MarketDataSnapshotRepository.class),
                mock(MarketDataRefreshRunRepository.class),
                new MarketDataSnapshotCodec(new ObjectMapper().findAndRegisterModules()),
                new QuoteQualityValidator(clock), new MarketDataSingleFlight(),
                new MarketDataGatewayProperties(30_000, 30, requestBudgetMs), executor, clock);
    }

    private Instrument instrument() {
        Instrument value = new Instrument();
        value.setId(7L);
        value.setType("STOCK");
        value.setMarket("SH");
        value.setCode("600519");
        return value;
    }

    private CapitalFlowData data(String providerCode) {
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setInstrumentId(7L);
        point.setProviderCode(providerCode);
        point.setGranularity("MINUTE_1");
        point.setDataDate(LocalDate.of(2026, 7, 14));
        point.setObservedAt(LocalDateTime.of(2026, 7, 14, 10, 0));
        point.setMainNetInflow(BigDecimal.ONE);
        point.setQualityStatus("COMPLETE");
        return new CapitalFlowData(Collections.singletonList(point), Collections.emptyList(),
                BigDecimal.ONE, BigDecimal.ONE, Collections.emptyList(), providerCode);
    }

    private CapitalFlowData dailyData(String providerCode) {
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setInstrumentId(7L);
        point.setProviderCode(providerCode);
        point.setGranularity("DAY_1");
        point.setDataDate(LocalDate.of(2026, 7, 14));
        point.setObservedAt(LocalDateTime.of(2026, 7, 14, 15, 0));
        point.setMainNetInflow(BigDecimal.ONE);
        point.setQualityStatus("PARTIAL");
        return new CapitalFlowData(Collections.emptyList(), Collections.singletonList(point),
                null, null, Collections.singletonList("SINA_DAILY_FLOW_ONLY"), providerCode);
    }

    private static final class FakeCapitalProvider implements CapitalFlowProvider {
        private final String code;
        private final String family;
        private final int priority;
        private CapitalFlowData data;
        private RuntimeException failure;
        private final AtomicInteger calls = new AtomicInteger();
        private long delayMillis;
        private Duration timeout = Duration.ofSeconds(1);

        private FakeCapitalProvider(String code, String family, int priority) {
            this.code = code;
            this.family = family;
            this.priority = priority;
        }

        public String providerCode() { return code; }
        public String providerFamily() { return family; }
        public Set<MarketDataCapability> capabilities() {
            return Collections.singleton(MarketDataCapability.CAPITAL_FLOW_5M);
        }
        public int priority() { return priority; }
        public int batchLimit() { return 1; }
        public Duration minimumInterval() { return Duration.ZERO; }
        public Duration timeout() { return timeout; }
        public boolean supports(Instrument instrument) { return true; }
        public CapitalFlowData fetch(Instrument instrument, LocalDate asOfDate) {
            calls.incrementAndGet();
            if (failure != null) throw failure;
            if (delayMillis > 0L) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new ProviderContractException("INTERRUPTED", "interrupted", false, error);
                }
            }
            return data;
        }
    }
}
