package com.finscope.rpc.marketintel.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketintel.DragonTigerRecord;
import com.finscope.domain.marketintel.DragonTigerSeat;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.DragonTigerData;
import com.finscope.rpc.marketintel.DragonTigerProvider;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class EastmoneyDragonTigerProvider implements DragonTigerProvider {
    private static final String ENDPOINT =
            "https://datacenter-web.eastmoney.com/api/data/v1/get";
    private static final String SUMMARY_REPORT = "RPT_DAILYBILLBOARD_DETAILSNEW";
    private static final String BUY_REPORT = "RPT_BILLBOARD_DAILYDETAILSBUY";
    private static final String SELL_REPORT = "RPT_BILLBOARD_DAILYDETAILSSELL";
    private static final Set<MarketDataCapability> CAPABILITIES =
            Collections.singleton(MarketDataCapability.DRAGON_TIGER);

    private final FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public EastmoneyDragonTigerProvider(FinanceHttpClient http) {
        this.http = http;
    }

    @Override
    public String providerCode() {
        return "EASTMONEY_DRAGON_TIGER";
    }

    @Override
    public String providerFamily() {
        return "EASTMONEY";
    }

    @Override
    public Set<MarketDataCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public int batchLimit() {
        return 1;
    }

    @Override
    public Duration minimumInterval() {
        return Duration.ofMillis(800);
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(12);
    }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument != null && "STOCK".equals(instrument.getType())
                && ("SH".equals(instrument.getMarket())
                || "SZ".equals(instrument.getMarket())
                || "BJ".equals(instrument.getMarket()));
    }

    @Override
    public ProviderResult<DragonTigerData> fetch(
            Instrument instrument, LocalDate startDate, LocalDate endDate) {
        validate(instrument, startDate, endDate);
        FinanceHttpResponse summary = request(SUMMARY_REPORT,
                "(SECURITY_CODE=\"" + instrument.getCode() + "\")"
                        + "(TRADE_DATE>='" + startDate + "')"
                        + "(TRADE_DATE<='" + endDate + "')");
        List<JsonNode> rows = rows(summary);
        if (rows.isEmpty()) {
            DragonTigerData data = new DragonTigerData(
                    Collections.<DragonTigerRecord>emptyList(), Collections.<String>emptyList());
            return ProviderResult.of(data, local(summary.getRetrievedAt()),
                    summary.getPayloadHash(), Collections.<String>emptyList());
        }

        List<String> warnings = new ArrayList<String>();
        Map<LocalDate, List<DragonTigerRecord>> byDate =
                new LinkedHashMap<LocalDate, List<DragonTigerRecord>>();
        Map<DragonTigerRecord, JsonNode> sources =
                new IdentityHashMap<DragonTigerRecord, JsonNode>();
        List<DragonTigerRecord> records = new ArrayList<DragonTigerRecord>();
        for (JsonNode row : rows) {
            DragonTigerRecord record = record(row, instrument, summary);
            byDate.computeIfAbsent(record.getTradeDate(),
                    ignored -> new ArrayList<DragonTigerRecord>()).add(record);
            sources.put(record, row);
            records.add(record);
        }

        Instant retrievedAt = summary.getRetrievedAt();
        StringBuilder aggregateHash = new StringBuilder(summary.getPayloadHash());
        for (Map.Entry<LocalDate, List<DragonTigerRecord>> entry : byDate.entrySet()) {
            DirectionResult buy = fetchSeats(instrument, entry.getKey(), BUY_REPORT, "BUY", warnings);
            DirectionResult sell = fetchSeats(instrument, entry.getKey(), SELL_REPORT, "SELL", warnings);
            retrievedAt = latest(retrievedAt, buy.retrievedAt, sell.retrievedAt);
            aggregateHash.append('|').append(buy.payloadHash).append('|').append(sell.payloadHash);
            attach(entry.getValue(), sources, buy, sell, warnings);
        }

        DragonTigerData data = new DragonTigerData(records, warnings);
        return ProviderResult.of(data, local(retrievedAt),
                ProviderResult.hashOf(aggregateHash), warnings);
    }

    private void validate(Instrument instrument, LocalDate startDate, LocalDate endDate) {
        if (!supports(instrument)) {
            throw new ProviderContractException("UNSUPPORTED_INSTRUMENT",
                    "Eastmoney dragon tiger requires an A-share stock", false);
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ProviderContractException("INVALID_DATE_RANGE",
                    "Dragon tiger date range is invalid", false);
        }
    }

    private DragonTigerRecord record(
            JsonNode row, Instrument instrument, FinanceHttpResponse response) {
        LocalDate tradeDate = date(row.get("TRADE_DATE"));
        String reason = text(row.get("EXPLANATION"));
        if (tradeDate == null || reason == null) {
            throw drift("summary requires TRADE_DATE and EXPLANATION");
        }
        DragonTigerRecord record = new DragonTigerRecord();
        record.setInstrumentId(instrument.getId());
        record.setProviderCode(providerCode());
        record.setTradeDate(tradeDate);
        record.setExternalId(text(row.get("TRADE_ID")));
        if (record.getExternalId() == null) {
            record.setExternalId(ProviderResult.hashOf(
                    instrument.getCode() + "|" + tradeDate + "|" + reason));
        }
        record.setReasonCode(text(row.get("CHANGE_TYPE")));
        record.setReason(reason);
        record.setProviderExplanation(text(row.get("EXPLAIN")));
        record.setClosePrice(decimal(row.get("CLOSE_PRICE")));
        record.setChangeRate(decimal(row.get("CHANGE_RATE")));
        record.setBuyAmount(decimal(row.get("BILLBOARD_BUY_AMT")));
        record.setSellAmount(decimal(row.get("BILLBOARD_SELL_AMT")));
        record.setNetAmount(decimal(row.get("BILLBOARD_NET_AMT")));
        record.setBillboardAmount(decimal(row.get("BILLBOARD_DEAL_AMT")));
        record.setMarketAmount(decimal(row.get("ACCUM_AMOUNT")));
        record.setNetAmountRatio(decimal(row.get("DEAL_NET_RATIO")));
        record.setBillboardAmountRatio(decimal(row.get("DEAL_AMOUNT_RATIO")));
        record.setTurnoverRate(decimal(row.get("TURNOVERRATE")));
        record.setFreeMarketCap(decimal(row.get("FREE_MARKET_CAP")));
        record.setRetrievedAt(local(response.getRetrievedAt()));
        record.setPayloadHash(response.getPayloadHash());
        record.setQualityStatus("PARTIAL");
        return record;
    }

    private DirectionResult fetchSeats(
            Instrument instrument, LocalDate date, String report, String direction,
            List<String> warnings) {
        try {
            FinanceHttpResponse response = request(report,
                    "(SECURITY_CODE=\"" + instrument.getCode() + "\")"
                            + "(TRADE_DATE='" + date + "')");
            List<JsonNode> rows = rows(response);
            List<SeatRow> seats = new ArrayList<SeatRow>();
            int rank = 1;
            for (JsonNode row : rows) {
                if (rank > 5) {
                    break;
                }
                seats.add(new SeatRow(seat(row, direction, rank++, response), row));
            }
            return DirectionResult.success(direction, seats, response);
        } catch (Exception error) {
            String type = error instanceof ProviderContractException
                    ? ((ProviderContractException) error).getErrorType()
                    : error.getClass().getSimpleName();
            warnings.add("DRAGON_TIGER_SEAT_UNAVAILABLE:" + direction + ":" + type);
            return DirectionResult.failed(direction);
        }
    }

    private DragonTigerSeat seat(
            JsonNode row, String direction, int rank, FinanceHttpResponse response) {
        String name = text(row.get("OPERATEDEPT_NAME"));
        if (name == null) {
            throw drift("seat requires OPERATEDEPT_NAME");
        }
        DragonTigerSeat seat = new DragonTigerSeat();
        seat.setExternalTradeId(text(row.get("TRADE_ID")));
        seat.setSeatCode(text(row.get("OPERATEDEPT_CODE")));
        seat.setSeatName(name);
        seat.setDirection(direction);
        seat.setRank(rank);
        seat.setBuyAmount(decimal(row.get("BUY")));
        seat.setSellAmount(decimal(row.get("SELL")));
        seat.setNetAmount(decimal(row.get("NET")));
        seat.setBuyRatio(decimal(row.get("TOTAL_BUYRIO")));
        seat.setSellRatio(decimal(row.get("TOTAL_SELLRIO")));
        seat.setSeatType(explicitSeatType(name));
        seat.setInstitutional("机构专用".equals(name));
        seat.setNorthbound("沪股通专用".equals(name) || "深股通专用".equals(name));
        seat.setRetrievedAt(local(response.getRetrievedAt()));
        seat.setPayloadHash(response.getPayloadHash());
        return seat;
    }

    private void attach(
            List<DragonTigerRecord> records,
            Map<DragonTigerRecord, JsonNode> sources,
            DirectionResult buy,
            DirectionResult sell,
            List<String> warnings) {
        for (DragonTigerRecord record : records) {
            List<DragonTigerSeat> seats = new ArrayList<DragonTigerSeat>();
            MatchResult buyMatch = match(record, records, buy.rows);
            MatchResult sellMatch = match(record, records, sell.rows);
            seats.addAll(buyMatch.seats);
            seats.addAll(sellMatch.seats);
            record.setSeats(seats);
            boolean complete = buy.success && sell.success
                    && buyMatch.unambiguous && sellMatch.unambiguous
                    && !buyMatch.seats.isEmpty() && !sellMatch.seats.isEmpty();
            record.setQualityStatus(complete ? "COMPLETE" : "PARTIAL");
            record.setRetrievedAt(local(latest(record.getRetrievedAt()
                    .atZone(ZoneId.systemDefault()).toInstant(), buy.retrievedAt, sell.retrievedAt)));
            record.setPayloadHash(ProviderResult.hashOf(
                    sources.get(record).toString() + "|" + seatPayload(buyMatch.seats)
                            + "|" + seatPayload(sellMatch.seats)));
            if (!buyMatch.unambiguous || !sellMatch.unambiguous) {
                addOnce(warnings, "DRAGON_TIGER_SEAT_AMBIGUOUS:"
                        + record.getTradeDate() + ":" + record.getExternalId());
            }
        }
    }

    private MatchResult match(
            DragonTigerRecord record, List<DragonTigerRecord> sameDate, List<SeatRow> rows) {
        List<DragonTigerSeat> byTradeId = new ArrayList<DragonTigerSeat>();
        List<DragonTigerSeat> byReason = new ArrayList<DragonTigerSeat>();
        for (SeatRow row : rows) {
            String tradeId = text(row.source.get("TRADE_ID"));
            String reason = text(row.source.get("EXPLANATION"));
            if (record.getExternalId().equals(tradeId)) {
                byTradeId.add(row.seat);
            } else if (record.getReason().equals(reason)) {
                byReason.add(row.seat);
            }
        }
        if (!byTradeId.isEmpty()) {
            return MatchResult.matched(byTradeId);
        }
        if (!byReason.isEmpty()) {
            return MatchResult.matched(byReason);
        }
        if (sameDate.size() == 1 && !rows.isEmpty()) {
            List<DragonTigerSeat> all = new ArrayList<DragonTigerSeat>();
            for (SeatRow row : rows) {
                all.add(row.seat);
            }
            return MatchResult.matched(all);
        }
        return rows.isEmpty() ? MatchResult.matched(Collections.<DragonTigerSeat>emptyList())
                : MatchResult.ambiguous();
    }

    private FinanceHttpResponse request(String report, String filter) {
        try {
            String query = "reportName=" + encode(report)
                    + "&columns=ALL"
                    + "&filter=" + encode(filter)
                    + "&pageNumber=1&pageSize=500"
                    + "&sortColumns=TRADE_DATE&sortTypes=-1";
            return http.get(providerCode(), URI.create(ENDPOINT + "?" + query),
                    Collections.singletonMap("Referer",
                            "https://data.eastmoney.com/stock/tradedetail.html"));
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("DRAGON_TIGER_HTTP_FAILED",
                    "东方财富龙虎榜请求失败", true, error);
        }
    }

    private List<JsonNode> rows(FinanceHttpResponse response) {
        try {
            JsonNode root = json.readTree(response.getBody());
            if (!root.path("success").asBoolean(false)) {
                throw new ProviderContractException("UPSTREAM_REJECTED",
                        "东方财富龙虎榜接口拒绝请求: " + root.path("message").asText(), true);
            }
            JsonNode data = root.path("result").path("data");
            if (!data.isArray()) {
                throw drift("missing result.data");
            }
            List<JsonNode> rows = new ArrayList<JsonNode>();
            data.forEach(rows::add);
            return rows;
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw drift("invalid response JSON");
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String explicitSeatType(String name) {
        if ("机构专用".equals(name)) {
            return "INSTITUTION";
        }
        if ("沪股通专用".equals(name) || "深股通专用".equals(name)) {
            return "NORTHBOUND";
        }
        return null;
    }

    private static String seatPayload(List<DragonTigerSeat> seats) {
        StringBuilder value = new StringBuilder();
        for (DragonTigerSeat seat : seats) {
            value.append(seat.getDirection()).append(':')
                    .append(seat.getRank()).append(':')
                    .append(seat.getSeatCode()).append(':')
                    .append(seat.getSeatName()).append(':')
                    .append(seat.getPayloadHash()).append('|');
        }
        return value.toString();
    }

    private static BigDecimal decimal(JsonNode value) {
        return value == null || value.isNull() || value.asText().trim().isEmpty()
                ? null : value.decimalValue();
    }

    private static String text(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.trim().isEmpty() ? null : text;
    }

    private static LocalDate date(JsonNode value) {
        String text = text(value);
        return text == null || text.length() < 10 ? null : LocalDate.parse(text.substring(0, 10));
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static Instant latest(Instant first, Instant... values) {
        Instant latest = first;
        for (Instant value : values) {
            if (value != null && (latest == null || value.isAfter(latest))) {
                latest = value;
            }
        }
        return latest;
    }

    private static void addOnce(List<String> warnings, String warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    private static ProviderContractException drift(String detail) {
        return new ProviderContractException("SCHEMA_DRIFT",
                "东方财富龙虎榜响应结构变化: " + detail, false);
    }

    private static final class SeatRow {
        private final DragonTigerSeat seat;
        private final JsonNode source;

        private SeatRow(DragonTigerSeat seat, JsonNode source) {
            this.seat = seat;
            this.source = source;
        }
    }

    private static final class DirectionResult {
        private final boolean success;
        private final List<SeatRow> rows;
        private final Instant retrievedAt;
        private final String payloadHash;

        private DirectionResult(
                boolean success, List<SeatRow> rows, Instant retrievedAt, String payloadHash) {
            this.success = success;
            this.rows = rows;
            this.retrievedAt = retrievedAt;
            this.payloadHash = payloadHash;
        }

        private static DirectionResult success(
                String direction, List<SeatRow> rows, FinanceHttpResponse response) {
            return new DirectionResult(true, rows, response.getRetrievedAt(),
                    direction + ":" + response.getPayloadHash());
        }

        private static DirectionResult failed(String direction) {
            return new DirectionResult(false, Collections.<SeatRow>emptyList(),
                    null, direction + ":unavailable");
        }
    }

    private static final class MatchResult {
        private final List<DragonTigerSeat> seats;
        private final boolean unambiguous;

        private MatchResult(List<DragonTigerSeat> seats, boolean unambiguous) {
            this.seats = seats;
            this.unambiguous = unambiguous;
        }

        private static MatchResult matched(List<DragonTigerSeat> seats) {
            return new MatchResult(seats, true);
        }

        private static MatchResult ambiguous() {
            return new MatchResult(Collections.<DragonTigerSeat>emptyList(), false);
        }
    }
}
