package com.finscope.rpc.valuation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class PythonValuationDataClient {
    @Autowired
    private FinanceHttpClient http;
    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    private final ObjectMapper json = new ObjectMapper();

    public PythonValuationDataClient() {
    }

    PythonValuationDataClient(String baseUrl, FinanceHttpClient http) {
        this.baseUrl = trim(baseUrl);
        this.http = http;
    }

    public ExternalValuationSnapshot fetchValuation(Instrument instrument) {
        requireSupported(instrument);
        URI uri = URI.create(trim(baseUrl) + stockPath(instrument) + "/valuation");
        try {
            FinanceHttpResponse response = http.get(
                    "PYTHON_VALUATION", uri, Collections.<String, String>emptyMap());
            return parseValuation(response);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException(
                    "PYTHON_VALUATION_SERVICE_ERROR", "Python 估值数据服务调用失败：" + message(error), true);
        }
    }

    public List<ExternalCorporateAction> fetchCorporateActions(
            Instrument instrument, LocalDate fromDate, LocalDate toDate) {
        requireSupported(instrument);
        URI uri = URI.create(trim(baseUrl) + stockPath(instrument) + "/corporate-actions"
                + "?from_date=" + fromDate + "&to_date=" + toDate);
        try {
            FinanceHttpResponse response = http.get(
                    "PYTHON_VALUATION", uri, Collections.<String, String>emptyMap());
            return parseCorporateActions(response);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException(
                    "PYTHON_CORPORATE_ACTION_SERVICE_ERROR", "Python 公司行为服务调用失败：" + message(error), true);
        }
    }

    private ExternalValuationSnapshot parseValuation(FinanceHttpResponse response) throws Exception {
        JsonNode root = json.readTree(response.getBody());
        JsonNode data = requireData(root);
        ExternalValuationSnapshot result = new ExternalValuationSnapshot();
        result.setName(text(data, "name"));
        result.setPeTtm(decimal(data.get("pe_ttm")));
        result.setPeMrq(decimal(data.get("pe_mrq")));
        result.setPbMrq(decimal(data.get("pb_mrq")));
        result.setPsTtm(decimal(data.get("ps_ttm")));
        result.setPcfTtm(decimal(data.get("pcf_ttm")));
        result.setObservedAt(Instant.parse(requiredText(data, "observed_at")));
        result.setSourceCode(defaultText(root, "source_code", "PYTHON_MARKET_DATA"));
        result.setQualityStatus(defaultText(root, "quality_status", "PARTIAL_FRESH"));
        result.setWarnings(strings(root.path("warnings")));
        return result;
    }

    private List<ExternalCorporateAction> parseCorporateActions(FinanceHttpResponse response) throws Exception {
        JsonNode root = json.readTree(response.getBody());
        JsonNode items = requireData(root).path("items");
        if (!items.isArray()) {
            throw schemaDrift("公司行为响应缺少 items");
        }
        String sourceCode = defaultText(root, "source_code", "PYTHON_MARKET_DATA");
        List<ExternalCorporateAction> result = new ArrayList<ExternalCorporateAction>();
        for (JsonNode item : items) {
            ExternalCorporateAction action = new ExternalCorporateAction();
            action.setExDate(LocalDate.parse(requiredText(item, "ex_date")));
            action.setEventTypes(strings(item.path("event_types")));
            action.setDividendPerShare(decimal(item.get("dividend_per_share")));
            action.setPerShareBonus(decimal(item.get("per_share_bonus")));
            action.setAllotmentRatio(decimal(item.get("allotment_ratio")));
            action.setAllotmentPrice(decimal(item.get("allotment_price")));
            action.setCurrency(defaultText(item, "currency", "CNY"));
            action.setSourceCode(sourceCode);
            result.add(action);
        }
        return result;
    }

    private static JsonNode requireData(JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isObject()) {
            throw schemaDrift("估值数据响应缺少 data");
        }
        return data;
    }

    private static void requireSupported(Instrument instrument) {
        if (instrument == null || !"STOCK".equals(instrument.getType())
                || !("SH".equals(instrument.getMarket())
                || "SZ".equals(instrument.getMarket())
                || "BJ".equals(instrument.getMarket()))) {
            throw new ProviderContractException(
                    "VALUATION_INSTRUMENT_UNSUPPORTED", "估值数据首期仅支持 A 股股票", false);
        }
    }

    private static String stockPath(Instrument instrument) {
        return "/v1/stocks/" + instrument.getMarket() + "/" + instrument.getCode();
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.trim().isEmpty() ? null : new BigDecimal(value);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.trim().isEmpty()) {
            throw schemaDrift("估值数据字段缺失：" + field);
        }
        return value;
    }

    private static String defaultText(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<String>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    result.add(item.asText());
                }
            }
        }
        return result;
    }

    private static ProviderContractException schemaDrift(String message) {
        return new ProviderContractException("PYTHON_VALUATION_SCHEMA_DRIFT", message, false);
    }

    private static String trim(String value) {
        String normalized = value == null || value.trim().isEmpty()
                ? "http://127.0.0.1:8000" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
