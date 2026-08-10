package com.finscope.rpc.quote;

import com.finscope.domain.instrument.FundHoldingDisclosure;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EastmoneyFundHoldingProviderTest {

    @Test
    void parsesLatestDisclosureAndHoldingNumbers() throws Exception {
        List<String> requestedUrls = new ArrayList<String>();
        EastmoneyFundHoldingProvider provider = new EastmoneyFundHoldingProvider(url -> {
            requestedUrls.add(url);
            return fixture();
        });

        FundHoldingDisclosure disclosure = provider.fetch("021894");

        assertEquals(1, requestedUrls.size());
        assertTrue(requestedUrls.get(0).startsWith(
                "https://fundf10.eastmoney.com/FundArchivesDatas.aspx?"));
        assertTrue(requestedUrls.get(0).contains("type=jjcc"));
        assertTrue(requestedUrls.get(0).contains("code=021894"));
        assertTrue(requestedUrls.get(0).contains("topline=10"));
        assertEquals("021894", disclosure.getFundCode());
        assertEquals("易方达半导体设备ETF联接C", disclosure.getFundName());
        assertEquals(LocalDate.of(2026, 6, 30), disclosure.getDisclosureDate());
        assertEquals(2, disclosure.getHoldings().size());
        assertEquals(1, disclosure.getHoldings().get(0).getRank());
        assertEquals("688012", disclosure.getHoldings().get(0).getStockCode());
        assertEquals("中微公司", disclosure.getHoldings().get(0).getStockName());
        assertEquals(0.32d, disclosure.getHoldings().get(0).getWeightPct(), 0.000001d);
        assertEquals(4.20d, disclosure.getHoldings().get(0).getSharesTenThousand(), 0.000001d);
        assertEquals(1967.70d, disclosure.getHoldings().get(0).getMarketValueTenThousand(), 0.000001d);
    }

    @Test
    void rejectsPayloadWithoutDisclosureDate() {
        EastmoneyFundHoldingProvider provider = new EastmoneyFundHoldingProvider(url ->
                "var apidata={content:\"<table class='tzxq'><tbody></tbody></table>\"};");

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider.fetch("021894"));

        assertEquals("FUND_HOLDING_CONTRACT_DRIFT", error.getErrorType());
    }

    @Test
    void returnsEmptyDisclosureForAValidEmptyTable() {
        String payload = "var apidata={content:\"<div class='boxitem'><h4 class='t'>"
                + "<label class='left'><a>纯债基金</a> 2026年2季度股票投资明细</label>"
                + "<label class='right'>截止至：<font>2026-06-30</font></label></h4>"
                + "<table class='tzxq'><tbody></tbody></table></div>\"};";
        EastmoneyFundHoldingProvider provider = new EastmoneyFundHoldingProvider(url -> payload);

        FundHoldingDisclosure disclosure = provider.fetch("000001");

        assertEquals("纯债基金", disclosure.getFundName());
        assertTrue(disclosure.getHoldings().isEmpty());
    }

    @Test
    void rejectsInvalidFundCodeBeforeRequesting() {
        List<String> requestedUrls = new ArrayList<String>();
        EastmoneyFundHoldingProvider provider = new EastmoneyFundHoldingProvider(url -> {
            requestedUrls.add(url);
            return fixture();
        });

        assertThrows(IllegalArgumentException.class, () -> provider.fetch("../021894"));
        assertTrue(requestedUrls.isEmpty());
    }

    @Test
    void rejectsMalformedHoldingWeight() throws Exception {
        String payload = fixture().replace("0.32%", "not-a-percent");
        EastmoneyFundHoldingProvider provider = new EastmoneyFundHoldingProvider(url -> payload);

        assertThrows(ProviderContractException.class, () -> provider.fetch("021894"));
    }

    private String fixture() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/quote/eastmoney-fund-holdings.txt")) {
            if (stream == null) {
                throw new IllegalStateException("missing fund holdings fixture");
            }
            byte[] bytes = new byte[stream.available()];
            int read = stream.read(bytes);
            if (read != bytes.length) {
                throw new IllegalStateException("failed to read fund holdings fixture");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
