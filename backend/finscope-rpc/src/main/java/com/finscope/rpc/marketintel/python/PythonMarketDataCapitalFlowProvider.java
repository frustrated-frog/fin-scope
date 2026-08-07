package com.finscope.rpc.marketintel.python;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.CapitalFlowData;
import com.finscope.rpc.marketintel.CapitalFlowProvider;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * FinScope Python 市场数据服务的 Java 侧资金流 Provider。
 * Java 只依赖稳定 JSON 契约，不感知 AkShare、腾讯或东方财富的具体字段。
 */
@Component
public class PythonMarketDataCapitalFlowProvider implements CapitalFlowProvider {
    private static final Set<MarketDataCapability> CAPABILITIES = Collections.singleton(
            MarketDataCapability.CAPITAL_FLOW_5M);

    private final String baseUrl;
    private final FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public PythonMarketDataCapitalFlowProvider(
            FinanceHttpClient http,
            @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}") String baseUrl) {
        this(baseUrl, http);
    }

    PythonMarketDataCapitalFlowProvider(String baseUrl, FinanceHttpClient http) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.http = http;
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) return null;
        return node.decimalValue();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private static String defaultText(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static void appendWarnings(List<String> target, JsonNode values) {
        if (!values.isArray()) return;
        for (JsonNode value : values) {
            if (value.isTextual() && !value.asText().trim().isEmpty()) target.add(value.asText());
        }
    }

    private static LocalDateTime parseTime(String value, Instant fallback) {
        if (value != null && !value.trim().isEmpty()) {
            try {
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDateTime.parse(value);
                } catch (DateTimeParseException ignoredAgain) {
                    // Fall through to the HTTP retrieval time.
                }
            }
        }
        return LocalDateTime.ofInstant(fallback, ZoneId.systemDefault());
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.trim().isEmpty()) return "http://127.0.0.1:8000";
        String normalized = value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    @Override
    public String providerCode() {
        return "PYTHON_MARKET_DATA";
    }

    @Override
    public String providerFamily() {
        return "FINSCOPE_PYTHON_SERVICE";
    }

    @Override
    public Set<MarketDataCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public int batchLimit() {
        return 20;
    }

    @Override
    public Duration minimumInterval() {
        return Duration.ofMillis(100);
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(15);
    }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument != null && "STOCK".equals(instrument.getType())
                && ("SH".equals(instrument.getMarket()) || "SZ".equals(instrument.getMarket())
                || "BJ".equals(instrument.getMarket()));
    }

    @Override
    public CapitalFlowData fetch(Instrument instrument, LocalDate asOfDate) {
        if (!supports(instrument)) {
            throw new ProviderContractException(
                    "PYTHON_PROVIDER_UNSUPPORTED",
                    "Python market data provider does not support this instrument",
                    false);
        }
        URI uri = URI.create(baseUrl + "/v1/stocks/" + instrument.getMarket() + "/"
                + instrument.getCode() + "/capital-flow?require_minute=false");
        try {
            FinanceHttpResponse response = http.get(providerCode(), uri, Collections.<String, String>emptyMap());
            return parse(response, instrument);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException(
                    "PYTHON_SERVICE_ERROR",
                    "Python market data service failed: " + message(error),
                    true);
        }
    }

    private CapitalFlowData parse(FinanceHttpResponse response, Instrument instrument) throws Exception {
        JsonNode root = json.readTree(response.getBody());
        String quality = text(root, "quality_status");
        if ("STALE_FALLBACK".equals(quality)) {
            throw new ProviderContractException(
                    "PYTHON_STALE_FALLBACK",
                    "Python market data service only returned an expired capital-flow snapshot",
                    true);
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull() || !data.isObject()) {
            throw new ProviderContractException(
                    "PYTHON_SCHEMA_DRIFT", "Python market data response has no data object", false);
        }
        boolean stale = false;
        LocalDateTime retrievedAt = parseTime(text(root, "retrieved_at"), response.getRetrievedAt());
        List<CapitalFlowPoint> minutePoints = parsePoints(
                data.path("minute_points"), instrument, response, retrievedAt, stale);
        List<CapitalFlowPoint> dailyPoints = parsePoints(
                data.path("daily_points"), instrument, response, retrievedAt, stale);
        if (minutePoints.isEmpty() && dailyPoints.isEmpty()) {
            throw new ProviderContractException(
                    "EMPTY_CAPITAL_FLOW", "Python market data service returned no capital flow points", true);
        }
        List<String> warnings = new ArrayList<String>();
        appendWarnings(warnings, root.path("warnings"));
        appendWarnings(warnings, data.path("warnings"));
        String source = text(root, "source_code");
        if (source != null) warnings.add("source:" + source);
        if (stale) warnings.add("PYTHON_STALE_FALLBACK");
        if ("FRESH_FALLBACK".equals(quality)) {
            warnings.add("PYTHON_SOURCE_FALLBACK");
        }
        return new CapitalFlowData(
                minutePoints,
                dailyPoints,
                decimal(data.get("turnover_rate")),
                decimal(data.get("volume_ratio")),
                warnings,
                providerCode());
    }

    private List<CapitalFlowPoint> parsePoints(JsonNode nodes, Instrument instrument,
                                               FinanceHttpResponse response,
                                               LocalDateTime retrievedAt,
                                               boolean stale) {
        if (!nodes.isArray()) return Collections.emptyList();
        List<CapitalFlowPoint> result = new ArrayList<CapitalFlowPoint>();
        for (JsonNode node : nodes) {
            String observedText = text(node, "observed_at");
            if (observedText == null) {
                throw new ProviderContractException(
                        "PYTHON_SCHEMA_DRIFT", "capital flow point has no observed_at", false);
            }
            LocalDateTime observedAt = parseTime(observedText, response.getRetrievedAt());
            CapitalFlowPoint point = new CapitalFlowPoint();
            point.setInstrumentId(instrument.getId());
            point.setProviderCode(providerCode());
            point.setGranularity(text(node, "granularity"));
            point.setObservedAt(observedAt);
            point.setDataDate(observedAt.toLocalDate());
            point.setPrice(decimal(node.get("price")));
            point.setTradeVolume(decimal(node.get("volume")));
            point.setIntervalTradeAmount(decimal(node.get("amount")));
            point.setTurnoverRate(decimal(node.get("turnover_rate")));
            point.setVolumeRatio(decimal(node.get("volume_ratio")));
            point.setMainNetInflow(decimal(node.get("main_net_inflow")));
            point.setSuperLargeNetInflow(decimal(node.get("super_large_net_inflow")));
            point.setLargeNetInflow(decimal(node.get("large_net_inflow")));
            point.setMediumNetInflow(decimal(node.get("medium_net_inflow")));
            point.setSmallNetInflow(decimal(node.get("small_net_inflow")));
            point.setCalculationVersion("python-market-data-v1");
            point.setRetrievedAt(retrievedAt);
            point.setPayloadHash(response.getPayloadHash());
            point.setQualityStatus(stale ? "STALE" : defaultText(node, "quality_status", "COMPLETE"));
            result.add(point);
        }
        return result;
    }

}
