package com.finscope.rpc.quote;

import com.finscope.domain.instrument.FundHoldingDisclosure;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FundHoldingProviderRouterTest {

    @Test
    void prefersConfiguredFuyaoProvider() {
        FuyaoFundHoldingProvider fuyao = new StubFuyaoProvider(true, disclosure("fuyao"), null);
        EastmoneyFundHoldingProvider eastmoney = new StubEastmoneyProvider(disclosure("eastmoney"));
        FundHoldingProviderRouter router = router(fuyao, eastmoney);

        FundHoldingDisclosure result = router.fetch("510300");

        assertEquals("fuyao", result.getFundName());
    }

    @Test
    void fallsBackToEastmoneyWhenFuyaoIsUnavailable() {
        FuyaoFundHoldingProvider fuyao = new StubFuyaoProvider(
                true,
                null,
                new ProviderContractException("FUYAO_5003", "upstream unavailable", true));
        EastmoneyFundHoldingProvider eastmoney = new StubEastmoneyProvider(disclosure("eastmoney"));
        FundHoldingProviderRouter router = router(fuyao, eastmoney);

        FundHoldingDisclosure result = router.fetch("510300");

        assertEquals("eastmoney", result.getFundName());
    }

    @Test
    void skipsFuyaoWhenApiKeyIsNotConfigured() {
        FuyaoFundHoldingProvider fuyao = new StubFuyaoProvider(false, disclosure("fuyao"), null);
        EastmoneyFundHoldingProvider eastmoney = new StubEastmoneyProvider(disclosure("eastmoney"));
        FundHoldingProviderRouter router = router(fuyao, eastmoney);

        FundHoldingDisclosure result = router.fetch("510300");

        assertEquals("eastmoney", result.getFundName());
    }

    private FundHoldingProviderRouter router(FuyaoFundHoldingProvider fuyao,
                                             EastmoneyFundHoldingProvider eastmoney) {
        FundHoldingProviderRouter router = new FundHoldingProviderRouter();
        ReflectionTestUtils.setField(router, "fuyao", fuyao);
        ReflectionTestUtils.setField(router, "eastmoney", eastmoney);
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

    private static final class StubEastmoneyProvider extends EastmoneyFundHoldingProvider {
        private final FundHoldingDisclosure result;

        private StubEastmoneyProvider(FundHoldingDisclosure result) {
            super(url -> "");
            this.result = result;
        }

        @Override
        public FundHoldingDisclosure fetch(String fundCode) {
            return result;
        }
    }
}
