package com.finscope.rpc.marketpulse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.common.enums.marketpulse.MarketResearchGroup;
import com.finscope.domain.marketpulse.DailyResearchGroup;
import com.finscope.domain.marketpulse.DailyResearchSnapshot;
import com.finscope.domain.marketpulse.DailyResearchStock;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 本地行情服务的日频研究适配器，隔离并核验外部版本、日期、数值和成员关系。 */
@Component
public class PythonDailyResearchSource {
    private static final int MAX_STOCKS = 10000;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int TIMEOUT_MS = 30000;
    @Resource
    private FinanceHttpClient http;
    @Value("${finscope.python-market-data.base-url:http://127.0.0.1:8000}")
    private String baseUrl;
    private final ObjectMapper json = new ObjectMapper();

    public DailyResearchSnapshot fetch(LocalDate businessDate) {
        try {
            URI uri = URI.create(baseUrl.replaceAll("/+$", "")
                    + "/v1/markets/CN-A/daily-research?business_date=" + businessDate);
            var response = http.get("PYTHON_DAILY_RESEARCH", uri, Map.of(), MAX_BYTES, TIMEOUT_MS);
            if (response.getStatus() != 200) {
                throw invalid("日频研究服务返回失败状态");
            }
            return parse(json.readTree(response.getBody()), businessDate);
        } catch (ProviderContractException error) {
            throw error;
        } catch (Exception error) {
            throw new ProviderContractException("DAILY_RESEARCH_FETCH_FAILED", "日频研究数据读取失败", true, error);
        }
    }

    private DailyResearchSnapshot parse(JsonNode root, LocalDate requested) {
        if (!"daily-research-v1".equals(text(root, "schema_version"))
                || !"LOCAL_DAILY_BAR_PANEL".equals(text(root, "source_code"))) {
            throw invalid("日频研究版本或来源不匹配");
        }
        var value = new DailyResearchSnapshot();
        value.setBusinessDate(LocalDate.parse(text(root, "business_date")));
        if (!requested.equals(value.getBusinessDate())) {
            throw invalid("日频研究日期不匹配");
        }
        if (root.hasNonNull("selection_date")) {
            value.setSelectionDate(LocalDate.parse(text(root, "selection_date")));
            if (!value.getSelectionDate().isBefore(requested)) {
                throw invalid("选组日期必须早于观察日期");
            }
        }
        String quality = text(root, "quality_status");
        if (!Set.of("PARTIAL", "UNAVAILABLE").contains(quality)) {
            throw invalid("本地样本质量状态无效");
        }
        value.setQualityStatus(MarketPulseQualityStatus.valueOf(quality));
        value.setSourceCode(text(root, "source_code"));
        value.setSampleCount(count(root, "sample_count"));
        value.setStocks(stocks(root.path("stocks")));
        if (value.getSampleCount() < value.getStocks().size()) {
            throw invalid("样本数量小于返回股票数量");
        }
        value.setGroups(groups(root.path("groups"), value.getStocks(), value.getSampleCount()));
        if (value.getSelectionDate() == null && value.getGroups().stream().anyMatch(group -> group.getMemberCount() > 0)) {
            throw invalid("缺少选组日期");
        }
        if (root.hasNonNull("cache_hit")) {
            if (!root.path("cache_hit").isBoolean()) {
                throw invalid("日频缓存标记必须为布尔值");
            }
            value.setCacheHit(root.path("cache_hit").asBoolean());
        }
        if (root.hasNonNull("calculated_at")) {
            String calculatedAt = text(root, "calculated_at");
            java.time.OffsetDateTime.parse(calculatedAt);
            value.setCalculatedAt(calculatedAt);
        }
        value.setWarnings(strings(root.path("warnings"), 30));
        return value;
    }

    private List<DailyResearchStock> stocks(JsonNode rows) {
        array(rows, MAX_STOCKS);
        List<DailyResearchStock> values = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (JsonNode row : rows) {
            var value = new DailyResearchStock();
            String code = text(row, "instrument_code");
            if (!code.matches("(?:6\\d{5}\\.SH|[03]\\d{5}\\.SZ|[489]\\d{5}\\.BJ)") || !codes.add(code)) {
                throw invalid("股票代码无效或重复");
            }
            value.setInstrumentCode(code);
            value.setReturn1d(number(row, "return_1d"));
            value.setReturn5d(number(row, "return_5d"));
            value.setReturn20d(number(row, "return_20d"));
            value.setAmount(number(row, "amount"));
            if (value.getAmount() != null && value.getAmount() < 0) {
                throw invalid("成交额不能为负数");
            }
            value.setGroupCodes(strings(row.path("group_codes"), 3).stream().map(MarketResearchGroup::valueOf).toList());
            if (new HashSet<>(value.getGroupCodes()).size() != value.getGroupCodes().size()) {
                throw invalid("股票组重复");
            }
            values.add(value);
        }
        return values;
    }

    private List<DailyResearchGroup> groups(JsonNode rows, List<DailyResearchStock> stocks, int sampleCount) {
        array(rows, 3);
        if (rows.size() != 3) {
            throw invalid("必须提供三个观察组");
        }
        Map<String, DailyResearchStock> byCode = new HashMap<>();
        stocks.forEach(stock -> byCode.put(stock.getInstrumentCode(), stock));
        Set<MarketResearchGroup> seen = new HashSet<>();
        List<DailyResearchGroup> values = new ArrayList<>();
        for (JsonNode row : rows) {
            var value = new DailyResearchGroup();
            value.setCode(MarketResearchGroup.valueOf(text(row, "code")));
            if (!seen.add(value.getCode())) {
                throw invalid("观察组代码重复");
            }
            value.setLabel(text(row, "label"));
            value.setDefinition(text(row, "definition"));
            value.setEligibleCount(count(row, "eligible_count"));
            value.setMemberCount(count(row, "member_count"));
            value.setValidCount(count(row, "valid_count"));
            value.setAdvanceRatio(number(row, "advance_ratio"));
            value.setMedianReturn(number(row, "median_return"));
            value.setMembers(strings(row.path("members"), MAX_STOCKS));
            validateGroup(value, byCode, sampleCount);
            values.add(value);
        }
        return values;
    }

    private void validateGroup(DailyResearchGroup group, Map<String, DailyResearchStock> stocks, int sampleCount) {
        Set<String> members = new HashSet<>(group.getMembers());
        Set<String> expected = new HashSet<>();
        int valid = 0;
        for (DailyResearchStock stock : stocks.values()) {
            if (stock.getGroupCodes().contains(group.getCode())) {
                expected.add(stock.getInstrumentCode());
                if (stock.getReturn1d() != null) {
                    valid++;
                }
            }
        }
        if (!members.equals(expected) || members.size() != group.getMembers().size()
                || group.getMemberCount() != members.size() || group.getValidCount() != valid
                || group.getEligibleCount() < members.size() || group.getEligibleCount() > sampleCount) {
            throw invalid("观察组成员与样本计数不一致");
        }
        Double ratio = group.getAdvanceRatio();
        if (ratio != null && (ratio < 0 || ratio > 1)
                || valid < 5 && (ratio != null || group.getMedianReturn() != null)) {
            throw invalid("观察组统计与样本范围不一致");
        }
    }

    private int count(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0 || value.asInt() > MAX_STOCKS) {
            throw invalid("样本计数无效：" + field);
        }
        return value.asInt();
    }

    private Double number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw invalid("指标必须为有限数字：" + field);
        }
        return value.asDouble();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 2000) {
            throw invalid("文本字段无效：" + field);
        }
        return value.asText();
    }

    private List<String> strings(JsonNode rows, int maximum) {
        array(rows, maximum);
        List<String> result = new ArrayList<>();
        for (JsonNode row : rows) {
            if (!row.isTextual() || row.asText().isBlank() || row.asText().length() > 2000) {
                throw invalid("列表字段必须为非空文本");
            }
            result.add(row.asText());
        }
        return result;
    }

    private void array(JsonNode rows, int maximum) {
        if (!rows.isArray() || rows.size() > maximum) {
            throw invalid("日频研究列表格式或大小无效");
        }
    }

    private ProviderContractException invalid(String message) {
        return new ProviderContractException("DAILY_RESEARCH_CONTRACT_INVALID", message, false);
    }
}
