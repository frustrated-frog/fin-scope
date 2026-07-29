package com.finscope.rpc.marketintel;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.rpc.marketdata.MarketDataProvider;
import com.finscope.rpc.provider.ExternalDataProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provider 请求隔离器：按具体端点限频和熔断，并在同厂商的多个能力同时失败时开启家族熔断。
 * 限频只在状态锁内预订时间槽，实际等待发生在锁外，其他 Provider 不会被阻塞。
 */
@Component
public class ProviderRequestGuard {
    private static final double EWMA_ALPHA = 0.25d;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Duration legacyMinimumInterval;
    private final int maxRetries;
    private final int failureThreshold;
    private final Duration openDuration;
    private final ConcurrentMap<EndpointKey, EndpointState> endpoints =
            new ConcurrentHashMap<EndpointKey, EndpointState>();
    private final ConcurrentMap<FamilyKey, FamilyState> families =
            new ConcurrentHashMap<FamilyKey, FamilyState>();
    private final ConcurrentMap<String, FamilyThrottleState> familyThrottles =
            new ConcurrentHashMap<String, FamilyThrottleState>();
    public ProviderRequestGuard() {
        this(Clock.systemUTC(), Thread::sleep, Duration.ofSeconds(1), 1, 3, Duration.ofSeconds(60));
    }
    public ProviderRequestGuard(Clock clock, Sleeper sleeper, Duration legacyMinimumInterval,
                                int maxRetries, int failureThreshold, Duration openDuration) {
        this.clock = clock;
        this.sleeper = sleeper;
        this.legacyMinimumInterval = legacyMinimumInterval;
        this.maxRetries = maxRetries;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    /**
     * 兼容底层 HTTP 客户端；新网关应使用携带完整元数据的重载。
     */
    public <T> T execute(String providerCode, Operation<T> operation) {
        return execute(MarketDataCapability.REALTIME_STOCK_QUOTE, providerCode, operation);
    }

    /**
     * 兼容没有完整 Provider 元数据的调用方，同时显式隔离能力故障域。
     */
    public <T> T execute(MarketDataCapability capability, String providerCode,
                         Operation<T> operation) {
        return execute(new LegacyProvider(providerCode, legacyMinimumInterval),
                capability.name(), operation);
    }

    public <T> T execute(MarketDataProvider provider, MarketDataCapability capability,
                         Operation<T> operation) {
        if (!provider.supports(capability)) {
            throw new ProviderContractException("UNSUPPORTED_CAPABILITY",
                    provider.providerCode() + " does not support " + capability, false);
        }
        return execute(provider, capability.name(), operation);
    }

    public <T> T execute(ExternalDataProvider provider, String capabilityCode,
                         Operation<T> operation) {
        requireCapability(capabilityCode);
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(provider.timeout())) {
            return executeWithinDeadline(provider, capabilityCode, operation);
        }
    }

    private <T> T executeWithinDeadline(ExternalDataProvider provider,
                                        String capability,
                                        Operation<T> operation) {
        EndpointKey key = new EndpointKey(provider.providerCode(), capability);
        EndpointState endpoint = endpoints.computeIfAbsent(key, ignored -> new EndpointState());
        String reliabilityFamily = reliabilityFamily(provider);
        FamilyKey familyKey = new FamilyKey(capability, reliabilityFamily);
        FamilyState family = families.computeIfAbsent(familyKey, ignored -> new FamilyState());
        acquirePermission(provider, reliabilityFamily, endpoint, family);

        for (int attempt = 0; ; attempt++) {
            sleep(reserveDelay(endpoint, provider.minimumInterval()));
            sleep(reserveFamilyDelay(provider));
            long started = System.nanoTime();
            try {
                T value = operation.run();
                recordSuccess(provider.providerCode(), reliabilityFamily, capability,
                        elapsedMillis(started));
                return value;
            } catch (ProviderContractException error) {
                if (error.isRetryable() && attempt < maxRetries) {
                    continue;
                }
                recordFailure(key, reliabilityFamily, capability,
                        elapsedMillis(started), error.isRetryable());
                throw error;
            } catch (Exception error) {
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    recordFailure(key, reliabilityFamily, capability,
                            elapsedMillis(started), false);
                    throw new ProviderContractException("INTERRUPTED",
                            "provider call interrupted", false, error);
                }
                if (attempt < maxRetries) {
                    continue;
                }
                recordFailure(key, reliabilityFamily, capability,
                        elapsedMillis(started), true);
                throw new ProviderContractException("CONNECTION_ERROR", error.getMessage(), true, error);
            }
        }
    }

    public boolean isAvailable(MarketDataProvider provider, MarketDataCapability capability) {
        if (!provider.supports(capability)) {
            return false;
        }
        return isAvailable(provider, capability.name());
    }

    public boolean isAvailable(ExternalDataProvider provider, String capabilityCode) {
        requireCapability(capabilityCode);
        EndpointState endpoint = endpoints.get(new EndpointKey(provider.providerCode(), capabilityCode));
        FamilyState family = families.get(new FamilyKey(capabilityCode, reliabilityFamily(provider)));
        Instant now = clock.instant();
        return (endpoint == null || endpoint.isAvailable(now))
                && (family == null || family.isAvailable(now));
    }

    /** 查询指定可靠性故障域是否可用；通常传入 provider.reliabilityFamily()。 */
    public boolean isFamilyAvailable(String reliabilityFamily) {
        return isFamilyAvailable(MarketDataCapability.REALTIME_STOCK_QUOTE, reliabilityFamily);
    }

    public boolean isFamilyAvailable(MarketDataCapability capability, String reliabilityFamily) {
        return isFamilyAvailable(capability.name(), reliabilityFamily);
    }

    public boolean isFamilyAvailable(String capabilityCode, String reliabilityFamily) {
        requireCapability(capabilityCode);
        FamilyState family = families.get(new FamilyKey(capabilityCode, reliabilityFamily));
        return family == null || family.isAvailable(clock.instant());
    }

    /** 记录端点及其可靠性故障域成功；第二个参数通常传入 provider.reliabilityFamily()。 */
    public void recordSuccess(String providerCode, String reliabilityFamily,
                              MarketDataCapability capability, long latencyMillis) {
        recordSuccess(providerCode, reliabilityFamily, capability.name(), latencyMillis);
    }

    public void recordSuccess(String providerCode, String reliabilityFamily,
                              String capabilityCode, long latencyMillis) {
        requireCapability(capabilityCode);
        EndpointState endpoint = endpoints.computeIfAbsent(
                new EndpointKey(providerCode, capabilityCode), ignored -> new EndpointState());
        endpoint.recordSuccess(Math.max(0L, latencyMillis));
        families.computeIfAbsent(new FamilyKey(capabilityCode, reliabilityFamily),
                ignored -> new FamilyState()).recordSuccess();
    }

    public double successRateEwma(MarketDataProvider provider, MarketDataCapability capability) {
        return successRateEwma(provider, capability.name());
    }

    public double successRateEwma(ExternalDataProvider provider, String capabilityCode) {
        EndpointState state = endpoints.get(new EndpointKey(provider.providerCode(), capabilityCode));
        return state == null ? 1.0d : state.successRate();
    }

    public double latencyEwmaMillis(MarketDataProvider provider, MarketDataCapability capability) {
        return latencyEwmaMillis(provider, capability.name());
    }

    public double latencyEwmaMillis(ExternalDataProvider provider, String capabilityCode) {
        EndpointState state = endpoints.get(new EndpointKey(provider.providerCode(), capabilityCode));
        return state == null ? 0.0d : state.latencyMillis();
    }

    public double failurePenalty(MarketDataProvider provider, MarketDataCapability capability) {
        return failurePenalty(provider, capability.name());
    }

    public double failurePenalty(ExternalDataProvider provider, String capabilityCode) {
        EndpointState state = endpoints.get(new EndpointKey(provider.providerCode(), capabilityCode));
        if (!isAvailable(provider, capabilityCode)) {
            return 10_000.0d;
        }
        return state == null ? 0.0d : state.consecutiveFailures() * 25.0d;
    }

    private void acquirePermission(ExternalDataProvider provider, String reliabilityFamily,
                                   EndpointState endpoint, FamilyState family) {
        Instant now = clock.instant();
        if (!endpoint.tryAcquire(now)) {
            throw circuitOpen(provider.providerCode());
        }
        if (!family.tryAcquire(now)) {
            endpoint.releaseProbe();
            throw circuitOpen(reliabilityFamily);
        }
    }

    private ProviderContractException circuitOpen(String target) {
        return new ProviderContractException("CIRCUIT_OPEN", "provider circuit is open: " + target, false);
    }

    private String reliabilityFamily(ExternalDataProvider provider) {
        String value = provider.reliabilityFamily();
        if (value == null || value.trim().isEmpty()) {
            value = provider.providerFamily();
        }
        if (value == null || value.trim().isEmpty()) {
            value = provider.providerCode();
        }
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("provider reliability family must not be blank");
        }
        return value.trim();
    }

    private long reserveDelay(EndpointState state, Duration minimumInterval) {
        return state.reserve(clock.instant(), minimumInterval == null ? Duration.ZERO : minimumInterval);
    }

    private long reserveFamilyDelay(ExternalDataProvider provider) {
        Duration interval = provider.minimumInterval() == null
                ? Duration.ZERO : provider.minimumInterval();
        if ("EASTMONEY".equalsIgnoreCase(provider.providerFamily())
                && interval.compareTo(Duration.ofSeconds(1)) < 0) {
            interval = Duration.ofSeconds(1);
        }
        return familyThrottles.computeIfAbsent(provider.providerFamily(),
                        ignored -> new FamilyThrottleState())
                .reserve(clock.instant(), interval);
    }

    private void sleep(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProviderContractException("INTERRUPTED", "provider call interrupted", false, error);
        }
    }

    private void recordFailure(EndpointKey key, String providerFamily,
                               String capability, long latencyMillis,
                               boolean retryable) {
        Instant now = clock.instant();
        endpoints.computeIfAbsent(key, ignored -> new EndpointState())
                .recordFailure(now, Math.max(0L, latencyMillis), retryable,
                        failureThreshold, openDuration);
        families.computeIfAbsent(new FamilyKey(capability, providerFamily),
                        ignored -> new FamilyState())
                .recordFailure(now, retryable, failureThreshold, openDuration);
    }

    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public interface Operation<T> {
        T run() throws Exception;
    }

    private static final class EndpointKey {
        private final String providerCode;
        private final String capability;

        private EndpointKey(String providerCode, String capability) {
            this.providerCode = providerCode;
            this.capability = capability;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof EndpointKey)) return false;
            EndpointKey other = (EndpointKey) value;
            return providerCode.equals(other.providerCode) && capability.equals(other.capability);
        }

        @Override
        public int hashCode() {
            return 31 * providerCode.hashCode() + capability.hashCode();
        }
    }

    private static final class FamilyKey {
        private final String capability;
        private final String providerFamily;

        private FamilyKey(String capability, String providerFamily) {
            this.capability = capability;
            this.providerFamily = providerFamily;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof FamilyKey)) return false;
            FamilyKey other = (FamilyKey) value;
            return capability.equals(other.capability) && providerFamily.equals(other.providerFamily);
        }

        @Override
        public int hashCode() {
            return 31 * capability.hashCode() + providerFamily.hashCode();
        }
    }

    private static final class EndpointState {
        private Instant nextAllowedAt;
        private Instant openUntil;
        private boolean probeInFlight;
        private int consecutiveFailures;
        private boolean metricsInitialized;
        private double successRate = 1.0d;
        private double latencyMillis;

        synchronized boolean tryAcquire(Instant now) {
            if (openUntil == null) return true;
            if (now.isBefore(openUntil) || probeInFlight) return false;
            probeInFlight = true;
            return true;
        }

        synchronized boolean isAvailable(Instant now) {
            return openUntil == null || (!now.isBefore(openUntil) && !probeInFlight);
        }

        synchronized void releaseProbe() {
            probeInFlight = false;
        }

        synchronized long reserve(Instant now, Duration interval) {
            Instant slot = nextAllowedAt == null || !nextAllowedAt.isAfter(now) ? now : nextAllowedAt;
            nextAllowedAt = slot.plus(interval.isNegative() ? Duration.ZERO : interval);
            return Math.max(0L, Duration.between(now, slot).toMillis());
        }

        synchronized void recordSuccess(long latency) {
            updateMetrics(1.0d, latency);
            consecutiveFailures = 0;
            openUntil = null;
            probeInFlight = false;
        }

        synchronized void recordFailure(Instant now, long latency, boolean retryable,
                                        int threshold, Duration duration) {
            updateMetrics(0.0d, latency);
            probeInFlight = false;
            if (!retryable) {
                consecutiveFailures = 0;
                return;
            }
            consecutiveFailures++;
            if (consecutiveFailures >= threshold) {
                openUntil = now.plus(duration);
            }
        }

        private void updateMetrics(double success, long latency) {
            if (!metricsInitialized) {
                successRate = success;
                latencyMillis = latency;
                metricsInitialized = true;
                return;
            }
            successRate = EWMA_ALPHA * success + (1.0d - EWMA_ALPHA) * successRate;
            latencyMillis = EWMA_ALPHA * latency + (1.0d - EWMA_ALPHA) * latencyMillis;
        }

        synchronized double successRate() {
            return successRate;
        }

        synchronized double latencyMillis() {
            return latencyMillis;
        }

        synchronized int consecutiveFailures() {
            return consecutiveFailures;
        }
    }

    private static final class FamilyState {
        private Instant openUntil;
        private boolean probeInFlight;
        private int consecutiveFailures;

        synchronized boolean tryAcquire(Instant now) {
            if (openUntil == null) return true;
            if (now.isBefore(openUntil) || probeInFlight) return false;
            probeInFlight = true;
            return true;
        }

        synchronized boolean isAvailable(Instant now) {
            return openUntil == null || (!now.isBefore(openUntil) && !probeInFlight);
        }

        synchronized void recordSuccess() {
            openUntil = null;
            probeInFlight = false;
            consecutiveFailures = 0;
        }

        synchronized void recordFailure(Instant now, boolean retryable,
                                        int threshold, Duration duration) {
            probeInFlight = false;
            if (!retryable) return;
            consecutiveFailures++;
            if (consecutiveFailures >= threshold) {
                openUntil = now.plus(duration);
            }
        }
    }

    private static final class FamilyThrottleState {
        private Instant nextAllowedAt;

        synchronized long reserve(Instant now, Duration interval) {
            Instant slot = nextAllowedAt == null || !nextAllowedAt.isAfter(now)
                    ? now : nextAllowedAt;
            nextAllowedAt = slot.plus(interval.isNegative() ? Duration.ZERO : interval);
            return Math.max(0L, Duration.between(now, slot).toMillis());
        }
    }

    private static final class LegacyProvider implements ExternalDataProvider {
        private final String code;
        private final Duration minimumInterval;

        private LegacyProvider(String code, Duration minimumInterval) {
            this.code = code;
            this.minimumInterval = minimumInterval;
        }

        public String providerCode() {
            return code;
        }

        public String providerFamily() {
            return code;
        }

        public int priority() {
            return 100;
        }

        public int batchLimit() {
            return 1;
        }

        public Duration minimumInterval() {
            return minimumInterval;
        }

        public Duration timeout() {
            return Duration.ofSeconds(10);
        }
    }

    private void requireCapability(String capabilityCode) {
        if (capabilityCode == null || capabilityCode.trim().isEmpty()) {
            throw new IllegalArgumentException("provider capability must not be blank");
        }
    }
}
