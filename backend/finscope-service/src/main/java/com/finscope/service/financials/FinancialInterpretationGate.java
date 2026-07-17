package com.finscope.service.financials;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.financials.FinancialInterpretation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FinancialInterpretationGate {
    private static final Set<String> OPERATING_STATES = set(
            "IMPROVING", "STABLE", "UNDER_PRESSURE", "INSUFFICIENT_EVIDENCE");
    private static final Set<String> CONFIDENCES = set("HIGH", "MEDIUM", "LOW");
    private static final Set<String> ASSESSMENTS = set(
            "POSITIVE", "NEUTRAL", "NEGATIVE", "INSUFFICIENT_EVIDENCE");
    private static final Set<String> CLAIM_TYPES = set("FACT", "INFERENCE", "WATCHPOINT");
    private static final Set<String> DIMENSIONS = set("GROWTH", "PROFITABILITY",
            "EARNINGS_QUALITY", "CASH_QUALITY", "ASSET_QUALITY",
            "SOLVENCY_CAPITAL_DISCIPLINE");
    private static final Pattern NUMBER = Pattern.compile("(?<![A-Za-z_])[-+]?\\d+(?:\\.\\d+)?");
    private static final Pattern INVESTMENT_ADVICE = Pattern.compile(
            "建议买入|建议卖出|应当买入|应当卖出|目标价|保证收益|收益承诺");

    private final ObjectMapper json;

    public FinancialInterpretationGate(ObjectMapper json) {
        this.json = json;
    }

    public FinancialInterpretation.Result apply(JsonNode root, FinancialEvidencePacket packet) {
        List<String> errors = new ArrayList<String>();
        FinancialInterpretation.Result result;
        try {
            result = json.treeToValue(root, FinancialInterpretation.Result.class);
        } catch (Exception error) {
            throw new IllegalArgumentException("JSON 结构不符合财报解读协议：" + error.getMessage(), error);
        }
        require(OPERATING_STATES, result.getOperatingState(), "operatingState", errors);
        require(CONFIDENCES, result.getConfidence(), "confidence", errors);
        if (result.getExecutiveSummary() == null || result.getExecutiveSummary().isEmpty()) {
            errors.add("executiveSummary 至少需要一条结论");
        }
        validateClaims(result.getExecutiveSummary(), "executiveSummary", packet, errors);
        validateClaims(result.getPositiveSignals(), "positiveSignals", packet, errors);
        validateClaims(result.getRisks(), "risks", packet, errors);
        validateClaims(result.getTurningPoints(), "turningPoints", packet, errors);
        validateClaims(result.getWatchpoints(), "watchpoints", packet, errors);
        validateDimensions(result.getDimensions(), packet, errors);
        if (rank(result.getConfidence()) > rank(packet.getQualityCeiling())) {
            errors.add("置信度超过数据质量上限 " + packet.getQualityCeiling());
        }
        for (String text : narrative(result)) {
            if (text == null) continue;
            if (INVESTMENT_ADVICE.matcher(text).find()) {
                errors.add("输出包含投资建议或承诺性表达");
            }
            validateNumbers(text, packet, errors);
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("；", new LinkedHashSet<String>(errors)));
        }
        return result;
    }

    private void validateDimensions(List<FinancialInterpretation.Dimension> values,
                                    FinancialEvidencePacket packet, List<String> errors) {
        if (values == null) {
            errors.add("dimensions 缺失");
            return;
        }
        Set<String> actual = new HashSet<String>();
        for (FinancialInterpretation.Dimension value : values) {
            if (value == null) {
                errors.add("dimensions 包含空项");
                continue;
            }
            require(DIMENSIONS, value.getCode(), "dimension.code", errors);
            require(ASSESSMENTS, value.getAssessment(), "dimension.assessment", errors);
            if (!actual.add(value.getCode())) errors.add("dimensions 包含重复维度 " + value.getCode());
            if (blank(value.getSummary())) errors.add("dimension.summary 不能为空");
            validateRefs(value.getRefs(), "dimension " + value.getCode(), packet, errors);
        }
        if (!actual.equals(DIMENSIONS)) errors.add("dimensions 必须完整覆盖六个固定维度");
    }

    private void validateClaims(List<FinancialInterpretation.Claim> values, String field,
                                FinancialEvidencePacket packet, List<String> errors) {
        if (values == null) return;
        for (FinancialInterpretation.Claim value : values) {
            if (value == null || blank(value.getClaim())) {
                errors.add(field + " 包含空结论");
                continue;
            }
            require(CLAIM_TYPES, value.getClaimType(), field + ".claimType", errors);
            validateRefs(value.getRefs(), field, packet, errors);
        }
    }

    private void validateRefs(List<String> refs, String field, FinancialEvidencePacket packet,
                              List<String> errors) {
        if (refs == null || refs.isEmpty()) {
            errors.add(field + " 必须包含证据引用");
            return;
        }
        for (String ref : refs) {
            if (!packet.getEvidenceIndex().containsKey(ref)) {
                errors.add("引用不存在：" + ref);
            }
        }
    }

    private void validateNumbers(String text, FinancialEvidencePacket packet, List<String> errors) {
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group();
            String unsigned = raw.startsWith("+") ? raw.substring(1) : raw;
            String normalized;
            try {
                normalized = new BigDecimal(raw).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException error) {
                normalized = unsigned;
            }
            if (!packet.getAllowedNumbers().contains(unsigned)
                    && !packet.getAllowedNumbers().contains(normalized)) {
                errors.add("输出包含证据包外数字：" + raw);
            }
        }
    }

    private List<String> narrative(FinancialInterpretation.Result result) {
        List<String> values = new ArrayList<String>();
        addClaims(values, result.getExecutiveSummary());
        addClaims(values, result.getPositiveSignals());
        addClaims(values, result.getRisks());
        addClaims(values, result.getTurningPoints());
        addClaims(values, result.getWatchpoints());
        if (result.getDimensions() != null) {
            result.getDimensions().forEach(item -> values.add(item == null ? null : item.getSummary()));
        }
        if (result.getLimitations() != null) values.addAll(result.getLimitations());
        values.add(result.getDisclaimer());
        return values;
    }

    private void addClaims(List<String> target, List<FinancialInterpretation.Claim> claims) {
        if (claims != null) claims.forEach(item -> target.add(item == null ? null : item.getClaim()));
    }

    private void require(Set<String> allowed, String value, String field, List<String> errors) {
        if (!allowed.contains(value)) errors.add(field + " 非法：" + value);
    }

    private int rank(String confidence) {
        if ("HIGH".equals(confidence)) return 3;
        if ("MEDIUM".equals(confidence)) return 2;
        return 1;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<String>(Arrays.asList(values));
    }
}
