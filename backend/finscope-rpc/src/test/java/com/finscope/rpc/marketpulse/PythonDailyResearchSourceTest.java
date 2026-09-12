package com.finscope.rpc.marketpulse;

import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PythonDailyResearchSourceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 11);
    private static final String PAYLOAD = """
            {"schema_version":"daily-research-v1","business_date":"2026-09-11",
             "selection_date":"2026-09-10","source_code":"LOCAL_DAILY_BAR_PANEL",
             "quality_status":"PARTIAL","sample_count":1,
             "stocks":[{"instrument_code":"600519.SH","return_1d":-2,"return_5d":null,
              "return_20d":null,"amount":100,"group_codes":["STRONG"]}],
             "groups":[{"code":"STRONG","label":"昨日强势组","definition":"昨日涨幅≥3%",
               "eligible_count":1,"member_count":1,"valid_count":1,"advance_ratio":null,
               "median_return":null,"members":["600519.SH"]},
               {"code":"TREND","label":"趋势组","definition":"趋势","eligible_count":0,
               "member_count":0,"valid_count":0,"members":[]},
               {"code":"BREAKOUT","label":"突破组","definition":"突破","eligible_count":0,
               "member_count":0,"valid_count":0,"members":[]}],"warnings":["本地样本"]}
            """;

    @Test
    void mapsPercentUnitsMembersAndDateWithoutLosingMissingData() {
        var result = source(PAYLOAD).fetch(DATE);
        assertEquals(DATE, result.getBusinessDate());
        assertEquals(-2D, result.getStocks().get(0).getReturn1d());
        assertNull(result.getStocks().get(0).getReturn5d());
        assertEquals(1, result.getGroups().get(0).getMemberCount());
        assertNull(result.getGroups().get(0).getMedianReturn());
    }

    @Test
    void rejectsDateVersionCountsAndUnknownMembers() {
        for (String invalid : new String[] {
                PAYLOAD.replace("daily-research-v1", "daily-research-v0"),
                PAYLOAD.replace("2026-09-11", "2026-09-12"),
                PAYLOAD.replace("\"valid_count\":1", "\"valid_count\":2"),
                PAYLOAD.replace("\"members\":[\"600519.SH\"]", "\"members\":[\"600000.SH\"]"),
                PAYLOAD.replace("\"amount\":100", "\"amount\":-1"),
                PAYLOAD.replace("\"return_1d\":-2", "\"return_1d\":\"NaN\"")
        }) {
            assertThrows(ProviderContractException.class, () -> source(invalid).fetch(DATE));
        }
    }

    private PythonDailyResearchSource source(String payload) {
        var source = new PythonDailyResearchSource();
        ReflectionTestUtils.setField(source, "baseUrl", "http://127.0.0.1:8000/");
        com.finscope.rpc.marketintel.FinanceHttpClient http = (provider, uri, headers) -> {
            assertEquals("/v1/markets/CN-A/daily-research", uri.getPath());
            assertEquals("business_date=2026-09-11", uri.getQuery());
            return new FinanceHttpResponse(200, payload, Instant.now(), "test");
        };
        ReflectionTestUtils.setField(source, "http", http);
        return source;
    }
}
