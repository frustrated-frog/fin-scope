package com.finscope.rpc.marketintel.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.rpc.marketintel.CapitalFlowProvider;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.JdkFinanceHttpClient;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EastmoneyCapitalFlowProvider implements CapitalFlowProvider {
    private static final String REALTIME_HOST = "https://push2.eastmoney.com/api/qt/stock/";
    private static final String HISTORY_HOST = "https://push2his.eastmoney.com/api/qt/stock/";
    private static final String EASTMONEY_UT = "7eea3edcaed734bea9cbfc24409ed989";
    private static final String FUND_FLOW_UT = "b2884a393a59ad64002292a3e90d46a5";
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public EastmoneyCapitalFlowProvider(FinanceHttpClient http) {
        this.http = http;
    }

    @Override
    public String providerCode() { return "EASTMONEY"; }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument != null && "STOCK".equals(instrument.getType())
                && ("SH".equals(instrument.getMarket()) || "SZ".equals(instrument.getMarket()) || "BJ".equals(instrument.getMarket()));
    }

    @Override
    public CapitalFlowData fetch(Instrument instrument, LocalDate asOfDate) {
        if (!supports(instrument)) {
            throw new ProviderContractException("UNSUPPORTED_INSTRUMENT", "Eastmoney capital flow requires an A-share stock", false);
        }
        String secid = ("SH".equals(instrument.getMarket()) ? "1." : "0.") + instrument.getCode();
        List<String> warnings = new ArrayList<String>();

        FetchAttempt minuteAttempt = attempt(() -> getMinuteFundFlow(secid));
        FetchAttempt dailyAttempt = attempt(() -> getDailyFundFlow(secid));
        FetchAttempt quoteAttempt = attempt(() -> getQuote(secid));
        FetchAttempt trendAttempt = attempt(() -> getTrend(secid));
        FetchAttempt dailyMarketAttempt = attempt(() -> getDailyMarket(secid));

        QuoteContext quoteContext = QuoteContext.empty();
        if (quoteAttempt.response != null) {
            try {
                JsonNode quote = data(quoteAttempt.response);
                quoteContext = new QuoteContext(scaled(quote.get("f43"), 2), numeric(quote.get("f47")),
                        numeric(quote.get("f48")), scaled(quote.get("f168"), 2), scaled(quote.get("f50"), 2));
            } catch (Exception error) {
                quoteAttempt = FetchAttempt.failed(error);
            }
        }
        if (quoteAttempt.failure != null) addWarning(warnings, "QUOTE_UNAVAILABLE", quoteAttempt.failure);

        Map<LocalDateTime, MarketPoint> intradayMarket = Collections.emptyMap();
        if (trendAttempt.response != null) {
            try { intradayMarket = parseTrends(trendAttempt.response); }
            catch (Exception error) { trendAttempt = FetchAttempt.failed(error); }
        }
        if (trendAttempt.failure != null) addWarning(warnings, "INTRADAY_MARKET_UNAVAILABLE", trendAttempt.failure);

        Map<LocalDateTime, MarketPoint> dailyMarket = Collections.emptyMap();
        if (dailyMarketAttempt.response != null) {
            try { dailyMarket = parseDailyMarket(dailyMarketAttempt.response); }
            catch (Exception error) { dailyMarketAttempt = FetchAttempt.failed(error); }
        }
        if (dailyMarketAttempt.failure != null) addWarning(warnings, "DAILY_MARKET_UNAVAILABLE", dailyMarketAttempt.failure);

        List<CapitalFlowPoint> minutes = Collections.emptyList();
        if (minuteAttempt.response != null) {
            try {
                minutes = parseFlow(minuteAttempt.response, instrument, "MINUTE_1", false, intradayMarket, warnings);
                if (minutes.isEmpty()) minuteAttempt = FetchAttempt.failed(new ProviderContractException("EMPTY_FUND_FLOW", "实时资金流为空", true));
            } catch (Exception error) { minuteAttempt = FetchAttempt.failed(error); }
        }
        if (minuteAttempt.failure != null) addWarning(warnings, "REALTIME_FUND_FLOW_UNAVAILABLE", minuteAttempt.failure);

        List<CapitalFlowPoint> days = Collections.emptyList();
        if (dailyAttempt.response != null) {
            try {
                days = parseFlow(dailyAttempt.response, instrument, "DAY_1", true, dailyMarket, warnings);
                if (days.isEmpty()) dailyAttempt = FetchAttempt.failed(new ProviderContractException("EMPTY_FUND_FLOW", "历史资金流为空", true));
            } catch (Exception error) { dailyAttempt = FetchAttempt.failed(error); }
        }
        if (dailyAttempt.failure != null) addWarning(warnings, "HISTORICAL_FUND_FLOW_UNAVAILABLE", dailyAttempt.failure);

        if (minutes.isEmpty() && days.isEmpty()) {
            ProviderContractException schemaFailure = schemaFailure(minuteAttempt.failure, dailyAttempt.failure);
            if (schemaFailure != null) throw schemaFailure;
            throw new ProviderContractException("ALL_FUND_FLOW_SOURCES_FAILED", "东财实时与历史资金流接口均不可用，请稍后重试", true);
        }

        if (!minutes.isEmpty()) {
            CapitalFlowPoint latestMinute = minutes.get(minutes.size() - 1);
            if (isRecentTradingDay(latestMinute.getDataDate(), asOfDate)) {
                latestMinute.setTurnoverRate(quoteContext.turnoverRate);
                latestMinute.setVolumeRatio(quoteContext.volumeRatio);
                if (quoteAttempt.response != null) mergeProvenance(latestMinute, quoteAttempt.response);
            }
        }

        if (!days.isEmpty()) {
            CapitalFlowPoint latest = days.get(days.size() - 1);
            LocalDate latestMinuteDate = minutes.isEmpty() ? null : minutes.get(minutes.size() - 1).getDataDate();
            boolean quoteMatchesDaily = latestMinuteDate == null || !latestMinuteDate.isAfter(latest.getDataDate());
            if (quoteMatchesDaily && isRecentTradingDay(latest.getDataDate(), asOfDate)) {
                if (latest.getPrice() == null) latest.setPrice(quoteContext.price);
                if (latest.getTradeVolume() == null) latest.setTradeVolume(quoteContext.volume);
                if (latest.getIntervalTradeAmount() == null) latest.setIntervalTradeAmount(quoteContext.amount);
                if (latest.getTurnoverRate() == null) latest.setTurnoverRate(quoteContext.turnoverRate);
                latest.setVolumeRatio(quoteContext.volumeRatio);
                if (quoteAttempt.response != null) mergeProvenance(latest, quoteAttempt.response);
            }
        }
        return new CapitalFlowData(minutes, days, quoteContext.turnoverRate, quoteContext.volumeRatio, warnings, providerCode());
    }

    private FinanceHttpResponse getMinuteFundFlow(String secid) throws Exception {
        return request(REALTIME_HOST, "fflow/kline/get", "secid=" + secid + "&lmt=500&klt=1&fields1=f1,f2,f3,f7&fields2=f51,f52,f53,f54,f55,f56,f57");
    }

    private FinanceHttpResponse getDailyFundFlow(String secid) throws Exception {
        return request(HISTORY_HOST, "fflow/daykline/get", "secid=" + secid + "&lmt=20&klt=101&fields1=f1,f2,f3,f7"
                + "&fields2=f51,f52,f53,f54,f55,f56,f57&ut=" + FUND_FLOW_UT);
    }

    private FinanceHttpResponse getQuote(String secid) throws Exception {
        return request(REALTIME_HOST, "get", "secid=" + secid + "&fields=f43,f47,f48,f50,f168");
    }

    private FinanceHttpResponse getTrend(String secid) throws Exception {
        return request(HISTORY_HOST, "trends2/get", "secid=" + secid + "&ndays=1&fields1=f1,f2,f3,f4,f5,f6,f7,f8&fields2=f51,f52,f53,f54,f55,f56,f57,f58");
    }

    private FinanceHttpResponse getDailyMarket(String secid) throws Exception {
        return request(HISTORY_HOST, "kline/get", "fields1=f1,f2,f3,f4,f5,f6"
                + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61,f116"
                + "&ut=" + EASTMONEY_UT + "&klt=101&fqt=1&secid=" + secid + "&lmt=21&end=20500101");
    }

    private FinanceHttpResponse request(String host, String path, String query) throws Exception {
        return http.get(providerCode(), URI.create(host + path + "?" + query),
                Collections.singletonMap("Referer", "https://quote.eastmoney.com"));
    }

    private JsonNode data(FinanceHttpResponse response) throws Exception {
        JsonNode value = json.readTree(response.getBody()).path("data");
        if (value.isMissingNode() || value.isNull()) throw drift("missing data");
        return value;
    }

    private List<CapitalFlowPoint> parseFlow(FinanceHttpResponse response, Instrument instrument, String granularity,
                                             boolean daily, Map<LocalDateTime, MarketPoint> market, List<String> warnings) throws Exception {
        JsonNode lines = data(response).path("klines");
        if (!lines.isArray()) throw drift("missing klines");
        List<CapitalFlowPoint> result = new ArrayList<CapitalFlowPoint>();
        BigDecimal[] previousCumulativeFlow = null;
        LocalDate previousDate = null;
        for (JsonNode line : lines) {
            String[] fields = line.asText().split(",", -1);
            if (fields.length < 6) throw drift("kline field count");
            try {
                LocalDateTime observed = daily ? LocalDate.parse(fields[0]).atTime(15, 0) : LocalDateTime.parse(fields[0], MINUTE);
                CapitalFlowPoint point = new CapitalFlowPoint();
                point.setInstrumentId(instrument.getId()); point.setProviderCode(providerCode()); point.setGranularity(granularity);
                point.setDataDate(observed.toLocalDate()); point.setObservedAt(observed);
                BigDecimal[] rawFlow = new BigDecimal[]{decimal(fields[1]), decimal(fields[2]), decimal(fields[3]), decimal(fields[4]), decimal(fields[5])};
                if (!daily && (previousDate == null || !previousDate.equals(observed.toLocalDate()))) previousCumulativeFlow = null;
                BigDecimal[] normalizedFlow = daily ? rawFlow : flowDelta(rawFlow, previousCumulativeFlow);
                point.setMainNetInflow(normalizedFlow[0]); point.setSmallNetInflow(normalizedFlow[1]);
                point.setMediumNetInflow(normalizedFlow[2]); point.setLargeNetInflow(normalizedFlow[3]);
                point.setSuperLargeNetInflow(normalizedFlow[4]); point.setCalculationVersion("eastmoney-v3");
                point.setRetrievedAt(LocalDateTime.ofInstant(response.getRetrievedAt(), ZoneId.systemDefault()));
                point.setPayloadHash(response.getPayloadHash()); point.setQualityStatus("COMPLETE");
                MarketPoint context = market.get(observed);
                if (context != null) {
                    point.setPrice(context.price); point.setTradeVolume(context.volume); point.setIntervalTradeAmount(context.amount);
                    point.setCumulativeTradeAmount(context.cumulativeAmount); point.setTurnoverRate(context.turnoverRate);
                    mergeProvenance(point, context);
                } else {
                    point.setQualityStatus("PARTIAL");
                    if (!market.isEmpty()) addWarningOnce(warnings, "TIMELINE_ALIGNMENT_GAP");
                }
                result.add(point);
                if (!daily) { previousCumulativeFlow = rawFlow; previousDate = observed.toLocalDate(); }
            } catch (ProviderContractException error) {
                throw error;
            } catch (Exception error) {
                throw drift("invalid kline value: " + line.asText());
            }
        }
        return result;
    }

    private static BigDecimal[] flowDelta(BigDecimal[] current, BigDecimal[] previous) {
        BigDecimal[] result = new BigDecimal[current.length];
        for (int index = 0; index < current.length; index++) {
            result[index] = current[index] == null ? null
                    : previous == null || previous[index] == null ? current[index] : current[index].subtract(previous[index]);
        }
        return result;
    }

    private Map<LocalDateTime, MarketPoint> parseTrends(FinanceHttpResponse response) throws Exception {
        JsonNode lines = data(response).path("trends");
        Map<LocalDateTime, MarketPoint> values = new HashMap<LocalDateTime, MarketPoint>();
        if (!lines.isArray()) throw drift("missing intraday market trends");
        BigDecimal cumulativeAmount = BigDecimal.ZERO;
        LocalDate currentDate = null;
        for (JsonNode line : lines) {
            String[] fields = line.asText().split(",", -1);
            if (fields.length < 8) throw drift("intraday market field count");
            LocalDateTime observed = LocalDateTime.parse(fields[0], MINUTE);
            if (!observed.toLocalDate().equals(currentDate)) {
                currentDate = observed.toLocalDate();
                cumulativeAmount = BigDecimal.ZERO;
            }
            BigDecimal intervalAmount = decimal(fields[6]);
            if (intervalAmount != null) cumulativeAmount = cumulativeAmount.add(intervalAmount);
            values.put(observed, new MarketPoint(decimal(fields[2]), decimal(fields[5]), intervalAmount,
                    cumulativeAmount, null, response.getPayloadHash(), response.getRetrievedAt()));
        }
        return values;
    }

    private Map<LocalDateTime, MarketPoint> parseDailyMarket(FinanceHttpResponse response) throws Exception {
        JsonNode lines = data(response).path("klines");
        Map<LocalDateTime, MarketPoint> values = new HashMap<LocalDateTime, MarketPoint>();
        if (!lines.isArray()) throw drift("missing daily market klines");
        for (JsonNode line : lines) {
            String[] fields = line.asText().split(",", -1);
            if (fields.length < 11) throw drift("daily market kline field count");
            LocalDateTime observed = LocalDate.parse(fields[0]).atTime(15, 0);
            values.put(observed, new MarketPoint(decimal(fields[2]), decimal(fields[5]), decimal(fields[6]),
                    null, decimal(fields[10]), response.getPayloadHash(), response.getRetrievedAt()));
        }
        return values;
    }

    private static FetchAttempt attempt(HttpRequest request) {
        try { return FetchAttempt.succeeded(request.get()); }
        catch (Exception error) { return FetchAttempt.failed(error); }
    }

    private static void addWarning(List<String> warnings, String code, Exception error) {
        String errorType = error instanceof ProviderContractException
                ? ((ProviderContractException) error).getErrorType() : error.getClass().getSimpleName();
        warnings.add(code + ":" + errorType);
    }

    private static void addWarningOnce(List<String> warnings, String warning) {
        if (!warnings.contains(warning)) warnings.add(warning);
    }

    private static ProviderContractException schemaFailure(Exception first, Exception second) {
        for (Exception error : new Exception[]{first, second}) {
            if (error instanceof ProviderContractException
                    && "SCHEMA_DRIFT".equals(((ProviderContractException) error).getErrorType())) {
                return (ProviderContractException) error;
            }
        }
        return null;
    }

    private static void mergeProvenance(CapitalFlowPoint point, MarketPoint market) {
        point.setPayloadHash(JdkFinanceHttpClient.sha256(point.getPayloadHash() + "|market:" + market.payloadHash));
        point.setRetrievedAt(max(point.getRetrievedAt(), market.retrievedAt));
    }

    private static void mergeProvenance(CapitalFlowPoint point, FinanceHttpResponse response) {
        point.setPayloadHash(JdkFinanceHttpClient.sha256(point.getPayloadHash() + "|quote:" + response.getPayloadHash()));
        point.setRetrievedAt(max(point.getRetrievedAt(), response.getRetrievedAt()));
    }

    private static LocalDateTime max(LocalDateTime current, Instant candidate) {
        LocalDateTime converted = LocalDateTime.ofInstant(candidate, ZoneId.systemDefault());
        return current == null || converted.isAfter(current) ? converted : current;
    }

    private static BigDecimal decimal(String value) {
        return value == null || value.isEmpty() || "-".equals(value) ? null : new BigDecimal(value);
    }

    private static BigDecimal scaled(JsonNode value, int scale) {
        return value == null || !value.isNumber() ? null : new BigDecimal(value.asText()).movePointLeft(scale);
    }

    private static BigDecimal numeric(JsonNode value) {
        return value == null || !value.isNumber() ? null : new BigDecimal(value.asText());
    }

    private static boolean isRecentTradingDay(LocalDate dataDate, LocalDate asOfDate) {
        if (dataDate == null || asOfDate == null || dataDate.isAfter(asOfDate)) return false;
        return ChronoUnit.DAYS.between(dataDate, asOfDate) <= 7;
    }

    private static ProviderContractException drift(String message) {
        return new ProviderContractException("SCHEMA_DRIFT", message, false);
    }

    private interface HttpRequest { FinanceHttpResponse get() throws Exception; }

    private static final class FetchAttempt {
        private final FinanceHttpResponse response;
        private final Exception failure;
        private FetchAttempt(FinanceHttpResponse response, Exception failure) { this.response = response; this.failure = failure; }
        private static FetchAttempt succeeded(FinanceHttpResponse response) { return new FetchAttempt(response, null); }
        private static FetchAttempt failed(Exception error) { return new FetchAttempt(null, error); }
    }

    private static final class MarketPoint {
        private final BigDecimal price, volume, amount, cumulativeAmount, turnoverRate;
        private final String payloadHash;
        private final Instant retrievedAt;
        private MarketPoint(BigDecimal price, BigDecimal volume, BigDecimal amount, BigDecimal cumulativeAmount,
                            BigDecimal turnoverRate, String payloadHash, Instant retrievedAt) {
            this.price = price; this.volume = volume; this.amount = amount; this.cumulativeAmount = cumulativeAmount;
            this.turnoverRate = turnoverRate; this.payloadHash = payloadHash; this.retrievedAt = retrievedAt;
        }
    }

    private static final class QuoteContext {
        private final BigDecimal price, volume, amount, turnoverRate, volumeRatio;
        private QuoteContext(BigDecimal price, BigDecimal volume, BigDecimal amount, BigDecimal turnoverRate, BigDecimal volumeRatio) {
            this.price = price; this.volume = volume; this.amount = amount;
            this.turnoverRate = turnoverRate; this.volumeRatio = volumeRatio;
        }
        private static QuoteContext empty() { return new QuoteContext(null, null, null, null, null); }
    }
}
