package com.finscope.rpc.financials;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialStatementType;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class PythonFinancialDataClient implements StructuredFinancialDataGateway {
    private final String baseUrl;
    private final FinanceHttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public PythonFinancialDataClient(
            FinanceHttpClient http,
            @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}") String baseUrl) {
        this(baseUrl, http);
    }

    PythonFinancialDataClient(String baseUrl, FinanceHttpClient http) {
        this.baseUrl = trim(baseUrl);
        this.http = http;
    }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument != null && "STOCK".equals(instrument.getType())
                && ("SH".equals(instrument.getMarket())
                || "SZ".equals(instrument.getMarket())
                || "BJ".equals(instrument.getMarket()));
    }

    @Override
    public String providerCode() {
        return "PYTHON_FINANCIALS";
    }

    @Override
    public ExternalFinancialStatements fetch(Instrument instrument, LocalDate periodEnd,
                                             FinancialReportType reportType) {
        requireSupported(instrument);
        URI uri = URI.create(baseUrl + "/v1/stocks/" + instrument.getMarket() + "/"
                + instrument.getCode() + "/financial-statements?period_end=" + periodEnd
                + "&report_type=" + reportType.name() + "&scope=CONSOLIDATED");
        try {
            FinanceHttpResponse response = http.get(
                    "PYTHON_FINANCIALS", uri, Collections.<String, String>emptyMap());
            return parse(response);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("PYTHON_FINANCIAL_SERVICE_ERROR", "Python 财报数据服务调用失败：" + message(error), true);
        }
    }

    private ExternalFinancialStatements parse(FinanceHttpResponse response) throws Exception {
        JsonNode root = json.readTree(response.getBody());
        String qualityText = text(root, "quality_status");
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull() || !data.isObject()) {
            throw new ProviderContractException(
                    "PYTHON_FINANCIAL_SCHEMA_DRIFT", "财报数据响应缺少 data", false);
        }
        JsonNode report = data.path("report");
        if (!report.isObject()) {
            throw new ProviderContractException(
                    "PYTHON_FINANCIAL_SCHEMA_DRIFT", "财报数据响应缺少 report", false);
        }
        ExternalFinancialStatements result = new ExternalFinancialStatements();
        result.setPeriodEnd(LocalDate.parse(requiredText(report, "period_end")));
        result.setReportType(FinancialReportType.valueOf(requiredText(report, "report_type")));
        result.setScope(defaultText(report, "scope", "CONSOLIDATED"));
        result.setCurrency(defaultText(report, "currency", "CNY"));
        result.setPublishedAt(parseDateTime(text(report, "published_at")));
        if (report.hasNonNull("audited")) {
            result.setAudited(report.get("audited").asBoolean());
        }
        result.setSourceCode(defaultText(root, "source_code", "PYTHON_MARKET_DATA"));
        result.setQualityStatus(mapQuality(qualityText));
        result.setWarnings(strings(root.path("warnings")));
        for (JsonNode statementNode : data.path("statements")) {
            ExternalFinancialStatements.Statement statement =
                    new ExternalFinancialStatements.Statement();
            statement.setStatementType(FinancialStatementType.valueOf(
                    requiredText(statementNode, "statement_type")));
            List<ExternalFinancialStatements.Value> values =
                    new ArrayList<ExternalFinancialStatements.Value>();
            for (JsonNode valueNode : statementNode.path("values")) {
                ExternalFinancialStatements.Value value =
                        new ExternalFinancialStatements.Value();
                value.setSourceLabel(requiredText(valueNode, "source_label"));
                value.setConceptCode(text(valueNode, "concept_code"));
                value.setPeriodRole(requiredText(valueNode, "period_role"));
                value.setValue(decimal(valueNode.get("value")));
                BigDecimal multiplier = decimal(valueNode.get("unit_multiplier"));
                value.setUnitMultiplier(multiplier == null ? BigDecimal.ONE : multiplier);
                value.setSourceField(text(valueNode, "source_field"));
                values.add(value);
            }
            statement.setValues(values);
            result.getStatements().add(statement);
        }
        if (result.getStatements().isEmpty()) {
            throw new ProviderContractException(
                    "EMPTY_FINANCIAL_STATEMENTS", "财报数据服务未返回三张表", true);
        }
        return result;
    }

    private static void requireSupported(Instrument instrument) {
        if (instrument == null || !"STOCK".equals(instrument.getType())
                || !("SH".equals(instrument.getMarket())
                || "SZ".equals(instrument.getMarket())
                || "BJ".equals(instrument.getMarket()))) {
            throw new ProviderContractException(
                    "FINANCIAL_INSTRUMENT_UNSUPPORTED", "首期仅支持 A 股股票财报", false);
        }
    }

    private static FinancialQualityStatus mapQuality(String value) {
        if ("FRESH_PRIMARY".equals(value) || "FRESH_FALLBACK".equals(value)) {
            return FinancialQualityStatus.FRESH;
        }
        if ("STALE_FALLBACK".equals(value)) {
            return FinancialQualityStatus.STALE;
        }
        if ("PARTIAL_FRESH".equals(value)) {
            return FinancialQualityStatus.PARTIAL;
        }
        if ("UNAVAILABLE".equals(value)) {
            return FinancialQualityStatus.UNAVAILABLE;
        }
        return FinancialQualityStatus.PARTIAL;
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
            throw new ProviderContractException(
                    "PYTHON_FINANCIAL_SCHEMA_DRIFT", "财报数据字段缺失：" + field, false);
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

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException error) {
            return LocalDateTime.parse(value);
        }
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
