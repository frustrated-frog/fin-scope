package com.finscope.rpc.financials;

import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.common.enums.financials.FinancialStatementType;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PythonFinancialDataClientTest {
    @Test
    void parsesNormalizedStatementsAndPreservesDecimalStrings() {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        FinanceHttpClient http = (provider, uri, headers) -> {
            requested.set(uri);
            return new FinanceHttpResponse(
                    200, payload(), Instant.parse("2026-08-20T10:01:00Z"), "payload-hash");
        };
        PythonFinancialDataClient client = new PythonFinancialDataClient(
                "http://127.0.0.1:8000/", http);

        ExternalFinancialStatements result = client.fetch(
                stock(), LocalDate.of(2026, 6, 30), FinancialReportType.HALF_YEAR);

        assertEquals("/v1/stocks/SH/600519/financial-statements", requested.get().getPath());
        assertEquals("2026-06-30", requested.get().getQuery().split("&")[0].split("=")[1]);
        assertEquals("AKSHARE", result.getSourceCode());
        assertEquals(3, result.getStatements().size());
        assertEquals(new BigDecimal("1200000000.12"),
                result.find(FinancialStatementType.INCOME, "REVENUE").getValue());
        assertEquals(new BigDecimal("220000000"),
                result.find(FinancialStatementType.BALANCE_SHEET, "ACCOUNTS_RECEIVABLE").getValue());
    }

    private static Instrument stock() {
        Instrument value = new Instrument();
        value.setId(7L);
        value.setCode("600519");
        value.setMarket("SH");
        value.setType("STOCK");
        value.setName("贵州茅台");
        return value;
    }

    private static String payload() {
        return "{"
                + "\"capability\":\"FINANCIAL_STATEMENTS\","
                + "\"symbol\":{\"market\":\"SH\",\"code\":\"600519\"},"
                + "\"quality_status\":\"FRESH_PRIMARY\",\"source_code\":\"AKSHARE\","
                + "\"retrieved_at\":\"2026-08-20T10:01:00Z\",\"warnings\":[],\"attempts\":[],"
                + "\"data\":{\"report\":{\"symbol\":{\"market\":\"SH\",\"code\":\"600519\"},"
                + "\"period_end\":\"2026-06-30\",\"report_type\":\"HALF_YEAR\","
                + "\"scope\":\"CONSOLIDATED\",\"published_at\":\"2026-08-20T18:00:00+08:00\","
                + "\"audited\":false,\"currency\":\"CNY\"},\"statements\":["
                + "{\"statement_type\":\"INCOME\",\"values\":[{\"source_label\":\"营业收入\","
                + "\"concept_code\":\"REVENUE\",\"period_role\":\"CURRENT_YTD\","
                + "\"value\":\"1200000000.12\",\"unit_multiplier\":\"1\","
                + "\"source_field\":\"TOTAL_OPERATE_INCOME\"}]},"
                + "{\"statement_type\":\"BALANCE_SHEET\",\"values\":[{\"source_label\":\"应收账款\","
                + "\"concept_code\":\"ACCOUNTS_RECEIVABLE\",\"period_role\":\"CURRENT_PERIOD_END\","
                + "\"value\":\"220000000\",\"unit_multiplier\":\"1\","
                + "\"source_field\":\"ACCOUNTS_RECE\"}]},"
                + "{\"statement_type\":\"CASH_FLOW\",\"values\":[{\"source_label\":\"经营活动现金流量净额\","
                + "\"concept_code\":\"OPERATING_CASH_FLOW\",\"period_role\":\"CURRENT_YTD\","
                + "\"value\":\"180000000\",\"unit_multiplier\":\"1\","
                + "\"source_field\":\"NETCASH_OPERATE\"}]}],\"warnings\":[]}}";
    }
}
