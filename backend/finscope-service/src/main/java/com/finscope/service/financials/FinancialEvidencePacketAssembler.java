package com.finscope.service.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialFinding;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.domain.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialReportView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class FinancialEvidencePacketAssembler {
    public static final String PROMPT_VERSION = "financial-interpret-v3";
    public static final String ALGORITHM_VERSION = "financial-analysis-v4";
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
    private static final BigDecimal HUNDRED_MILLION = new BigDecimal("100000000");

    private final ObjectMapper json;
    private final FinancialTrendEngine trends;
    private final FinancialEvidenceSelector selector;

    public FinancialEvidencePacketAssembler(ObjectMapper json, FinancialTrendEngine trends,
                                            FinancialEvidenceSelector selector) {
        this.json = json;
        this.trends = trends;
        this.selector = selector;
    }

    public FinancialEvidencePacket assemble(FinancialReportView current,
                                             List<FinancialReportView> comparables) {
        try {
            List<FinancialEvidence> evidence = new ArrayList<FinancialEvidence>();
            evidence.addAll(metricEvidence(current));
            evidence.addAll(lineEvidence(current));
            evidence.addAll(findingEvidence(current));
            List<FinancialReportView> all = new ArrayList<FinancialReportView>();
            all.add(current);
            if (comparables != null) all.addAll(comparables);
            evidence.addAll(trends.build(all));
            evidence.addAll(gapEvidence(current));
            evidence.sort(Comparator.comparing(FinancialEvidence::getId));
            List<FinancialEvidence> modelEvidence = selector.select(
                    evidence, current.getReport().getReportType());

            String quality = qualityCeiling(current);
            Map<String, Object> report = new LinkedHashMap<String, Object>();
            report.put("stockCode", current.getInstrument().getCode());
            report.put("market", current.getInstrument().getMarket());
            report.put("companyName", current.getInstrument().getName());
            report.put("periodEnd", current.getReport().getPeriodEnd().toString());
            report.put("reportType", current.getReport().getReportType().name());
            report.put("scope", current.getReport().getScope());
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("report", report);
            payload.put("qualityCeiling", quality);
            payload.put("evidence", evidence);
            payload.put("algorithmVersion", ALGORITHM_VERSION);
            payload.put("selectorVersion", FinancialEvidenceSelector.SELECTOR_VERSION);
            List<String> modelEvidenceIds = new ArrayList<String>();
            for (FinancialEvidence item : modelEvidence) modelEvidenceIds.add(item.getId());
            payload.put("modelEvidenceIds", modelEvidenceIds);
            Map<String, Object> modelPayload = new LinkedHashMap<String, Object>();
            modelPayload.put("report", report);
            modelPayload.put("qualityCeiling", quality);
            modelPayload.put("evidence", modelEvidence);
            modelPayload.put("algorithmVersion", ALGORITHM_VERSION);
            modelPayload.put("selectorVersion", FinancialEvidenceSelector.SELECTOR_VERSION);
            String modelPayloadJson = json.writeValueAsString(modelPayload);
            payload.put("modelEvidenceCount", modelEvidence.size());
            payload.put("modelInputBytes", modelPayloadJson.getBytes(StandardCharsets.UTF_8).length);
            String payloadJson = json.writeValueAsString(payload);
            String sourceHash = sha256(json.writeValueAsString(sourceCanonical(current)));

            FinancialEvidencePacket packet = new FinancialEvidencePacket();
            packet.setReportId(current.getReport().getId());
            packet.setPromptVersion(PROMPT_VERSION);
            packet.setAlgorithmVersion(ALGORITHM_VERSION);
            packet.setSourceHash(sourceHash);
            packet.setInputHash(sha256(payloadJson));
            packet.setQualityCeiling(quality);
            packet.setPayloadJson(payloadJson);
            packet.setModelPayloadJson(modelPayloadJson);
            packet.setEvidence(evidence);
            packet.setModelEvidence(modelEvidence);
            LinkedHashMap<String, FinancialEvidence> index = new LinkedHashMap<String, FinancialEvidence>();
            LinkedHashSet<String> numbers = new LinkedHashSet<String>();
            for (FinancialEvidence item : modelEvidence) {
                index.put(item.getId(), item);
                collectNumbers(numbers, item.getValue());
                collectNumbers(numbers, item.getDetail());
                collectNumbers(numbers, item.getPeriod());
            }
            packet.setEvidenceIndex(index);
            packet.setAllowedNumbers(numbers);
            return packet;
        } catch (Exception error) {
            throw new IllegalStateException("cannot assemble financial evidence packet", error);
        }
    }

    private List<FinancialEvidence> metricEvidence(FinancialReportView view) {
        List<FinancialMetric> sorted = new ArrayList<FinancialMetric>(view.getMetrics());
        sorted.sort(Comparator.comparing(FinancialMetric::getMetricCode));
        List<FinancialEvidence> result = new ArrayList<FinancialEvidence>();
        for (FinancialMetric metric : sorted) {
            if (metric.getMetricCode() == null || metric.getValue() == null) continue;
            FinancialEvidence value = base("M_" + token(metric.getMetricCode()), "METRIC",
                    metric.getLabel(), metric.getValue().toPlainString(), metric.getUnit(),
                    view.getReport().getPeriodEnd().toString());
            value.setDetail("公式版本=" + metric.getFormulaVersion());
            result.add(value);
        }
        return result;
    }

    private List<FinancialEvidence> lineEvidence(FinancialReportView view) {
        List<FinancialLineItem> lines = new ArrayList<FinancialLineItem>();
        view.getStatements().values().forEach(lines::addAll);
        lines.sort(Comparator.comparing((FinancialLineItem value) -> value.getStatementType().name())
                .thenComparing(value -> safe(value.getConceptCode()))
                .thenComparing(value -> safe(value.getPeriodRole()))
                .thenComparing(value -> safe(value.getSourceLabel())));
        List<FinancialEvidence> result = new ArrayList<FinancialEvidence>();
        for (FinancialLineItem line : lines) {
            if (line.getNormalizedValue() == null) continue;
            String concept = line.getConceptCode() == null ? line.getSourceLabel() : line.getConceptCode();
            String id = "L_" + token(line.getStatementType().name()) + "_" + token(concept) + "_"
                    + view.getReport().getPeriodEnd().getYear() + "_"
                    + token(view.getReport().getReportType().name()) + "_" + token(line.getPeriodRole());
            FinancialEvidence value = base(id, "LINE_ITEM", line.getSourceLabel(),
                    line.getNormalizedValue().toPlainString(), line.getCurrency(),
                    view.getReport().getPeriodEnd().toString());
            value.setDetail("口径=" + line.getPeriodRole() + "；来源=" + line.getValueOrigin());
            result.add(value);
        }
        return result;
    }

    private List<FinancialEvidence> findingEvidence(FinancialReportView view) {
        List<FinancialFinding> sorted = new ArrayList<FinancialFinding>(view.getFindings());
        sorted.sort(Comparator.comparing(FinancialFinding::getRuleCode));
        List<FinancialEvidence> result = new ArrayList<FinancialEvidence>();
        for (FinancialFinding finding : sorted) {
            FinancialEvidence value = base("F_" + token(finding.getRuleCode()), "FINDING",
                    finding.getTitle(), finding.getSeverity(), null,
                    view.getReport().getPeriodEnd().toString());
            value.setDetail(finding.getExplanation());
            if (finding.getMetricRefs() != null) {
                for (String ref : finding.getMetricRefs().split(",")) {
                    value.getSourceRefs().add("M_" + token(ref.trim()));
                }
            }
            result.add(value);
        }
        return result;
    }

    private List<FinancialEvidence> gapEvidence(FinancialReportView view) {
        List<String> gaps = new ArrayList<String>(view.getDataGaps());
        gaps.sort(String::compareTo);
        List<FinancialEvidence> result = new ArrayList<FinancialEvidence>();
        for (String gap : gaps) {
            FinancialEvidence value = base("G_" + sha256(gap).substring(0, 12).toUpperCase(),
                    "DATA_GAP", "数据缺口", null, null, view.getReport().getPeriodEnd().toString());
            value.setDetail(gap);
            result.add(value);
        }
        return result;
    }

    private Map<String, Object> sourceCanonical(FinancialReportView view) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("periodEnd", view.getReport().getPeriodEnd());
        value.put("reportType", view.getReport().getReportType());
        value.put("scope", view.getReport().getScope());
        value.put("currency", view.getReport().getCurrency());
        value.put("source", view.getReport().getSourceCode());
        value.put("lines", lineEvidence(view));
        return value;
    }

    private String qualityCeiling(FinancialReportView view) {
        FinancialQualityStatus status = view.getReport().getQualityStatus();
        if (status == FinancialQualityStatus.UNAVAILABLE || status == FinancialQualityStatus.CONFLICT) {
            return "LOW";
        }
        if (status != FinancialQualityStatus.FRESH || !view.getDataGaps().isEmpty()) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private FinancialEvidence base(String id, String type, String label, String value,
                                   String unit, String period) {
        FinancialEvidence evidence = new FinancialEvidence();
        evidence.setId(id);
        evidence.setType(type);
        evidence.setLabel(label);
        evidence.setValue(value);
        evidence.setUnit(unit);
        evidence.setPeriod(period);
        return evidence;
    }

    private void collectNumbers(LinkedHashSet<String> values, String text) {
        if (text == null) return;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<![A-Za-z_])[-+]?\\d+(?:\\.\\d+)?").matcher(text);
        while (matcher.find()) {
            String raw = matcher.group();
            values.add(raw.startsWith("+") ? raw.substring(1) : raw);
            try {
                BigDecimal number = new BigDecimal(raw);
                addDisplayVariants(values, number);
                if (number.abs().compareTo(TEN_THOUSAND) >= 0) {
                    addDisplayVariants(values, number.divide(TEN_THOUSAND, 8, RoundingMode.HALF_UP));
                    addDisplayVariants(values, number.divide(HUNDRED_MILLION, 8, RoundingMode.HALF_UP));
                }
            } catch (NumberFormatException ignored) {
                // 正则已限制格式；保留原始文本即可。
            }
        }
    }

    private void addDisplayVariants(LinkedHashSet<String> values, BigDecimal number) {
        addDisplayVariantsForSign(values, number);
        if (number.signum() < 0) addDisplayVariantsForSign(values, number.abs());
    }

    private void addDisplayVariantsForSign(LinkedHashSet<String> values, BigDecimal number) {
        values.add(number.stripTrailingZeros().toPlainString());
        for (int scale = 0; scale <= 2; scale++) {
            values.add(number.setScale(scale, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString());
            values.add(number.setScale(scale, RoundingMode.DOWN)
                    .stripTrailingZeros().toPlainString());
        }
    }

    private static String token(String value) {
        return safe(value).toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }

    private static String safe(String value) {
        return value == null ? "UNKNOWN" : value;
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
