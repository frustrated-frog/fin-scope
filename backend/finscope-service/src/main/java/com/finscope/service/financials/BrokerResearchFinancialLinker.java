package com.finscope.service.financials;

import com.finscope.domain.financials.BrokerResearchClaim;
import com.finscope.domain.financials.BrokerResearchForecast;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.common.enums.financials.FinancialReportType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class BrokerResearchFinancialLinker {

    public void link(FinancialReportView financial, List<BrokerResearchForecast> forecasts,
                     List<BrokerResearchClaim> claims) {
        for (BrokerResearchForecast forecast : forecasts) linkForecast(financial, forecast);
        for (BrokerResearchClaim claim : claims) linkClaim(financial, claim);
    }

    private void linkForecast(FinancialReportView financial, BrokerResearchForecast forecast) {
        if (financial == null || financial.getReport() == null) {
            insufficient(forecast, "尚未选择可用于验证的财报");
            return;
        }
        if (forecast.getForecastPeriod() != null
                && financial.getReport().getPeriodEnd().isBefore(forecast.getForecastPeriod())) {
            forecast.setVerificationStatus("PENDING");
            forecast.setVerificationReason("预测期尚未结束，等待后续财报验证");
            return;
        }
        if (forecast.getForecastPeriod() == null
                || !financial.getReport().getPeriodEnd().equals(forecast.getForecastPeriod())) {
            insufficient(forecast, "当前财报与预测财年不一致，不能直接计算偏差");
            return;
        }
        if (financial.getReport().getReportType() != FinancialReportType.ANNUAL) {
            insufficient(forecast, "研报盈利预测按财年展示，请选择对应年度报告验证");
            return;
        }
        Fact fact = fact(financial, forecast.getMetricCode());
        if (fact == null || fact.value == null || forecast.getForecastValue() == null) {
            insufficient(forecast, "当前财报缺少同口径实际值");
            return;
        }
        BigDecimal comparableForecast = comparableForecastValue(forecast);
        if (comparableForecast == null) {
            insufficient(forecast, "研报预测单位无法与财报标准单位安全换算");
            return;
        }
        forecast.setActualValue(fact.value);
        forecast.setActualUnit(fact.unit);
        forecast.setActualPeriod(financial.getReport().getPeriodEnd());
        if (comparableForecast.compareTo(BigDecimal.ZERO) == 0) {
            insufficient(forecast, "预测值为零，无法计算相对偏差");
            return;
        }
        BigDecimal variance;
        if ("GROSS_MARGIN".equals(forecast.getMetricCode())) {
            variance = fact.value.subtract(comparableForecast).setScale(2, RoundingMode.HALF_UP);
        } else {
            variance = fact.value.subtract(comparableForecast)
                    .divide(comparableForecast.abs(), 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        }
        forecast.setVariancePercent(variance);
        if (variance.abs().compareTo(new BigDecimal("10")) <= 0) {
            forecast.setVerificationStatus("VERIFIED");
            forecast.setVerificationReason("GROSS_MARGIN".equals(forecast.getMetricCode())
                    ? "实际毛利率与预测值相差不超过10个百分点"
                    : "实际值与预测值的偏差不超过10%");
        } else {
            forecast.setVerificationStatus("CONTRADICTED");
            forecast.setVerificationReason("GROSS_MARGIN".equals(forecast.getMetricCode())
                    ? "实际毛利率与预测值相差超过10个百分点，需要复核预测假设和口径"
                    : "实际值与预测值的偏差超过10%，需要复核预测假设和口径");
        }
    }

    private void linkClaim(FinancialReportView financial, BrokerResearchClaim claim) {
        if (financial == null || financial.getReport() == null) {
            claim.setVerificationStatus("INSUFFICIENT_EVIDENCE");
            claim.setVerificationReason("尚未选择财报");
            return;
        }
        Fact fact = null;
        if (claim.getFinancialMetricCode() != null) fact = metric(financial, claim.getFinancialMetricCode());
        if (fact == null && claim.getFinancialConceptCode() != null) fact = concept(financial, claim.getFinancialConceptCode());
        if (fact == null) {
            claim.setVerificationStatus("INSUFFICIENT_EVIDENCE");
            claim.setVerificationReason("当前财报没有与该观点直接对应的结构化证据");
            return;
        }
        claim.setEvidenceLabel(fact.label);
        claim.setEvidenceValue(fact.value == null ? null : fact.value.stripTrailingZeros().toPlainString());
        claim.setEvidenceUnit(fact.unit);
        claim.setEvidencePeriod(financial.getReport().getPeriodEnd());
        claim.setVerificationStatus("EVIDENCE_FOUND");
        claim.setVerificationReason("已找到财报事实，请结合研报论证方向人工核对");
    }

    private Fact fact(FinancialReportView financial, String code) {
        Fact metric = metric(financial, code);
        return metric != null ? metric : concept(financial, code);
    }

    private Fact metric(FinancialReportView financial, String code) {
        if (code == null) return null;
        for (FinancialMetric item : financial.getMetrics()) {
            if (code.equals(item.getMetricCode()) && item.getValue() != null) {
                return new Fact(item.getLabel(), item.getValue(), item.getUnit());
            }
        }
        return null;
    }

    private Fact concept(FinancialReportView financial, String code) {
        if (code == null) return null;
        Fact fallback = null;
        for (List<FinancialLineItem> statement : financial.getStatements().values()) {
            for (FinancialLineItem item : statement) {
                if (code.equals(item.getConceptCode()) && item.getNormalizedValue() != null) {
                    String unit = item.getCurrency() == null ? financial.getReport().getCurrency() : item.getCurrency();
                    Fact value = new Fact(item.getSourceLabel(), item.getNormalizedValue(), unit);
                    if ("CURRENT_YTD".equals(item.getPeriodRole())
                            || "CURRENT_PERIOD_END".equals(item.getPeriodRole())) return value;
                    if (fallback == null) fallback = value;
                }
            }
        }
        return fallback;
    }

    private BigDecimal comparableForecastValue(BrokerResearchForecast forecast) {
        String unit = forecast.getUnit() == null ? "" : forecast.getUnit().trim().toUpperCase();
        if ("GROSS_MARGIN".equals(forecast.getMetricCode())) {
            return "%".equals(unit) || "PERCENT".equals(unit) || "百分比".equals(unit)
                    ? forecast.getForecastValue() : null;
        }
        if ("EPS".equals(forecast.getMetricCode())) {
            return "CNY/SHARE".equals(unit) || "RMB/SHARE".equals(unit)
                    || "元/股".equals(unit) || "元".equals(unit)
                    ? forecast.getForecastValue() : null;
        }
        BigDecimal multiplier;
        if ("CNY".equals(unit) || "RMB".equals(unit) || "元".equals(unit)) {
            multiplier = BigDecimal.ONE;
        } else if ("万元".equals(unit)) {
            multiplier = new BigDecimal("10000");
        } else if ("百万元".equals(unit)) {
            multiplier = new BigDecimal("1000000");
        } else if ("亿元".equals(unit)) {
            multiplier = new BigDecimal("100000000");
        } else {
            return null;
        }
        return forecast.getForecastValue().multiply(multiplier);
    }

    private void insufficient(BrokerResearchForecast forecast, String reason) {
        forecast.setVerificationStatus("INSUFFICIENT_EVIDENCE");
        forecast.setVerificationReason(reason);
    }

    private static final class Fact {
        private final String label;
        private final BigDecimal value;
        private final String unit;
        private Fact(String label, BigDecimal value, String unit) {
            this.label = label;
            this.value = value;
            this.unit = unit;
        }
    }
}
