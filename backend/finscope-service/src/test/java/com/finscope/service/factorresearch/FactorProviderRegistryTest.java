package com.finscope.service.factorresearch;

import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorObservation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactorProviderRegistryTest {
    @Test
    void rejectsDuplicateFactorOwnership() {
        FactorIdentity identity = new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0");
        assertThrows(IllegalStateException.class, () -> new FactorProviderRegistry(Arrays.asList(
                provider("first", identity), provider("duplicate", identity))));
    }

    @Test
    void routesByStableIdentityAndCode() {
        FactorIdentity identity = new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0");
        FactorProvider provider = provider("capital-frozen", identity);
        FactorProviderRegistry registry = new FactorProviderRegistry(Collections.singletonList(provider));

        assertEquals(provider, registry.provider(identity));
        assertEquals(identity, registry.identity("MAIN_FLOW_SHARE"));
    }

    private FactorProvider provider(String code, FactorIdentity identity) {
        return new FactorProvider() {
            public Set<FactorIdentity> factors() { return Collections.singleton(identity); }
            public FactorObservation calculate(FactorCalculationContext context, FactorIdentity factor) { return null; }
            public String providerCode() { return code; }
            public String calculationVersion() { return "1"; }
        };
    }
}
