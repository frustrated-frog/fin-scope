package com.finscope.service.instrument;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.quote.SectorMarketProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectorMarketServiceTest {
    private final List<ExecutorService> executors = new ArrayList<ExecutorService>();

    @AfterEach
    void tearDown() {
        for (ExecutorService executor : executors) executor.shutdownNow();
    }

    @Test
    void ranksOneSnapshotDeterministicallyWithoutOverlap() {
        MutableProvider provider = new MutableProvider(entries(
                entry("BK0001", "甲", 4.0, 100.0),
                entry("BK0002", "乙", -3.0, 90.0),
                entry("BK0003", "丙", 4.0, 120.0),
                entry("BK0004", "丁", -2.0, 80.0)));
        SectorMarketService service = service(provider, new MutableClock(Instant.parse("2026-07-14T02:00:00Z")));

        SectorMarketOverview result = service.overview(SectorCategory.INDUSTRY, 2, false);

        assertEquals(Arrays.asList("BK0003", "BK0001"), codes(result.getLeaders()));
        assertEquals(Arrays.asList("BK0002", "BK0004"), codes(result.getLaggards()));
        assertEquals(SectorMarketQualityStatus.FRESH, result.getQualityStatus());
    }

    @Test
    void reusesFreshSnapshotUntilForced() {
        MutableProvider provider = new MutableProvider(entries(entry("BK0001", "甲", 1.0, 100.0)));
        SectorMarketService service = service(provider, new MutableClock(Instant.parse("2026-07-14T02:00:00Z")));

        service.overview(SectorCategory.INDUSTRY, 5, false);
        service.overview(SectorCategory.INDUSTRY, 5, false);
        service.overview(SectorCategory.INDUSTRY, 5, true);

        assertEquals(2, provider.callCount.get());
    }

    @Test
    void returnsStaleSnapshotWhenRefreshFailsInsideStaleWindow() {
        MutableProvider provider = new MutableProvider(entries(entry("BK0001", "甲", 1.0, 100.0)));
        MutableClock clock = new MutableClock(Instant.parse("2026-07-14T02:00:00Z"));
        SectorMarketService service = service(provider, clock);
        service.overview(SectorCategory.INDUSTRY, 5, false);
        clock.advance(Duration.ofMinutes(1));
        provider.failure = new ProviderContractException("HTTP_503", "down", true);

        SectorMarketOverview result = service.overview(SectorCategory.INDUSTRY, 5, true);

        assertEquals(SectorMarketQualityStatus.STALE, result.getQualityStatus());
        assertTrue(result.getWarning().contains("down"));
        assertEquals("BK0001", result.getLeaders().get(0).getCode());
    }

    @Test
    void becomesUnavailableAfterStaleWindowExpires() {
        MutableProvider provider = new MutableProvider(entries(entry("BK0001", "甲", 1.0, 100.0)));
        MutableClock clock = new MutableClock(Instant.parse("2026-07-14T02:00:00Z"));
        SectorMarketService service = service(provider, clock);
        service.overview(SectorCategory.INDUSTRY, 5, false);
        clock.advance(Duration.ofMinutes(16));
        provider.failure = new ProviderContractException("HTTP_503", "down", true);

        SectorMarketOverview result = service.overview(SectorCategory.INDUSTRY, 5, true);

        assertEquals(SectorMarketQualityStatus.UNAVAILABLE, result.getQualityStatus());
        assertTrue(result.getLeaders().isEmpty());
    }

    @Test
    void searchesExactCodeAndNameMatchesInPriorityOrder() {
        MutableProvider provider = new MutableProvider(entries(
                entry("BK1036", "半导体", 2.0, 100.0),
                entry("BK2000", "半导体设备", 3.0, 80.0),
                entry("BK3000", "先进半导体材料", 4.0, 70.0)));
        SectorMarketService service = service(provider, new MutableClock(Instant.parse("2026-07-14T02:00:00Z")));

        SectorMarketSearchResult byCode = service.search("bk1036", SectorCategory.INDUSTRY, 10);
        SectorMarketSearchResult byName = service.search("半导体", SectorCategory.INDUSTRY, 10);

        assertEquals(Collections.singletonList("BK1036"), codes(byCode.getItems()));
        assertEquals(Arrays.asList("BK1036", "BK2000", "BK3000"), codes(byName.getItems()));
    }

    @Test
    void mergesConcurrentRefreshesIntoOneProviderCall() throws Exception {
        BlockingProvider provider = new BlockingProvider(entries(entry("BK0001", "甲", 1.0, 100.0)));
        MutableClock clock = new MutableClock(Instant.parse("2026-07-14T02:00:00Z"));
        ExecutorService refreshExecutor = executor(Executors.newFixedThreadPool(2));
        SectorMarketService service = service(provider, clock, refreshExecutor);
        ExecutorService callers = executor(Executors.newFixedThreadPool(2));

        CompletableFuture<SectorMarketOverview> first = CompletableFuture.supplyAsync(
                () -> service.overview(SectorCategory.INDUSTRY, 5, true), callers);
        assertTrue(provider.started.await(2, TimeUnit.SECONDS));
        CompletableFuture<SectorMarketOverview> second = CompletableFuture.supplyAsync(
                () -> service.overview(SectorCategory.INDUSTRY, 5, true), callers);
        provider.release.countDown();

        first.get(2, TimeUnit.SECONDS);
        second.get(2, TimeUnit.SECONDS);
        assertEquals(1, provider.callCount.get());
    }

    @Test
    void reportsUnavailableWhenRefreshExecutorRejectsTask() {
        MutableProvider provider = new MutableProvider(entries(entry("BK0001", "甲", 1.0, 100.0)));
        SectorMarketService service = service(provider, new MutableClock(Instant.parse("2026-07-14T02:00:00Z")),
                command -> { throw new java.util.concurrent.RejectedExecutionException("executor saturated"); });

        SectorMarketOverview result = service.overview(SectorCategory.INDUSTRY, 5, false);

        assertEquals(SectorMarketQualityStatus.UNAVAILABLE, result.getQualityStatus());
        assertTrue(result.getWarning().contains("executor saturated"));
        assertEquals(0, provider.callCount.get());
    }

    private SectorMarketService service(SectorMarketProvider provider, Clock clock) {
        return service(provider, clock, Runnable::run);
    }

    private SectorMarketService service(SectorMarketProvider provider, Clock clock, java.util.concurrent.Executor executor) {
        SectorMarketService service = new SectorMarketService(clock);
        ReflectionTestUtils.setField(service, "providers", Collections.singletonList(provider));
        ReflectionTestUtils.setField(service, "executor", executor);
        return service;
    }

    private ExecutorService executor(ExecutorService executor) {
        executors.add(executor);
        return executor;
    }

    private static List<String> codes(List<SectorMarketEntry> values) {
        return values.stream().map(SectorMarketEntry::getCode).collect(Collectors.toList());
    }

    private static List<SectorMarketEntry> entries(SectorMarketEntry... values) {
        return Arrays.asList(values);
    }

    private static SectorMarketEntry entry(String code, String name, double changePct, double turnover) {
        SectorMarketEntry value = new SectorMarketEntry();
        value.setCode(code);
        value.setName(name);
        value.setCategory(SectorCategory.INDUSTRY);
        value.setChangePct(changePct);
        value.setTurnover(turnover);
        return value;
    }

    private static class MutableProvider implements SectorMarketProvider {
        protected final AtomicInteger callCount = new AtomicInteger();
        private final List<SectorMarketEntry> entries;
        private RuntimeException failure;

        private MutableProvider(List<SectorMarketEntry> entries) { this.entries = entries; }
        @Override public String providerCode() { return "TEST"; }
        @Override public boolean supports(SectorCategory category) { return true; }
        @Override public SectorMarketSnapshot fetch(SectorCategory category) {
            callCount.incrementAndGet();
            if (failure != null) throw failure;
            return new SectorMarketSnapshot(category, providerCode(),
                    java.time.LocalDateTime.ofInstant(Instant.parse("2026-07-14T02:00:00Z"), ZoneOffset.UTC),
                    "hash", entries, Collections.<String>emptyList());
        }
    }

    private static class BlockingProvider extends MutableProvider {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingProvider(List<SectorMarketEntry> entries) { super(entries); }
        @Override public SectorMarketSnapshot fetch(SectorCategory category) {
            started.countDown();
            try { release.await(2, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            return super.fetch(category);
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
