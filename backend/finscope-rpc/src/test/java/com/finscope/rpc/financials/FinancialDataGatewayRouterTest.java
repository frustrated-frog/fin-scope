package com.finscope.rpc.financials;

import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinancialDataGatewayRouterTest {
    @Test
    void routesUnitedStatesStocksToTheSecGateway() {
        ExternalFinancialStatements expected = new ExternalFinancialStatements();
        expected.setSourceCode("SEC_COMPANY_FACTS");
        StructuredFinancialDataGateway aShare = gateway("SH", "PYTHON", null);
        StructuredFinancialDataGateway sec = gateway("US", "SEC", expected);

        ExternalFinancialStatements result = new FinancialDataGatewayRouter(
                Arrays.asList(aShare, sec)).fetch(stock("US"), LocalDate.of(2025, 12, 31),
                FinancialReportType.ANNUAL);

        assertEquals(expected, result);
    }

    @Test
    void rejectsMarketsWithoutAStructuredFinancialProvider() {
        FinancialDataGatewayRouter router = new FinancialDataGatewayRouter(Collections.emptyList());

        assertThrows(ProviderContractException.class, () -> router.fetch(
                stock("KR"), LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL));
    }

    private static StructuredFinancialDataGateway gateway(String market, String name,
                                                           ExternalFinancialStatements result) {
        return new StructuredFinancialDataGateway() {
            @Override
            public boolean supports(Instrument instrument) {
                return market.equals(instrument.getMarket());
            }

            @Override
            public ExternalFinancialStatements fetch(Instrument instrument, LocalDate periodEnd,
                                                     FinancialReportType reportType) {
                return result;
            }

            @Override
            public String providerCode() {
                return name;
            }
        };
    }

    private static Instrument stock(String market) {
        Instrument value = new Instrument();
        value.setCode("CODE");
        value.setType("STOCK");
        value.setMarket(market);
        return value;
    }
}
