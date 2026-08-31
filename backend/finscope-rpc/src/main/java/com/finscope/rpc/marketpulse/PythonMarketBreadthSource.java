package com.finscope.rpc.marketpulse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketBreadthMomentum;
import com.finscope.domain.marketpulse.MarketInternalHistoryPoint;
import com.finscope.domain.marketpulse.MarketNewHighLow;
import com.finscope.domain.marketpulse.MarketReturnDistributionBucket;
import com.finscope.domain.marketpulse.MarketTrendBreadth;
import com.finscope.domain.marketpulse.MarketVolumePressure;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class PythonMarketBreadthSource implements MarketBreadthSource {
    private static final String CLIENT_CODE = "PYTHON_MARKET_BREADTH";
    private static final Set<String> QUALITY_VALUES = Set.of(
            "FRESH_PRIMARY", "FRESH_FALLBACK", "PARTIAL_FRESH", "STALE_FALLBACK");
    private static final Set<String> MOMENTUM_VALUES = Set.of(
            "BULLISH_THRUST", "RECOVERING", "NEUTRAL", "WEAKENING", "UNAVAILABLE");
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
        if (!"market-breadth-v3".equals(text(root, "schema_version"))) {
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
        value.setRetrievedAt(LocalDateTime.parse(
                requiredText(root, "retrieved_at"), DateTimeFormatter.ISO_DATE_TIME));
        value.setAdvanceCount(requiredNonNegativeInteger(root, "advance_count"));
        value.setDeclineCount(requiredNonNegativeInteger(root, "decline_count"));
        value.setFlatCount(requiredNonNegativeInteger(root, "flat_count"));
        value.setValidCount(requiredPositiveInteger(root, "valid_count"));
        value.setAdvanceRatio(requiredRatio(root, "advance_ratio"));
        value.setTotalAmount(requiredNonNegativeNumber(root, "total_amount"));
        value.setLimitUpCount(optionalNonNegativeInteger(root, "limit_up_count"));
        value.setLimitDownCount(optionalNonNegativeInteger(root, "limit_down_count"));
        value.setMedianChangePct(requiredNumber(root, "median_change_pct"));
        value.setReturnDistribution(distribution(root.path("return_distribution"), value.getValidCount()));
        value.setTrendBreadth(trendBreadth(requiredObject(root, "trend_breadth")));
        value.setNewHighLow(newHighLow(requiredObject(root, "new_high_low")));
        value.setNetAdvances(requiredInteger(root, "net_advances"));
        value.setAdvanceDeclineLine(requiredInteger(root, "advance_decline_line"));
        value.setVolumePressure(volumePressure(requiredObject(root, "volume_pressure")));
        value.setBreadthMomentum(breadthMomentum(requiredObject(root, "breadth_momentum")));
        value.setHistory(history(root.path("history"), businessDate));
        value.setWarnings(warnings(root.path("warnings")));
        if (value.getAdvanceCount() + value.getDeclineCount() + value.getFlatCount()
                != value.getValidCount()) {
            throw contract("MARKET_BREADTH_COUNT_MISMATCH", "market breadth counts do not sum to valid count", false);
        }
        return value;
    }

    private MarketVolumePressure volumePressure(JsonNode node) {
        MarketVolumePressure value = new MarketVolumePressure();
        value.setAdvanceAmount(requiredNonNegativeNumber(node, "advance_amount"));
        value.setDeclineAmount(requiredNonNegativeNumber(node, "decline_amount"));
        value.setFlatAmount(requiredNonNegativeNumber(node, "flat_amount"));
        value.setAdvanceAmountRatio(optionalRatio(node, "advance_amount_ratio"));
        value.setNetAdvancingAmount(requiredNumber(node, "net_advancing_amount"));
        value.setTrin(optionalNonNegativeNumber(node, "trin"));
        return value;
    }

    private MarketBreadthMomentum breadthMomentum(JsonNode node) {
        MarketBreadthMomentum value = new MarketBreadthMomentum();
        value.setMcclellanOscillator(optionalNumber(node, "mcclellan_oscillator"));
        value.setBreadthThrustRatio(optionalRatio(node, "breadth_thrust_ratio"));
        value.setStatus(requiredText(node, "status"));
        if (!MOMENTUM_VALUES.contains(value.getStatus())) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT",
                    "unsupported breadth momentum status", false);
        }
        if (!"UNAVAILABLE".equals(value.getStatus())
                && (value.getMcclellanOscillator() == null
                || value.getBreadthThrustRatio() == null)) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT",
                    "available breadth momentum requires numeric values", false);
        }
        return value;
    }

    private List<MarketReturnDistributionBucket> distribution(JsonNode rows, int validCount) {
        if (!rows.isArray() || rows.size() != 7) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT",
                    "return_distribution must contain seven buckets", false);
        }
        List<MarketReturnDistributionBucket> values = new ArrayList<>();
        int count = 0;
        for (JsonNode row : rows) {
            MarketReturnDistributionBucket value = new MarketReturnDistributionBucket();
            value.setCode(requiredText(row, "code"));
            value.setLabel(requiredText(row, "label"));
            value.setLowerBound(optionalNumber(row, "lower_bound"));
            value.setUpperBound(optionalNumber(row, "upper_bound"));
            value.setCount(requiredNonNegativeInteger(row, "count"));
            value.setRatio(requiredRatio(row, "ratio"));
            count += value.getCount();
            values.add(value);
        }
        if (count != validCount) {
            throw contract("MARKET_BREADTH_COUNT_MISMATCH",
                    "return distribution does not sum to valid count", false);
        }
        return values;
    }

    private MarketTrendBreadth trendBreadth(JsonNode node) {
        MarketTrendBreadth value = new MarketTrendBreadth();
        value.setMa20Ratio(optionalRatio(node, "ma20_ratio"));
        value.setMa20ValidCount(requiredNonNegativeInteger(node, "ma20_valid_count"));
        value.setMa60Ratio(optionalRatio(node, "ma60_ratio"));
        value.setMa60ValidCount(requiredNonNegativeInteger(node, "ma60_valid_count"));
        value.setMa120Ratio(optionalRatio(node, "ma120_ratio"));
        value.setMa120ValidCount(requiredNonNegativeInteger(node, "ma120_valid_count"));
        value.setMa250Ratio(optionalRatio(node, "ma250_ratio"));
        value.setMa250ValidCount(requiredNonNegativeInteger(node, "ma250_valid_count"));
        return value;
    }

    private MarketNewHighLow newHighLow(JsonNode node) {
        MarketNewHighLow value = new MarketNewHighLow();
        value.setHigh20Count(requiredNonNegativeInteger(node, "high20_count"));
        value.setLow20Count(requiredNonNegativeInteger(node, "low20_count"));
        value.setValid20Count(requiredNonNegativeInteger(node, "valid20_count"));
        value.setHigh60Count(requiredNonNegativeInteger(node, "high60_count"));
        value.setLow60Count(requiredNonNegativeInteger(node, "low60_count"));
        value.setValid60Count(requiredNonNegativeInteger(node, "valid60_count"));
        value.setHigh250Count(requiredNonNegativeInteger(node, "high250_count"));
        value.setLow250Count(requiredNonNegativeInteger(node, "low250_count"));
        value.setValid250Count(requiredNonNegativeInteger(node, "valid250_count"));
        validateHighLow(value.getHigh20Count(), value.getLow20Count(), value.getValid20Count(), "20");
        validateHighLow(value.getHigh60Count(), value.getLow60Count(), value.getValid60Count(), "60");
        validateHighLow(value.getHigh250Count(), value.getLow250Count(), value.getValid250Count(), "250");
        return value;
    }

    private void validateHighLow(int high, int low, int valid, String window) {
        if (high > valid || low > valid) {
            throw contract("MARKET_BREADTH_COUNT_MISMATCH",
                    window + " day high/low count exceeds valid count", false);
        }
    }

    private List<MarketInternalHistoryPoint> history(JsonNode rows, LocalDate requested) {
        if (!rows.isArray()) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", "history must be an array", false);
        }
        if (rows.size() > 60) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", "history cannot exceed sixty points", false);
        }
        List<MarketInternalHistoryPoint> values = new ArrayList<>();
        LocalDate previousDate = null;
        for (JsonNode row : rows) {
            MarketInternalHistoryPoint value = historyPoint(row);
            if (value.getBusinessDate().isAfter(requested)
                    || previousDate != null && !value.getBusinessDate().isAfter(previousDate)) {
                throw contract("MARKET_BREADTH_DATE_MISMATCH",
                        "history dates must be ascending and bounded by business date", false);
            }
            previousDate = value.getBusinessDate();
            values.add(value);
        }
        return values;
    }

    private MarketInternalHistoryPoint historyPoint(JsonNode row) {
        MarketInternalHistoryPoint value = new MarketInternalHistoryPoint();
        value.setBusinessDate(LocalDate.parse(requiredText(row, "business_date")));
        value.setAdvanceCount(requiredNonNegativeInteger(row, "advance_count"));
        value.setDeclineCount(requiredNonNegativeInteger(row, "decline_count"));
        value.setFlatCount(requiredNonNegativeInteger(row, "flat_count"));
        value.setValidCount(requiredPositiveInteger(row, "valid_count"));
        if (value.getAdvanceCount() + value.getDeclineCount() + value.getFlatCount()
                != value.getValidCount()) {
            throw contract("MARKET_BREADTH_COUNT_MISMATCH",
                    "history counts do not sum to valid count", false);
        }
        value.setAdvanceRatio(requiredRatio(row, "advance_ratio"));
        value.setTotalAmount(requiredNonNegativeNumber(row, "total_amount"));
        value.setMedianChangePct(requiredNumber(row, "median_change_pct"));
        value.setMa20Ratio(optionalRatio(row, "ma20_ratio"));
        value.setMa60Ratio(optionalRatio(row, "ma60_ratio"));
        value.setMa120Ratio(optionalRatio(row, "ma120_ratio"));
        value.setMa250Ratio(optionalRatio(row, "ma250_ratio"));
        value.setNewHigh20Count(requiredNonNegativeInteger(row, "new_high20_count"));
        value.setNewLow20Count(requiredNonNegativeInteger(row, "new_low20_count"));
        value.setNewHigh60Count(requiredNonNegativeInteger(row, "new_high60_count"));
        value.setNewLow60Count(requiredNonNegativeInteger(row, "new_low60_count"));
        value.setNewHigh250Count(requiredNonNegativeInteger(row, "new_high250_count"));
        value.setNewLow250Count(requiredNonNegativeInteger(row, "new_low250_count"));
        value.setNetAdvances(requiredInteger(row, "net_advances"));
        value.setAdvanceDeclineLine(requiredInteger(row, "advance_decline_line"));
        value.setAdvanceAmount(requiredNonNegativeNumber(row, "advance_amount"));
        value.setDeclineAmount(requiredNonNegativeNumber(row, "decline_amount"));
        value.setFlatAmount(requiredNonNegativeNumber(row, "flat_amount"));
        value.setAdvanceAmountRatio(optionalRatio(row, "advance_amount_ratio"));
        value.setNetAdvancingAmount(requiredNumber(row, "net_advancing_amount"));
        value.setTrin(optionalNonNegativeNumber(row, "trin"));
        value.setMcclellanOscillator(requiredNumber(row, "mcclellan_oscillator"));
        value.setBreadthThrustRatio(requiredRatio(row, "breadth_thrust_ratio"));
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

    private Integer requiredInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", field + " must be an integer", false);
        }
        return value.asInt();
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

    private Double optionalRatio(JsonNode node, String field) {
        Double value = optionalNumber(node, field);
        if (value != null && (value < 0D || value > 1D)) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT",
                    field + " must be between zero and one", false);
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

    private Double optionalNonNegativeNumber(JsonNode node, String field) {
        Double value = optionalNumber(node, field);
        if (value != null && value < 0D) {
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

    private Double optionalNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", field + " must be numeric", false);
        }
        return value.asDouble();
    }

    private JsonNode requiredObject(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isObject()) {
            throw contract("MARKET_BREADTH_SCHEMA_DRIFT", field + " must be an object", false);
        }
        return value;
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
