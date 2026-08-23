package com.finscope.rpc.marketpulse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class PythonMarketBreadthSource implements MarketBreadthSource {
    private static final String CLIENT_CODE = "PYTHON_MARKET_BREADTH";
    private static final Set<String> QUALITY_VALUES = Set.of(
            "FRESH_PRIMARY", "FRESH_FALLBACK", "PARTIAL_FRESH", "STALE_FALLBACK");
    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    @Resource
    private FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public MarketBreadthSnapshot fetch(LocalDate businessDate) {
        URI uri = URI.create(trimTrailingSlash(baseUrl)
                + "/v1/markets/CN-A/breadth?business_date=" + businessDate);
        try {
            FinanceHttpResponse response = http.get(
                    CLIENT_CODE, uri, Collections.<String, String>emptyMap());
            return parse(response, businessDate);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw contract("PYTHON_MARKET_BREADTH_ERROR", message(error), true, error);
        }
    }

    private MarketBreadthSnapshot parse(FinanceHttpResponse response, LocalDate requested) throws Exception {
        JsonNode root = json.readTree(response.getBody());
        if (!"market-breadth-v1".equals(text(root, "schema_version"))) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", "unsupported market breadth schema", false);
        }
        if (!"CN-A".equals(text(root, "market"))) {
            throw contract("MARKET_BREADTH_MARKET_DRIFT", "market breadth must describe CN-A", false);
        }
        LocalDate businessDate = LocalDate.parse(requiredText(root, "business_date"));
        if (!requested.equals(businessDate)) {
            throw contract("MARKET_BREADTH_DATE_MISMATCH", "market breadth business date mismatch", false);
        }
        String quality = requiredText(root, "quality_status");
        if (!QUALITY_VALUES.contains(quality)) {
            throw contract("MARKET_BREADTH_QUALITY_DRIFT", "unsupported market breadth quality", false);
        }
        MarketBreadthSnapshot value = new MarketBreadthSnapshot();
        value.setBusinessDate(businessDate);
        value.setSourceCode(requiredText(root, "source_code"));
        value.setSourceFamily(requiredText(root, "source_family"));
        value.setQualityStatus(quality);
        value.setRetrievedAt(OffsetDateTime.parse(requiredText(root, "retrieved_at")).toLocalDateTime());
        value.setAdvanceCount(requiredNonNegativeInteger(root, "advance_count"));
        value.setDeclineCount(requiredNonNegativeInteger(root, "decline_count"));
        value.setFlatCount(requiredNonNegativeInteger(root, "flat_count"));
        value.setValidCount(requiredPositiveInteger(root, "valid_count"));
        value.setAdvanceRatio(requiredRatio(root, "advance_ratio"));
        value.setTotalAmount(requiredNonNegativeNumber(root, "total_amount"));
        value.setLimitUpCount(optionalNonNegativeInteger(root, "limit_up_count"));
        value.setLimitDownCount(optionalNonNegativeInteger(root, "limit_down_count"));
        value.setMedianChangePct(requiredNumber(root, "median_change_pct"));
        value.setWarnings(warnings(root.path("warnings")));
        if (value.getAdvanceCount() + value.getDeclineCount() + value.getFlatCount()
                != value.getValidCount()) {
            throw contract("MARKET_BREADTH_COUNT_MISMATCH", "market breadth counts do not sum to valid count", false);
        }
        return value;
    }

    private Integer requiredNonNegativeInteger(JsonNode node, String field) {
        Integer value = optionalNonNegativeInteger(node, field);
        if (value == null) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", field + " must be a non-negative integer", false);
        }
        return value;
    }

    private Integer requiredPositiveInteger(JsonNode node, String field) {
        Integer value = requiredNonNegativeInteger(node, field);
        if (value < 1) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", field + " must be positive", false);
        }
        return value;
    }

    private Integer optionalNonNegativeInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || value.asInt() < 0) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", field + " must be a non-negative integer", false);
        }
        return value.asInt();
    }

    private Double requiredRatio(JsonNode node, String field) {
        double value = requiredNumber(node, field);
        if (value < 0D || value > 1D) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", field + " must be between zero and one", false);
        }
        return value;
    }

    private Double requiredNonNegativeNumber(JsonNode node, String field) {
        double value = requiredNumber(node, field);
        if (value < 0D) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", field + " must be non-negative", false);
        }
        return value;
    }

    private Double requiredNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", field + " must be numeric", false);
        }
        return value.asDouble();
    }

    private List<String> warnings(JsonNode rows) {
        List<String> values = new ArrayList<>();
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                if (row.isTextual() && !row.asText().trim().isEmpty()) {
                    values.add(row.asText().trim());
                }
            }
        }
        return values;
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", "market breadth is missing " + field, false);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isValueNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null || value.trim().isEmpty()
                ? "http://127.0.0.1:8000" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String message(Exception error) {
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
