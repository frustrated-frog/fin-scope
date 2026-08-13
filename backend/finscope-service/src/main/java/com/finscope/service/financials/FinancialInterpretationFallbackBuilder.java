package com.finscope.service.financials;

import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.common.enums.financials.FinancialInterpretationStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class FinancialInterpretationFallbackBuilder {
    private static final java.util.List<String> DIMENSIONS = Arrays.asList(
            "GROWTH", "PROFITABILITY", "EARNINGS_QUALITY", "CASH_QUALITY",
            "ASSET_QUALITY", "SOLVENCY_CAPITAL_DISCIPLINE");

    public FinancialInterpretation build(FinancialEvidencePacket packet, String reason) {
        FinancialInterpretation value = new FinancialInterpretation();
        value.setReportId(packet.getReportId());
        value.setPromptVersion(packet.getPromptVersion());
        value.setStatus(FinancialInterpretationStatus.FALLBACK.code());
        value.setGenerationMode("DETERMINISTIC_FALLBACK");
        value.setFailureCode(reason);
        FinancialInterpretation.Result result = new FinancialInterpretation.Result();
        result.setOperatingState(packet.getEvidence().isEmpty()
                ? "INSUFFICIENT_EVIDENCE" : "STABLE");
        result.setConfidence("LOW");
        FinancialEvidence primary = packet.getEvidence().stream()
                .filter(item -> "METRIC".equals(item.getType()) || "FINDING".equals(item.getType()))
                .findFirst().orElse(packet.getEvidence().isEmpty() ? null : packet.getEvidence().get(0));
        FinancialInterpretation.Claim summary = new FinancialInterpretation.Claim();
        summary.setClaim(primary == null ? "当前结构化证据不足，暂不能形成可靠财报判断。"
                : primary.getLabel() + (primary.getValue() == null ? "" : "为" + primary.getValue()
                + (primary.getUnit() == null ? "" : primary.getUnit())));
        summary.setClaimType("FACT");
        if (primary != null) summary.getRefs().add(primary.getId());
        result.getExecutiveSummary().add(summary);
        for (String code : DIMENSIONS) {
            FinancialInterpretation.Dimension dimension = new FinancialInterpretation.Dimension();
            dimension.setCode(code);
            dimension.setAssessment("INSUFFICIENT_EVIDENCE");
            dimension.setSummary("当前维度由规则结果兜底展示，需结合更多可比期证据复核。");
            if (primary != null) dimension.getRefs().add(primary.getId());
            result.getDimensions().add(dimension);
        }
        packet.getEvidence().stream().filter(item -> "DATA_GAP".equals(item.getType()))
                .map(FinancialEvidence::getDetail).forEach(result.getLimitations()::add);
        if (result.getLimitations().isEmpty()) {
            result.getLimitations().add("模型解读不可用，当前展示确定性规则结果。");
        }
        result.setDisclaimer("规则解读仅用于研究，不构成投资建议。");
        value.setResult(result);
        return value;
    }
}
