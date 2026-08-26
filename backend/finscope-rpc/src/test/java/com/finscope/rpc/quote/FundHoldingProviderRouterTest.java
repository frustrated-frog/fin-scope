package com.finscope.rpc.quote;

import com.finscope.domain.instrument.FundHoldingDisclosure;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FundHoldingProviderRouterTest {

    @Test
    void usesConfiguredFuyaoProvider() {
        FuyaoFundHoldingProvider fuyao = new StubFuyaoProvider(true, disclosure("fuyao"), null);
        FundHoldingProviderRouter router = router(fuyao);

        FundHoldingDisclosure result = router.fetch("510300");

        assertEquals("fuyao", result.getFundName());
    }

    @Test
    void propagatesFuyaoFailureWithoutLegacyFallback() {
        ProviderContractException failure = new ProviderContractException(
                "FUYAO_5003", "upstream unavailable", true);
        FuyaoFundHoldingProvider fuyao = new StubFuyaoProvider(
                true,
                null,
                failure);
        FundHoldingProviderRouter router = router(fuyao);

        ProviderContractException actual = assertThrows(
                ProviderContractException.class, () -> router.fetch("510300"));

        assertSame(failure, actual);
    }

    @Test
    void propagatesFuyaoConfigurationFailureWithoutLegacyFallback() {
        ProviderContractException failure = new ProviderContractException(
                "FUYAO_NOT_CONFIGURED", "missing key", false);
        FuyaoFundHoldingProvider fuyao = new StubFuyaoProvider(false, null, failure);
        FundHoldingProviderRouter router = router(fuyao);

        assertSame(failure, assertThrows(
                ProviderContractException.class, () -> router.fetch("510300")));
    }

    private FundHoldingProviderRouter router(FuyaoFundHoldingProvider fuyao) {
        FundHoldingProviderRouter router = new FundHoldingProviderRouter();
        ReflectionTestUtils.setField(router, "fuyao", fuyao);
        return router;
    }

    private FundHoldingDisclosure disclosure(String name) {
        return new FundHoldingDisclosure("510300", name, null,
                LocalDateTime.of(2026, 8, 26, 10, 0), Collections.emptyList());
    }

    private static final class StubFuyaoProvider extends FuyaoFundHoldingProvider {
        private final boolean configured;
        private final FundHoldingDisclosure result;
        private final ProviderContractException error;

        private StubFuyaoProvider(boolean configured, FundHoldingDisclosure result,
                                  ProviderContractException error) {
            this.configured = configured;
            this.result = result;
            this.error = error;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public FundHoldingDisclosure fetch(String fundCode) {
            if (error != null) {
                throw error;
            }
            return result;
        }
    }

}
