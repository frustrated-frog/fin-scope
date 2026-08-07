package com.finscope.rpc.quant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps the Python market-data envelope to the strict quant daily-bar contract. */
@Component
public class PythonQuantDailyBarSource implements QuantDailyBarSource {
    private static final String CLIENT_CODE = "PYTHON_QUANT_DAILY_BARS";
    private static final Pattern INSTRUMENT = Pattern.compile("^(\\d{6})\\.(SH|SZ|BJ)$");

    private final String baseUrl;
    private final FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public PythonQuantDailyBarSource(
            @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}") String baseUrl,
            FinanceHttpClient http) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.http = http;
    }

    @Override
    public QuantDailyBarBatch fetch(String instrumentCode, int limit) {
        Matcher matcher = INSTRUMENT.matcher(instrumentCode == null ? "" : instrumentCode.trim());
        if (!matcher.matches()) {
            throw contract("INVALID_INSTRUMENT", "instrument code must use 600519.SH format", false);
        }
        int normalizedLimit = Math.max(1, Math.min(limit, 5000));
        URI uri = URI.create(baseUrl + "/v1/stocks/" + matcher.group(2) + "/"
                + matcher.group(1) + "/daily-bars?limit=" + normalizedLimit);
        try {
            FinanceHttpResponse response = http.get(
                    CLIENT_CODE, uri, Collections.<String, String>emptyMap());
            return parse(response, instrumentCode.trim(), matcher.group(1), matcher.group(2));
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw contract("PYTHON_SERVICE_ERROR",
                    "Python market data service failed: " + message(error), true, error);
        }
    }

    private QuantDailyBarBatch parse(FinanceHttpResponse response, String instrumentCode,
                                     String expectedCode, String expectedMarket) throws Exception {
        JsonNode root = json.readTree(response.getBody());
        assertSymbol(root.path("symbol"), expectedCode, expectedMarket);
        String quality = requiredText(root, "quality_status");
        if ("UNAVAILABLE".equals(quality) || root.path("data").isNull()) {
            throw contract("UPSTREAM_UNAVAILABLE", "daily bars are unavailable", true);
        }
        JsonNode rows = root.path("data");
        if (!rows.isArray() || rows.isEmpty()) {
            throw contract("EMPTY_DAILY_BARS", "daily-bar response contains no rows", true);
        }
        List<QuantDailyBar> bars = new ArrayList<QuantDailyBar>();
        for (JsonNode row : rows) {
            assertSymbol(row.path("symbol"), expectedCode, expectedMarket);
            if (!"QFQ".equals(requiredText(row, "adjustment"))) {
                throw contract("UNSUPPORTED_ADJUSTMENT",
                        "quant research requires QFQ adjusted daily bars", false);
            }
            QuantDailyBar bar = mapBar(row, instrumentCode);
            assertValidBar(bar);
            bars.add(bar);
        }
        LocalDate asOf = parseAsOf(requiredText(root, "as_of"));
        LocalDate lastTradeDate = bars.stream().map(QuantDailyBar::getTradeDate)
                .max(LocalDate::compareTo).orElseThrow(() ->
                        contract("EMPTY_DAILY_BARS", "daily-bar response contains no rows", true));
        if (!lastTradeDate.equals(asOf)) {
            throw contract("AS_OF_MISMATCH", "daily-bar as_of does not match the last trade date", false);
        }
        return new QuantDailyBarBatch(
                bars,
                requiredText(root, "source_code"),
                requiredText(root, "source_family"),
                quality,
                asOf,
                warnings(root.path("warnings")));
    }

    private static QuantDailyBar mapBar(JsonNode row, String instrumentCode) {
        QuantDailyBar bar = new QuantDailyBar();
        bar.setInstrumentCode(instrumentCode);
        try {
            bar.setTradeDate(LocalDate.parse(requiredText(row, "trade_date")));
        } catch (DateTimeParseException error) {
            throw contract("SCHEMA_DRIFT", "daily bar has an invalid trade_date", false, error);
        }
        bar.setOpen(decimal(row, "open"));
        bar.setHigh(decimal(row, "high"));
        bar.setLow(decimal(row, "low"));
        bar.setClose(decimal(row, "close"));
        bar.setAdjustedClose(bar.getClose());
        bar.setVolume(decimal(row, "volume"));
        bar.setAmount(decimal(row, "amount"));
        bar.setTradeStatus("TRADING");
        return bar;
    }

    private static void assertValidBar(QuantDailyBar bar) {
        if (!positive(bar.getOpen()) || !positive(bar.getHigh()) || !positive(bar.getLow())
                || !positive(bar.getClose()) || bar.getVolume() == null || bar.getVolume().signum() < 0
                || bar.getAmount() == null || bar.getAmount().signum() < 0) {
            throw contract("INVALID_DAILY_BAR", "daily bar contains invalid price, volume or amount", false);
        }
        BigDecimal top = bar.getOpen().max(bar.getClose()).max(bar.getLow());
        BigDecimal bottom = bar.getOpen().min(bar.getClose()).min(bar.getHigh());
        if (bar.getHigh().compareTo(top) < 0 || bar.getLow().compareTo(bottom) > 0) {
            throw contract("INVALID_OHLC", "daily bar contains invalid OHLC", false);
        }
    }

    private static void assertSymbol(JsonNode symbol, String code, String market) {
        if (!code.equals(text(symbol, "code")) || !market.equals(text(symbol, "market"))) {
            throw contract("SYMBOL_MISMATCH", "daily-bar response symbol does not match request", false);
        }
    }

    private static LocalDate parseAsOf(String value) {
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException error) {
            throw contract("SCHEMA_DRIFT", "daily-bar response has an invalid as_of", false, error);
        }
    }

    private static List<String> warnings(JsonNode node) {
        if (!node.isArray()) return Collections.emptyList();
        List<String> values = new ArrayList<String>();
        for (JsonNode value : node) if (value.isTextual()) values.add(value.asText());
        return values;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw contract("SCHEMA_DRIFT", "daily bar is missing numeric field " + field, false);
        }
        return value.decimalValue();
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.trim().isEmpty()) {
            throw contract("SCHEMA_DRIFT", "daily-bar response is missing " + field, false);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) return null;
        JsonNode value = node.get(field);
        return value != null && value.isValueNode() && !value.isNull() ? value.asText() : null;
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value == null || value.trim().isEmpty()
                ? "http://127.0.0.1:8000" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static ProviderContractException contract(String type, String message, boolean retryable) {
        return new ProviderContractException(type, message, retryable);
    }

    private static ProviderContractException contract(String type, String message, boolean retryable,
                                                       Throwable cause) {
        return new ProviderContractException(type, message, retryable, cause);
    }
}
