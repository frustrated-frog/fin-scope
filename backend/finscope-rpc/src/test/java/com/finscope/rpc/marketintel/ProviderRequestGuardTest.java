package com.finscope.rpc.marketintel;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderRequestGuardTest {
    @Test
    void retriesOneRetryableFailure() {
        AtomicInteger calls = new AtomicInteger();
        ProviderRequestGuard guard = new ProviderRequestGuard(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                millis -> { }, Duration.ZERO, 1, 3, Duration.ofSeconds(60));
        String result = guard.execute("EASTMONEY", () -> {
            if (calls.getAndIncrement() == 0) throw new ProviderContractException("HTTP_503", "busy", true);
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    @Test
    void opensCircuitAfterThreeRetryableOperationsFail() {
        ProviderRequestGuard guard = new ProviderRequestGuard(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                millis -> { }, Duration.ZERO, 0, 3, Duration.ofSeconds(60));
        for (int i = 0; i < 3; i++) {
            assertThrows(ProviderContractException.class,
                    () -> guard.execute("EASTMONEY", () -> { throw new ProviderContractException("HTTP_503", "busy", true); }));
        }
        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> guard.execute("EASTMONEY", () -> "never"));
        assertEquals("CIRCUIT_OPEN", error.getErrorType());
    }
}
