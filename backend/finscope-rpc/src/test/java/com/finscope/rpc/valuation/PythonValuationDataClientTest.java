package com.finscope.rpc.valuation;

import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PythonValuationDataClientTest {
    @Test
    void parsesValuationSnapshotWithoutLosingDecimalPrecision() {
        List<URI> requests = new ArrayList<URI>();
        FinanceHttpClient http = (provider, uri, headers) -> {
            requests.add(uri);
            return new FinanceHttpResponse(200, valuationPayload(),
                    Instant.parse("2026-08-29T02:30:00Z"), "valuation-hash");
        };
        PythonValuationDataClient client = new PythonValuationDataClient(
                "http://127.0.0.1:8000/", http);

        ExternalValuationSnapshot result = client.fetchValuation(stock());

        assertEquals("/v1/stocks/SH/600519/valuation", requests.get(0).getPath());
        assertEquals(new BigDecimal("21.3"), result.getPeTtm());
        assertEquals(new BigDecimal("7.1"), result.getPbMrq());
        assertEquals("FUYAO", result.getSourceCode());
        assertEquals(Instant.parse("2026-08-29T02:29:58Z"), result.getObservedAt());
    }

    @Test
    void parsesCorporateActionsAndPassesExplicitDateWindow() {
        List<URI> requests = new ArrayList<URI>();
        FinanceHttpClient http = (provider, uri, headers) -> {
            requests.add(uri);
            return new FinanceHttpResponse(200, actionsPayload(),
                    Instant.parse("2026-08-29T02:30:00Z"), "actions-hash");
        };
        PythonValuationDataClient client = new PythonValuationDataClient(
                "http://127.0.0.1:8000", http);

        List<ExternalCorporateAction> result = client.fetchCorporateActions(
                stock(), LocalDate.of(2021, 1, 1), LocalDate.of(2026, 8, 29));

        assertEquals("from_date=2021-01-01&to_date=2026-08-29", requests.get(0).getQuery());
        assertEquals(LocalDate.of(2026, 6, 20), result.get(0).getExDate());
        assertEquals(List.of("CASH_DIVIDEND"), result.get(0).getEventTypes());
        assertEquals(new BigDecimal("23.957"), result.get(0).getDividendPerShare());
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

    private static String valuationPayload() {
        return "{\"capability\":\"VALUATION_SNAPSHOT\",\"quality_status\":\"FRESH_PRIMARY\","+
                "\"source_code\":\"FUYAO\",\"warnings\":[],\"data\":{"+
                "\"name\":\"贵州茅台\",\"pe_ttm\":21.3,\"pe_mrq\":20.8,"+
                "\"pb_mrq\":7.1,\"ps_ttm\":10.3,\"pcf_ttm\":19.7,"+
                "\"observed_at\":\"2026-08-29T02:29:58Z\"}}";
    }

    private static String actionsPayload() {
        return "{\"capability\":\"CORPORATE_ACTIONS\",\"quality_status\":\"FRESH_PRIMARY\","+
                "\"source_code\":\"FUYAO\",\"warnings\":[],\"data\":{\"items\":[{"+
                "\"ex_date\":\"2026-06-20\",\"event_types\":[\"CASH_DIVIDEND\"],"+
                "\"dividend_per_share\":23.957,\"per_share_bonus\":0,"+
                "\"allotment_ratio\":0,\"allotment_price\":null,\"currency\":\"CNY\"}]}}";
    }
}
