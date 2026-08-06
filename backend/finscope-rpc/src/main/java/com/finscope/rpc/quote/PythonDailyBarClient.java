package com.finscope.rpc.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.DailyBarPoint;
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
import java.util.Locale;

/**
 * 从本地 Python market-data-service 读取日 K 线，供自选页标的展示。
 *
 * <p>与量化专用的 {@code PythonQuantDailyBarSource} 不同，这里不做 QFQ 复权、
 * as_of 一致性等严格校验，只透传 Python 侧已聚合的日线记录；上游不可用时按
 * {@link ProviderContractException} 语义抛出，由 service 层兜底为明确失败而非空数据。</p>
 */
@Component
public class PythonDailyBarClient {
    private static final String CLIENT_CODE = "PYTHON_DAILY_BARS";

    private final String baseUrl;
    private final FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public PythonDailyBarClient(
            FinanceHttpClient http,
            @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}") String baseUrl) {
        this(trimTrailingSlash(baseUrl), http);
    }

    PythonDailyBarClient(String baseUrl, FinanceHttpClient http) {
        this.baseUrl = baseUrl;
        this.http = http;
    }

    /**
     * 获取标的最近 {@code limit} 根日 K 线。
     *
     * @param code  六位证券代码，如 600519。
     * @param limit 请求根数，限制在 [1, 250]。
     * @return 按交易日升序排列的日线记录；上游不可用时抛出异常。
     */
    public List<DailyBarPoint> fetchDailyBars(String code, int limit) {
        return fetchDailyBars(code, limit, false);
    }

    /**
     * 获取日 K 线，并可显式要求 Python 服务刷新其持久快照。
     */
    public List<DailyBarPoint> fetchDailyBars(String code, int limit, boolean refresh) {
        String normalizedCode = normalizeCode(code);
        String market = market(normalizedCode);
        int normalizedLimit = Math.max(1, Math.min(limit, 250));
        URI uri = URI.create(baseUrl + "/v1/stocks/" + market + "/" + normalizedCode
                + "/daily-bars?limit=" + normalizedLimit + (refresh ? "&refresh=true" : ""));
        try {
            FinanceHttpResponse response = http.get(CLIENT_CODE, uri, Collections.<String, String>emptyMap());
            return parse(response, normalizedCode, market);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("PYTHON_SERVICE_ERROR",
                    "Python market data service failed: " + message(error), true, error);
        }
    }

    private List<DailyBarPoint> parse(FinanceHttpResponse response, String code, String market) throws Exception {
        JsonNode root = json.readTree(response.getBody());
        String quality = text(root, "quality_status");
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty() || "UNAVAILABLE".equals(quality)) {
            throw new ProviderContractException("UPSTREAM_UNAVAILABLE", "daily bars are unavailable", true);
        }
        List<DailyBarPoint> bars = new ArrayList<DailyBarPoint>();
        for (JsonNode row : data) {
            DailyBarPoint bar = map(row, code, market);
            bars.add(bar);
        }
        return bars;
    }

    private static DailyBarPoint map(JsonNode row, String code, String market) {
        DailyBarPoint bar = new DailyBarPoint();
        bar.setCode(code);
        bar.setMarket(market);
        bar.setTradeDate(parseDate(row, "trade_date"));
        bar.setOpen(decimal(row, "open"));
        bar.setHigh(decimal(row, "high"));
        bar.setLow(decimal(row, "low"));
        bar.setClose(decimal(row, "close"));
        bar.setVolume(decimal(row, "volume"));
        bar.setAmount(decimalOrNull(row, "amount"));
        bar.setAmplitude(decimalOrNull(row, "amplitude"));
        bar.setChangePct(decimalOrNull(row, "change_pct"));
        bar.setTurnoverRate(decimalOrNull(row, "turnover_rate"));
        return bar;
    }

    private static LocalDate parseDate(JsonNode row, String field) {
        String value = text(row, field);
        if (value == null || value.trim().isEmpty()) {
            throw new ProviderContractException("SCHEMA_DRIFT", "daily bar is missing " + field, false);
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException error) {
            throw new ProviderContractException("SCHEMA_DRIFT", "daily bar has an invalid " + field, false, error);
        }
    }

    private static BigDecimal decimal(JsonNode row, String field) {
        BigDecimal value = decimalOrNull(row, field);
        if (value == null) {
            throw new ProviderContractException("SCHEMA_DRIFT", "daily bar is missing numeric field " + field, false);
        }
        return value;
    }

    private static BigDecimal decimalOrNull(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isNumber()) {
            return null;
        }
        return value.decimalValue();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) return null;
        JsonNode value = node.get(field);
        return value != null && value.isValueNode() && !value.isNull() ? value.asText() : null;
    }

    private static String normalizeCode(String code) {
        if (code == null) return null;
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("sh") || normalized.startsWith("sz") || normalized.startsWith("bj")) {
            normalized = normalized.substring(2);
        }
        return normalized.matches("\\d{6}") ? normalized : null;
    }

    private static String market(String code) {
        if (code == null) return "SZ";
        if (code.startsWith("6") || code.startsWith("5") || code.startsWith("9")) return "SH";
        if (code.startsWith("4") || code.startsWith("8")) return "BJ";
        return "SZ";
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
}
