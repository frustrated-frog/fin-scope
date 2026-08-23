package com.finscope.rpc.marketpulse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketpulse.SectorHistoryItem;
import com.finscope.domain.marketpulse.SectorHistorySnapshot;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 从 Python 行情服务读取同花顺全行业历史，并隔离外部 JSON 契约。 */
@Component
public class PythonSectorHistorySource implements SectorHistorySource {
    private static final String CLIENT_CODE = "PYTHON_SECTOR_HISTORY";
    private static final String SCHEMA_VERSION = "sector-history-v1";
    private static final String SOURCE_FAMILY = "TONGHUASHUN";
    private static final String SOURCE_CODE = "AKSHARE_TONGHUASHUN_SECTOR_HISTORY";
    private static final Pattern SECTOR_CODE = Pattern.compile("\\d{6}");
    private static final Set<String> QUALITY_VALUES = Set.of("FRESH_PRIMARY", "PARTIAL_FRESH");
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int REQUEST_TIMEOUT_MS = 180_000;

    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    @Resource
    private FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public SectorHistorySnapshot fetch(LocalDate businessDate, int window) {
        int boundedWindow = Math.max(20, Math.min(window, 120));
        URI uri = URI.create(trimTrailingSlash(baseUrl) + "/v1/sectors/INDUSTRY/history"
                + "?business_date=" + businessDate + "&window=" + boundedWindow);
        try {
            FinanceHttpResponse response = http.get(CLIENT_CODE, uri,
                    Collections.<String, String>emptyMap(), MAX_RESPONSE_BYTES, REQUEST_TIMEOUT_MS);
            return parse(response, businessDate, boundedWindow);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw contract("SECTOR_HISTORY_FETCH_FAILED", message(error), true, error);
        }
    }

    private SectorHistorySnapshot parse(FinanceHttpResponse response, LocalDate requestedDate,
                                        int requestedWindow) throws Exception {
        JsonNode root = json.readTree(response.getBody());
        if (!SCHEMA_VERSION.equals(text(root, "schema_version"))) {
            throw contract("SECTOR_HISTORY_SCHEMA_DRIFT", "unsupported sector history schema", false);
        }
        if (!SOURCE_FAMILY.equals(text(root, "source_family"))
                || !SOURCE_CODE.equals(text(root, "source_code"))
                || !"INDUSTRY".equals(text(root, "category"))) {
            throw contract("SECTOR_HISTORY_SOURCE_DRIFT", "sector history must describe Tonghuashun industries", false);
        }
        LocalDate businessDate = LocalDate.parse(requiredText(root, "business_date"));
        if (!requestedDate.equals(businessDate)) {
            throw contract("SECTOR_HISTORY_DATE_MISMATCH", "sector history business date mismatch", false);
        }
        int window = requiredInteger(root, "requested_window", 20, 120);
        if (window != requestedWindow) {
            throw contract("SECTOR_HISTORY_WINDOW_MISMATCH", "sector history window mismatch", false);
        }
        String quality = requiredText(root, "quality_status");
        if (!QUALITY_VALUES.contains(quality)) {
            throw contract("SECTOR_HISTORY_QUALITY_DRIFT", "unsupported sector history quality", false);
        }
        SectorHistorySnapshot value = new SectorHistorySnapshot();
        value.setBusinessDate(businessDate);
        value.setSourceCode(SOURCE_CODE);
        value.setSourceFamily(SOURCE_FAMILY);
        value.setQualityStatus(quality);
        value.setRetrievedAt(LocalDateTime.parse(requiredText(root, "retrieved_at")));
        value.setRequestedWindow(window);
        value.setCoveredTradeDates(dates(root.path("covered_trade_dates"), businessDate));
        value.setEntries(entries(root.path("entries"), businessDate));
        value.setWarnings(warnings(root.path("warnings")));
        if (value.getEntries().isEmpty()) {
            throw contract("EMPTY_SECTOR_HISTORY", "sector history contains no valid entries", true);
        }
        return value;
    }

    private List<SectorHistoryItem> entries(JsonNode rows, LocalDate businessDate) {
        if (!rows.isArray()) {
            throw contract("SECTOR_HISTORY_SCHEMA_DRIFT", "sector history entries must be an array", false);
        }
        List<SectorHistoryItem> values = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (JsonNode row : rows) {
            String code = requiredText(row, "code");
            String name = requiredText(row, "name");
            if (!SECTOR_CODE.matcher(code).matches() || !codes.add(code)) {
                throw contract("SECTOR_HISTORY_ENTRY_DRIFT", "invalid or duplicate sector code", false);
            }
            LocalDate lastTradeDate = LocalDate.parse(requiredText(row, "last_trade_date"));
            if (!lastTradeDate.equals(businessDate)) {
                throw contract("SECTOR_HISTORY_STALE_DATA", "sector history last trade date mismatch", false);
            }
            SectorHistoryItem item = new SectorHistoryItem();
            item.setSectorCode(code);
            item.setSectorName(name);
            item.setLastTradeDate(lastTradeDate);
            item.setCoverageDays(requiredInteger(row, "coverage_days", 2, 10000));
            item.setReturn1d(requiredNumber(row, "return_1d"));
            item.setReturn5d(optionalNumber(row, "return_5d"));
            item.setReturn20d(optionalNumber(row, "return_20d"));
            item.setPositiveDays5(optionalInteger(row, "positive_days_5", 0, 5));
            values.add(item);
        }
        return values;
    }

    private List<LocalDate> dates(JsonNode rows, LocalDate maximum) {
        if (!rows.isArray()) {
            throw contract("SECTOR_HISTORY_SCHEMA_DRIFT", "covered trade dates must be an array", false);
        }
        List<LocalDate> values = new ArrayList<>();
        LocalDate previous = null;
        for (JsonNode row : rows) {
            LocalDate value = LocalDate.parse(row.asText());
            if (value.isAfter(maximum)) {
                throw contract("SECTOR_HISTORY_FUTURE_DATA", "covered trade dates contain future data", false);
            }
            if (previous != null && !value.isAfter(previous)) {
                throw contract("SECTOR_HISTORY_DATE_DRIFT", "covered trade dates must be unique and ordered", false);
            }
            values.add(value);
            previous = value;
        }
        if (values.isEmpty() || !maximum.equals(values.get(values.size() - 1))) {
            throw contract("SECTOR_HISTORY_STALE_DATA", "covered trade dates do not reach business date", false);
        }
        return values;
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

    private int requiredInteger(JsonNode node, String field, int minimum, int maximum) {
        Integer value = optionalInteger(node, field, minimum, maximum);
        if (value == null) {
            throw contract("SECTOR_HISTORY_SCHEMA_DRIFT", field + " must be an integer", false);
        }
        return value;
    }

    private Integer optionalInteger(JsonNode node, String field, int minimum, int maximum) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || value.asInt() < minimum || value.asInt() > maximum) {
            throw contract("SECTOR_HISTORY_SCHEMA_DRIFT", field + " is outside its valid range", false);
        }
        return value.asInt();
    }

    private Double requiredNumber(JsonNode node, String field) {
        Double value = optionalNumber(node, field);
        if (value == null) {
            throw contract("SECTOR_HISTORY_SCHEMA_DRIFT", field + " must be numeric", false);
        }
        return value;
    }

    private Double optionalNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw contract("SECTOR_HISTORY_SCHEMA_DRIFT", field + " must be finite", false);
        }
        return value.asDouble();
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw contract("SECTOR_HISTORY_SCHEMA_DRIFT", "sector history is missing " + field, false);
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

    private ProviderContractException contract(String type, String message, boolean retryable) {
        return new ProviderContractException(type, message, retryable);
    }

    private ProviderContractException contract(String type, String message, boolean retryable, Throwable cause) {
        return new ProviderContractException(type, message, retryable, cause);
    }
}
