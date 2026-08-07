package com.finscope.rpc.financials;

import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialStatementType;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecFinancialDataClientTest {
    @Test
    void allowsCompanyFactsResponsesUpToSixteenMebibytes() {
        AtomicInteger responseLimit = new AtomicInteger();
        FinanceHttpClient http = new FinanceHttpClient() {
            public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers) {
                throw new AssertionError("SEC client should provide an explicit response limit");
            }

            public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers,
                                           int maxResponseBytes) {
                responseLimit.set(maxResponseBytes);
                return new FinanceHttpResponse(200, annualCompanyFacts(),
                        Instant.parse("2026-08-08T00:00:00Z"), "facts-hash");
            }
        };

        new SecFinancialDataClient(http).fetch(
                apple(), LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);

        assertEquals(16 * 1024 * 1024, responseLimit.get());
    }

    @Test
    void fetchesAnAnnualFilingByFiscalYearAndMapsAllThreeStatements() {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        AtomicReference<Map<String, String>> headers = new AtomicReference<Map<String, String>>();
        FinanceHttpClient http = (provider, uri, requestHeaders) -> {
            requested.set(uri);
            headers.set(requestHeaders);
            return new FinanceHttpResponse(200, annualCompanyFacts(),
                    Instant.parse("2026-08-08T00:00:00Z"), "facts-hash");
        };

        ExternalFinancialStatements result = new SecFinancialDataClient(http).fetch(
                apple(), LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);

        assertEquals("/api/xbrl/companyfacts/CIK0000320193.json", requested.get().getPath());
        assertTrue(headers.get().get("User-Agent").contains("FinScope"));
        assertEquals(LocalDate.of(2025, 9, 27), result.getPeriodEnd());
        assertEquals("USD", result.getCurrency());
        assertEquals("SEC_COMPANY_FACTS", result.getSourceCode());
        assertEquals(Boolean.TRUE, result.getAudited());
        assertEquals(new BigDecimal("416161000000"),
                result.find(FinancialStatementType.INCOME, "REVENUE").getValue());
        assertEquals(new BigDecimal("359241000000"),
                result.find(FinancialStatementType.BALANCE_SHEET, "TOTAL_ASSETS").getValue());
        assertEquals(new BigDecimal("111482000000"),
                result.find(FinancialStatementType.CASH_FLOW, "OPERATING_CASH_FLOW").getValue());
    }

    @Test
    void selectsYearToDateFactsForTheRequestedFiscalQuarter() {
        FinanceHttpClient http = (provider, uri, headers) -> new FinanceHttpResponse(
                200, q2CompanyFacts(), Instant.parse("2026-08-08T00:00:00Z"), "facts-hash");

        ExternalFinancialStatements result = new SecFinancialDataClient(http).fetch(
                apple(), LocalDate.of(2025, 6, 30), FinancialReportType.HALF_YEAR);

        assertEquals(LocalDate.of(2025, 3, 29), result.getPeriodEnd());
        assertEquals(new BigDecimal("219659000000"),
                result.find(FinancialStatementType.INCOME, "REVENUE").getValue());
        assertEquals("CURRENT_YTD",
                result.find(FinancialStatementType.INCOME, "REVENUE").getPeriodRole());
    }

    private static Instrument apple() {
        Instrument value = new Instrument();
        value.setCode("AAPL");
        value.setName("Apple Inc.");
        value.setType("STOCK");
        value.setMarket("US");
        value.setAliases("SEC_CIK:0000320193");
        return value;
    }

    private static String annualCompanyFacts() {
        return "{\"cik\":320193,\"entityName\":\"Apple Inc.\",\"facts\":{\"us-gaap\":{" +
                concept("RevenueFromContractWithCustomerExcludingAssessedTax", "Net sales",
                        "[{\"start\":\"2022-09-25\",\"end\":\"2023-09-30\",\"val\":383285000000,\"fy\":2025,\"fp\":\"FY\",\"form\":\"10-K\",\"filed\":\"2025-10-31\",\"accn\":\"0000320193-25-000079\"},"
                                + "{\"start\":\"2024-09-29\",\"end\":\"2025-09-27\",\"val\":416161000000,\"fy\":2025,\"fp\":\"FY\",\"form\":\"10-K\",\"filed\":\"2025-10-31\",\"accn\":\"0000320193-25-000079\"}]") + "," +
                concept("Assets", "Total assets",
                        instant("2025-09-27", "359241000000", "2025", "FY", "10-K", "2025-10-31", "0000320193-25-000079")) + "," +
                concept("NetCashProvidedByUsedInOperatingActivities", "Cash generated by operating activities",
                        duration("2024-09-29", "2025-09-27", "111482000000", "2025", "FY", "10-K", "2025-10-31", "0000320193-25-000079")) +
                "}}}";
    }

    private static String q2CompanyFacts() {
        return "{\"cik\":320193,\"entityName\":\"Apple Inc.\",\"facts\":{\"us-gaap\":{" +
                concept("RevenueFromContractWithCustomerExcludingAssessedTax", "Net sales",
                        "[{\"start\":\"2024-09-29\",\"end\":\"2025-03-29\",\"val\":219659000000,\"fy\":2025,\"fp\":\"Q2\",\"form\":\"10-Q\",\"filed\":\"2025-05-02\",\"accn\":\"0000320193-25-000057\"}," +
                                "{\"start\":\"2024-12-29\",\"end\":\"2025-03-29\",\"val\":95359000000,\"fy\":2025,\"fp\":\"Q2\",\"form\":\"10-Q\",\"filed\":\"2025-05-02\",\"accn\":\"0000320193-25-000057\"}]") + "," +
                concept("Assets", "Total assets",
                        instant("2025-03-29", "331495000000", "2025", "Q2", "10-Q", "2025-05-02", "0000320193-25-000057")) + "," +
                concept("NetCashProvidedByUsedInOperatingActivities", "Operating cash flow",
                        duration("2024-09-29", "2025-03-29", "53827000000", "2025", "Q2", "10-Q", "2025-05-02", "0000320193-25-000057")) +
                "}}}";
    }

    private static String concept(String name, String label, String facts) {
        return "\"" + name + "\":{\"label\":\"" + label + "\",\"units\":{\"USD\":" + facts + "}}";
    }

    private static String duration(String start, String end, String value, String fy, String fp,
                                   String form, String filed, String accession) {
        return "[{\"start\":\"" + start + "\",\"end\":\"" + end + "\",\"val\":" + value
                + ",\"fy\":" + fy + ",\"fp\":\"" + fp + "\",\"form\":\"" + form
                + "\",\"filed\":\"" + filed + "\",\"accn\":\"" + accession + "\"}]";
    }

    private static String instant(String end, String value, String fy, String fp,
                                  String form, String filed, String accession) {
        return "[{\"end\":\"" + end + "\",\"val\":" + value + ",\"fy\":" + fy
                + ",\"fp\":\"" + fp + "\",\"form\":\"" + form + "\",\"filed\":\""
                + filed + "\",\"accn\":\"" + accession + "\"}]";
    }
}
