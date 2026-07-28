package com.finscope.rpc.acquisition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AcquisitionContextTest {

    @Test
    void scopesRunAndSourceIdsAndAlwaysCleansThreadLocal() {
        assertFalse(AcquisitionContext.current().isPresent());

        try (AcquisitionContext.Scope ignored = AcquisitionContext.open(11L, 22L)) {
            assertEquals(Long.valueOf(11L), AcquisitionContext.current().get().getFetchRunId());
            assertEquals(Long.valueOf(22L), AcquisitionContext.current().get().getSourceId());
        }

        assertFalse(AcquisitionContext.current().isPresent());
    }
}
