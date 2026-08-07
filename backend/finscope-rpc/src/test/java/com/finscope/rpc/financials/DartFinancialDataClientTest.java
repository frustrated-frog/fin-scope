package com.finscope.rpc.financials;

import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialStatementType;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.search.SearchResult;
import com.finscope.domain.search.WebSearchRequest;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.search.WebSearchProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DartFinancialDataClientTest {
    @Test
    void usesTheOfficialDartDisclosureSearchBeforeWebSearch() {
        AtomicReference<String> form = new AtomicReference<String>();
        WebSearchProvider unusedSearch = new WebSearchProvider() {
            public String providerCode() { return "UNUSED"; }
            public boolean isConfigured() { return true; }
            public List<SearchResult> search(WebSearchRequest request) {
                throw new AssertionError("official DART search should be the primary discovery path");
            }
        };
        FinanceHttpClient http = new FinanceHttpClient() {
            public FinanceHttpResponse get(String provider, java.net.URI uri,
                                           java.util.Map<String, String> headers) {
                return ok(response(uri.getPath(), uri.getQuery()));
            }

            public FinanceHttpResponse postForm(String provider, java.net.URI uri, String body,
                                                java.util.Map<String, String> headers) {
                if (uri.getPath().endsWith("/searchCorp.ax")) {
                    return ok("<table><tr><td><input type='hidden' name='hiddenCikCD1' value='99999999'>"
                            + "SK hynix system ic</td><td>111111</td></tr>"
                            + "<tr><td><input type='hidden' name='hiddenCikCD1' value='00164779'>"
                            + "SK hynix</td><td>000660</td></tr></table>");
                }
                form.set(body);
                return ok("<a href='/dsbh001/main.do?rcpNo=20260317000635'>Annual Report</a>");
            }
        };

        ExternalFinancialStatements result = new DartFinancialDataClient(
                http, Collections.singletonList(unusedSearch)).fetch(
                skHynix(), LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);

        assertTrue(form.get().contains("textCrpCik=00164779"));
        assertTrue(form.get().contains("publicType=A001"));
        assertTrue(form.get().contains("startDate=20260101"));
        assertEquals(LocalDate.of(2025, 12, 31), result.getPeriodEnd());
    }

    @Test
    void discoversAnOfficialDartFilingAndMapsConsolidatedIfrsStatements() {
        AtomicReference<String> query = new AtomicReference<String>();
        WebSearchProvider search = new WebSearchProvider() {
            public String providerCode() { return "TEST_SEARCH"; }
            public boolean isConfigured() { return true; }
            public List<SearchResult> search(WebSearchRequest request) {
                query.set(request.getQuery());
                SearchResult wrong = new SearchResult();
                wrong.setTitle("SK hynix/Quarterly Report");
                wrong.setUrl("https://englishdart.fss.or.kr/dsbh001/main.do?rcpNo=20260101000001");
                SearchResult result = new SearchResult();
                result.setTitle("SK hynix/Annual Report/2026.03.17");
                result.setUrl("https://englishdart.fss.or.kr/dsbh001/main.do?rcpNo=20260317000635");
                return java.util.Arrays.asList(wrong, result);
            }
        };
        FinanceHttpClient http = (provider, uri, headers) -> new FinanceHttpResponse(
                200, response(uri.getPath(), uri.getQuery()),
                Instant.parse("2026-08-08T00:00:00Z"), "dart-hash");

        ExternalFinancialStatements result = new DartFinancialDataClient(
                http, Collections.singletonList(search)).fetch(
                skHynix(), LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);

        assertTrue(query.get().contains("000660"));
        assertTrue(query.get().contains("Annual Report"));
        assertTrue(query.get().contains("2026"));
        assertEquals(LocalDate.of(2025, 12, 31), result.getPeriodEnd());
        assertEquals("KRW", result.getCurrency());
        assertEquals("DART_XBRL", result.getSourceCode());
        assertEquals(new BigDecimal("66192960000000"),
                result.find(FinancialStatementType.INCOME, "REVENUE").getValue());
        assertEquals(new BigDecimal("119855209000000"),
                result.find(FinancialStatementType.BALANCE_SHEET, "TOTAL_ASSETS").getValue());
        assertEquals(new BigDecimal("24500000000000"),
                result.find(FinancialStatementType.CASH_FLOW, "OPERATING_CASH_FLOW").getValue());
    }

    @Test
    void selectsTheYearToDateColumnWhenAQuarterlyStatementAlsoContainsASingleQuarter() {
        WebSearchProvider search = new WebSearchProvider() {
            public String providerCode() { return "TEST_SEARCH"; }
            public boolean isConfigured() { return true; }
            public List<SearchResult> search(WebSearchRequest request) {
                SearchResult result = new SearchResult();
                result.setUrl("https://englishdart.fss.or.kr/dsbh001/main.do?rcpNo=20251114000001");
                return Collections.singletonList(result);
            }
        };
        FinanceHttpClient http = (provider, uri, headers) -> ok(
                quarterResponse(uri.getPath(), uri.getQuery()));

        ExternalFinancialStatements result = new DartFinancialDataClient(
                http, Collections.singletonList(search)).fetch(
                skHynix(), LocalDate.of(2025, 9, 30), FinancialReportType.Q3);

        assertEquals(new BigDecimal("900"),
                result.find(FinancialStatementType.INCOME, "REVENUE").getValue());
        assertEquals(new BigDecimal("700"),
                result.find(FinancialStatementType.CASH_FLOW, "OPERATING_CASH_FLOW").getValue());
    }

    private static Instrument skHynix() {
        Instrument value = new Instrument();
        value.setCode("000660");
        value.setName("SK hynix Inc.");
        value.setType("STOCK");
        value.setMarket("KR");
        value.setAliases("KRX_SYMBOL:000660");
        return value;
    }

    private static String response(String path, String query) {
        if (path.endsWith("/main.do")) {
            if (query.contains("20260101000001")) {
                return "<html><head><title>Other Corp/Annual Report/2026.01.01</title></head><body>"
                        + role("D210000", "dart_2025_role-D210000")
                        + role("D431410", "dart_2025_role-D431410")
                        + role("D520000", "dart_2025_role-D520000") + "</body></html>";
            }
            return "<html><head><title>SK hynix/Annual Report/2026.03.17</title></head><body>"
                    + role("D210000", "dart_2025_role-D210000")
                    + role("D431410", "dart_2025_role-D431410")
                    + role("D520000", "dart_2025_role-D520000") + "</body></html>";
        }
        if (query.contains("D210000")) {
            return table(row("ifrs-full_Assets", "Total assets", "119,855,209,000,000")
                    + row("ifrs-full_Liabilities", "Total liabilities", "45,939,505,000,000")
                    + row("ifrs-full_Equity", "Total equity", "73,915,704,000,000"));
        }
        if (query.contains("D431410")) {
            return table(row("ifrs-full_Revenue", "Revenue", "66,192,960,000,000")
                    + row("ifrs-full_CostOfSales", "Cost of sales", "34,364,814,000,000")
                    + row("ifrs-full_ProfitLoss", "Profit", "19,796,902,000,000"));
        }
        return table(row("ifrs-full_CashFlowsFromUsedInOperatingActivities",
                "Cash flows from operating activities", "24,500,000,000,000"));
    }

    private static String quarterResponse(String path, String query) {
        if (path.endsWith("/main.do")) {
            return "<html><head><title>SK hynix/Quarterly Report/2025.11.14</title></head><body>"
                    + role("D210000", "dart_2025_role-D210000")
                    + role("D431410", "dart_2025_role-D431410")
                    + role("D520000", "dart_2025_role-D520000") + "</body></html>";
        }
        String concept = query.contains("D210000") ? "ifrs-full_Assets"
                : query.contains("D431410") ? "ifrs-full_Revenue"
                : "ifrs-full_CashFlowsFromUsedInOperatingActivities";
        String ytd = query.contains("D210000") ? "1000" : query.contains("D431410") ? "900" : "700";
        return "<table class='fact-table'><tr><th></th>"
                + "<th class='period'>2025-07-01 ~ 2025-09-30</th>"
                + "<th class='period'>2025-01-01 ~ 2025-09-30</th></tr>"
                + "<tr><td><span class='concept-label' id='" + concept + "#root'>Value</span></td>"
                + "<td><span class='fact-value'>300</span></td>"
                + "<td><span class='fact-value'>" + ytd + "</span></td></tr></table>";
    }

    private static FinanceHttpResponse ok(String body) {
        return new FinanceHttpResponse(200, body,
                Instant.parse("2026-08-08T00:00:00Z"), "dart-hash");
    }

    private static String role(String code, String roleId) {
        return "<a id='role_" + code + "' onclick=\"viewDoc('20260317000044', '"
                + roleId + "', 'en', '" + code + "')\">Consolidated statement</a>";
    }

    private static String table(String rows) {
        return "<table class='fact-table'><tr><th></th><th class='period'>2025-01-01 ~ 2025-12-31</th>"
                + "<th class='period'>2024-01-01 ~ 2024-12-31</th></tr>" + rows + "</table>";
    }

    private static String row(String concept, String label, String value) {
        return "<tr><td><span class='concept-label' id='" + concept + "#root'>" + label
                + "</span></td><td><span class='fact-value'>" + value
                + "</span></td><td><span class='fact-value'>1</span></td></tr>";
    }
}
