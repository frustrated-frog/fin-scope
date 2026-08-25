package com.finscope.rpc.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.FundHoldingDisclosure;
import com.finscope.domain.instrument.FundStockHolding;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 使用扶摇结构化接口读取基金最近披露的股票持仓。 */
@Component
public class FuyaoFundHoldingProvider implements FundHoldingProvider {
    private static final String PROVIDER_CODE = "FUYAO_FUND_HOLDINGS";
    private static final Pattern FUND_CODE = Pattern.compile("^\\d{6}$");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private FinanceHttpClient http;

    @Value("${finscope.fuyao.base-url:https://fuyao.aicubes.cn}")
    private String baseUrl;

    @Value("${finscope.fuyao.api-key:}")
    private String apiKey;

    private final ObjectMapper json = new ObjectMapper();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    @Override
    public FundHoldingDisclosure fetch(String fundCode) {
        String normalizedCode = normalizeCode(fundCode);
        if (!isConfigured()) {
            throw new ProviderContractException(
                    "FUYAO_NOT_CONFIGURED", "扶摇基金数据源未配置 API Key", false);
        }
        FundTarget target = target(normalizedCode);
        URI uri = URI.create(trimBaseUrl() + "/api/fund/portfolio/holdings?fund_type="
                + encode(target.fundType) + "&thscode=" + encode(target.thscode));
        try {
            FinanceHttpResponse response = http.get(
                    PROVIDER_CODE, uri, Collections.singletonMap("X-api-key", apiKey.trim()));
            return parse(normalizedCode, response);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException(
                    "FUYAO_FUND_HOLDING_ERROR", "扶摇基金持仓请求或解析失败", true, error);
        }
    }

    private FundHoldingDisclosure parse(String fundCode, FinanceHttpResponse response) throws Exception {
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            boolean retryable = response.getStatus() == 429 || response.getStatus() >= 500;
            throw new ProviderContractException(
                    "FUYAO_HTTP_" + response.getStatus(), "扶摇基金持仓 HTTP 请求失败", retryable);
        }
        JsonNode root = json.readTree(response.getBody());
        int code = root.path("code").asInt(Integer.MIN_VALUE);
        if (code != 0) {
            throw businessError(code, text(root, "message"));
        }
        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            throw drift("扶摇基金持仓响应缺少 data");
        }
        JsonNode items = data.get("item");
        if (items == null || !items.isArray()) {
            throw drift("扶摇基金持仓响应缺少 item 数组");
        }
        List<FundStockHolding> holdings = new ArrayList<FundStockHolding>();
        LocalDate disclosureDate = null;
        for (JsonNode item : items) {
            LocalDate itemDate = date(item.get("end_date_ms"));
            if (itemDate != null && (disclosureDate == null || itemDate.isAfter(disclosureDate))) {
                disclosureDate = itemDate;
            }
            if (!"stock".equalsIgnoreCase(text(item, "asset_type"))) {
                continue;
            }
            holdings.add(holding(item));
        }
        LocalDateTime retrievedAt = response.getRetrievedAt() == null
                ? LocalDateTime.now(SHANGHAI)
                : LocalDateTime.ofInstant(response.getRetrievedAt(), SHANGHAI);
        return new FundHoldingDisclosure(
                fundCode, "", disclosureDate, retrievedAt, holdings);
    }

    private FundStockHolding holding(JsonNode item) {
        int rank = positiveInt(item, "investment_rank");
        String stockCode = requiredText(item, "ticker");
        String stockName = requiredText(item, "stock_name");
        if (!FUND_CODE.matcher(stockCode).matches()) {
            throw drift("扶摇基金持仓股票代码无效");
        }
        double weightPct = nonNegative(item, "hold_ratio");
        Double sharesTenThousand = dividedOptional(item, "position_count");
        Double marketValueTenThousand = dividedOptional(item, "position_capital");
        return new FundStockHolding(rank, stockCode, stockName, weightPct,
                sharesTenThousand, marketValueTenThousand);
    }

    private ProviderContractException businessError(int code, String message) {
        String safeMessage = message == null || message.trim().isEmpty()
                ? "扶摇基金持仓业务请求失败" : message;
        if (code == 4001) {
            return new ProviderContractException("FUYAO_RATE_LIMITED", safeMessage, true);
        }
        boolean retryable = code == 3002 || code == 5001 || code == 5002 || code == 5003;
        return new ProviderContractException("FUYAO_" + code, safeMessage, retryable);
    }

    private int positiveInt(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null || !value.canConvertToInt() || value.asInt() <= 0) {
            throw drift("扶摇基金持仓字段无效：" + field);
        }
        return value.asInt();
    }

    private double nonNegative(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null || !value.isNumber()) {
            throw drift("扶摇基金持仓字段缺失：" + field);
        }
        double number = value.asDouble();
        if (!Double.isFinite(number) || number < 0.0d) {
            throw drift("扶摇基金持仓字段无效：" + field);
        }
        return number;
    }

    private Double dividedOptional(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw drift("扶摇基金持仓字段无效：" + field);
        }
        double number = value.asDouble();
        if (!Double.isFinite(number) || number < 0.0d) {
            throw drift("扶摇基金持仓字段无效：" + field);
        }
        return number / 10_000.0d;
    }

    private String requiredText(JsonNode item, String field) {
        String value = text(item, field);
        if (value == null || value.trim().isEmpty()) {
            throw drift("扶摇基金持仓字段缺失：" + field);
        }
        return value.trim();
    }

    private String text(JsonNode item, String field) {
        JsonNode value = item == null ? null : item.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private LocalDate date(JsonNode value) {
        if (value == null || !value.canConvertToLong()) {
            return null;
        }
        return Instant.ofEpochMilli(value.asLong()).atZone(SHANGHAI).toLocalDate();
    }

    private FundTarget target(String code) {
        if (code.startsWith("5")) {
            return new FundTarget("exchange", code + ".SH");
        }
        if (code.startsWith("1")) {
            return new FundTarget("exchange", code + ".SZ");
        }
        return new FundTarget("otc", code + ".OF");
    }

    private String normalizeCode(String fundCode) {
        String normalized = fundCode == null ? "" : fundCode.trim();
        if (!FUND_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("fund code must contain exactly six digits");
        }
        return normalized;
    }

    private String trimBaseUrl() {
        String normalized = baseUrl == null || baseUrl.trim().isEmpty()
                ? "https://fuyao.aicubes.cn" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private ProviderContractException drift(String message) {
        return new ProviderContractException("FUYAO_FUND_HOLDING_SCHEMA_DRIFT", message, false);
    }

    private static final class FundTarget {
        private final String fundType;
        private final String thscode;

        private FundTarget(String fundType, String thscode) {
            this.fundType = fundType;
            this.thscode = thscode;
        }
    }
}
