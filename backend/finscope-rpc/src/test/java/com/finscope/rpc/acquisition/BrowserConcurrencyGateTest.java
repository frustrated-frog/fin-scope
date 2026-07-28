package com.finscope.rpc.acquisition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrowserConcurrencyGateTest {
    @Test
    void rejectsWorkThatCannotEnterBeforeItsDeadline() {
        BrowserConcurrencyGate gate = new BrowserConcurrencyGate(1);

        try (BrowserConcurrencyGate.Permit ignored = gate.acquire(100)) {
            AcquisitionException error = assertThrows(AcquisitionException.class,
                    () -> gate.acquire(20));
            assertEquals(AcquisitionErrorType.TIMEOUT, error.getErrorType());
        }

        try (BrowserConcurrencyGate.Permit ignored = gate.acquire(20)) {
            assertEquals(0, gate.availablePermits());
        }
        assertEquals(1, gate.availablePermits());
    }
}
