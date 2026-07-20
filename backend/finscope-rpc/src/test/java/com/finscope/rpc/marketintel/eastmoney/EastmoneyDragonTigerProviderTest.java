package com.finscope.rpc.marketintel.eastmoney;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.DragonTigerRecord;
import com.finscope.domain.marketintel.DragonTigerSeat;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.DragonTigerData;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EastmoneyDragonTigerProviderTest {

    @Test
    void parsesSummaryAndTopFiveSeats() throws Exception {
        ProviderResult<DragonTigerData> result = provider(new FixtureHttpClient()).fetch(
                stock(), LocalDate.of(2026, 3, 19), LocalDate.of(2026, 7, 16));

        DragonTigerRecord record = result.getData().getRecords().get(0);
        assertEquals("100373909", record.getExternalId());
        assertEquals(new BigDecimal("-395870676.13"), record.getNetAmount());
        assertEquals("4家机构卖出，成功率22.22%", record.getProviderExplanation());
        assertEquals(5, record.getBuySeats().size());
        assertEquals(5, record.getSellSeats().size());
        assertTrue(record.getBuySeats().stream().anyMatch(DragonTigerSeat::isInstitutional));
        assertTrue(record.getBuySeats().stream().anyMatch(DragonTigerSeat::isNorthbound));
        assertEquals("COMPLETE", record.getQualityStatus());
    }

    @Test
    void requestsOnlyTheTargetSecurityAndRange() throws Exception {
        RecordingHttpClient client = new RecordingHttpClient();

        provider(client).fetch(stock(), LocalDate.of(2026, 3, 19), LocalDate.of(2026, 7, 16));

        String summary = URLDecoder.decode(client.summary().getRawQuery(), "UTF-8");
        assertTrue(summary.contains("reportName=RPT_DAILYBILLBOARD_DETAILSNEW"));
        assertTrue(summary.contains("(SECURITY_CODE=\"000021\")"));
        assertTrue(summary.contains("(TRADE_DATE>='2026-03-19')"));
        assertTrue(summary.contains("(TRADE_DATE<='2026-07-16')"));
        assertTrue(client.requests.stream().filter(uri -> decoded(uri)
                .contains("RPT_BILLBOARD_DAILYDETAILSBUY")).allMatch(uri -> decoded(uri)
                .contains("(TRADE_DATE='2026-07-15')")));
    }

    @Test
    void treatsAnEmptySummaryAsSuccess() {
        ProviderResult<DragonTigerData> result = provider(new EmptyHttpClient()).fetch(
                stock(), LocalDate.of(2026, 3, 19), LocalDate.of(2026, 7, 16));

        assertTrue(result.getData().getRecords().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void keepsSummaryAndMarksPartialWhenOneSeatDirectionFails() {
        FixtureHttpClient fixtures = new FixtureHttpClient();
        FinanceHttpClient partial = (provider, uri, headers) -> {
            if (decoded(uri).contains("RPT_BILLBOARD_DAILYDETAILSSELL")) {
                throw new ProviderContractException("HTTP_503", "temporary unavailable", true);
            }
            return fixtures.get(provider, uri, headers);
        };

        ProviderResult<DragonTigerData> result = provider(partial).fetch(
                stock(), LocalDate.of(2026, 3, 19), LocalDate.of(2026, 7, 16));

        assertEquals("PARTIAL", result.getData().getRecords().get(0).getQualityStatus());
        assertEquals(5, result.getData().getRecords().get(0).getBuySeats().size());
        assertTrue(result.getData().getRecords().get(0).getSellSeats().isEmpty());
        assertTrue(result.getWarnings().stream()
                .anyMatch(value -> value.startsWith("DRAGON_TIGER_SEAT_UNAVAILABLE:SELL")));
    }

    @Test
    void keepsTopFiveSeatsForEveryTradeIdOnTheSameDate() {
        FinanceHttpClient client = (provider, uri, headers) -> {
            if (decoded(uri).contains("RPT_DAILYBILLBOARD_DETAILSNEW")) {
                return response("{\"result\":{\"data\":["
                        + summaryRow("trade-1", "原因一") + ","
                        + summaryRow("trade-2", "原因二")
                        + "]},\"success\":true}");
            }
            return response("{\"result\":{\"data\":["
                    + seatRows("trade-1", "原因一") + ","
                    + seatRows("trade-2", "原因二")
                    + "]},\"success\":true}");
        };

        ProviderResult<DragonTigerData> result = provider(client).fetch(
                stock(), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15));

        assertEquals(2, result.getData().getRecords().size());
        result.getData().getRecords().forEach(record -> {
            assertEquals(5, record.getBuySeats().size());
            assertEquals(5, record.getSellSeats().size());
            assertEquals(5, record.getBuySeats().get(4).getRank());
            assertEquals("COMPLETE", record.getQualityStatus());
        });
    }

    @Test
    void rejectsAnInstrumentCodeThatCannotBeSafelyEmbeddedInTheProviderFilter() {
        Instrument instrument = stock();
        instrument.setCode("000021\")");

        assertFalse(provider(new EmptyHttpClient()).supports(instrument));
    }

    private static EastmoneyDragonTigerProvider provider(FinanceHttpClient client) {
        return new EastmoneyDragonTigerProvider(client);
    }

    private static Instrument stock() {
        Instrument value = new Instrument();
        value.setId(7L);
        value.setCode("000021");
        value.setMarket("SZ");
        value.setType("STOCK");
        value.setName("深科技");
        return value;
    }

    private static String decoded(URI uri) {
        try {
            return URLDecoder.decode(uri.getRawQuery(), "UTF-8");
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static class FixtureHttpClient implements FinanceHttpClient {
        @Override
        public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers) throws Exception {
            String query = decoded(uri);
            if (query.contains("RPT_DAILYBILLBOARD_DETAILSNEW")) {
                return fixture("eastmoney-dragon-tiger-records.json");
            }
            if (query.contains("RPT_BILLBOARD_DAILYDETAILSBUY")) {
                return fixture("eastmoney-dragon-tiger-buy.json");
            }
            return fixture("eastmoney-dragon-tiger-sell.json");
        }
    }

    private static final class RecordingHttpClient extends FixtureHttpClient {
        private final List<URI> requests = new ArrayList<URI>();

        @Override
        public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers) throws Exception {
            requests.add(uri);
            return super.get(provider, uri, headers);
        }

        private URI summary() {
            return requests.stream().filter(uri -> decoded(uri)
                    .contains("RPT_DAILYBILLBOARD_DETAILSNEW")).findFirst()
                    .orElseThrow(() -> new AssertionError("missing summary request"));
        }
    }

    private static final class EmptyHttpClient implements FinanceHttpClient {
        @Override
        public FinanceHttpResponse get(String provider, URI uri, Map<String, String> headers) {
            return new FinanceHttpResponse(200,
                    "{\"result\":{\"pages\":0,\"data\":[],\"count\":0},\"success\":true,\"code\":0}",
                    Instant.parse("2026-07-16T08:00:00Z"), "empty");
        }
    }

    private static FinanceHttpResponse fixture(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(EastmoneyDragonTigerProviderTest.class
                .getClassLoader().getResource("marketintel/" + name).toURI()));
        return new FinanceHttpResponse(200, new String(bytes, StandardCharsets.UTF_8),
                Instant.parse("2026-07-16T08:00:00Z"), name);
    }

    private static FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body,
                Instant.parse("2026-07-16T08:00:00Z"), "inline");
    }

    private static String summaryRow(String tradeId, String reason) {
        return "{\"TRADE_DATE\":\"2026-07-15 00:00:00\","
                + "\"EXPLANATION\":\"" + reason + "\",\"TRADE_ID\":\"" + tradeId + "\"}";
    }

    private static String seatRows(String tradeId, String reason) {
        StringBuilder rows = new StringBuilder();
        for (int index = 1; index <= 5; index++) {
            if (index > 1) {
                rows.append(',');
            }
            rows.append("{\"TRADE_ID\":\"").append(tradeId)
                    .append("\",\"EXPLANATION\":\"").append(reason)
                    .append("\",\"OPERATEDEPT_CODE\":\"").append(index)
                    .append("\",\"OPERATEDEPT_NAME\":\"席位").append(index)
                    .append("\",\"BUY\":").append(index)
                    .append(",\"SELL\":").append(index)
                    .append(",\"NET\":0}");
        }
        return rows.toString();
    }
}
