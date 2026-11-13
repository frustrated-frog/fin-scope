package com.finscope.domain.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalComparisonState;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** 仅对照同类累计报告的事实变化，不把自由文本验证条件转换成自动结论。 */
public class FundamentalFinancialComparison {
    public FundamentalWatchComparison compare(FundamentalFinancialBaseline baseline, FundamentalFinancialBaseline current) {
        var result = new FundamentalWatchComparison();
        result.setBaseline(baseline);
        result.setCurrent(current);
        if (baseline == null) {
            result.setState(FundamentalComparisonState.LEGACY);
            result.setMessage("此项未保存结构化财务基线，可继续按原始记录复核。");
            return result;
        }
        if (current == null || !current.getPeriodEnd().isAfter(baseline.getPeriodEnd())) {
            result.setState(FundamentalComparisonState.WAITING);
            result.setMessage("基线已保存，等待下一份同期间、同口径财报。");
            return result;
        }
        if (!comparable(baseline, current)) {
            result.setState(FundamentalComparisonState.INCOMPARABLE);
            result.setMessage("已有较新财报，报告期间、来源或币种与基线不同，暂不直接计算变化。");
            return result;
        }
        for (FundamentalMetric original : baseline.getMetrics()) {
            var latest = current.getMetrics().stream().filter(value -> Objects.equals(value.getConceptCode(), original.getConceptCode())
                    && Objects.equals(value.getUnit(), original.getUnit())).findFirst().orElse(null);
            if (latest == null || latest.getValue() == null || original.getValue() == null) {
                continue;
            }
            var change = new FundamentalMetricChange();
            change.setConceptCode(original.getConceptCode());
            change.setLabel(original.getLabel());
            change.setUnit(original.getUnit());
            change.setBaseline(original.getValue());
            change.setCurrent(latest.getValue());
            change.setChange(latest.getValue().subtract(original.getValue()));
            result.getChanges().add(change);
        }
        if (result.getChanges().isEmpty()) {
            result.setState(FundamentalComparisonState.INCOMPARABLE);
            result.setMessage("较新同期间财报暂缺可比指标，保留基线等待补齐。");
            return result;
        }
        result.setState(FundamentalComparisonState.UPDATED);
        result.setMessage("已有后续同口径财报，可对照 " + result.getChanges().size() + " 项指标复核当前判断。");
        result.setToken(fingerprint(baseline, current));
        return result;
    }

    public boolean comparable(FundamentalFinancialBaseline baseline, FundamentalFinancialBaseline current) {
        return baseline.getPeriodEnd().getMonthValue() == current.getPeriodEnd().getMonthValue()
                && baseline.getPeriodEnd().getDayOfMonth() == current.getPeriodEnd().getDayOfMonth()
                && Objects.equals(baseline.getReportType(), current.getReportType())
                && Objects.equals(baseline.getScope(), current.getScope())
                && Objects.equals(baseline.getCurrency(), current.getCurrency())
                && Objects.equals(baseline.getSourceCode(), current.getSourceCode())
                && baseline.getDimension() == current.getDimension();
    }

    private String fingerprint(FundamentalFinancialBaseline baseline, FundamentalFinancialBaseline current) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(baseline.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(current.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("财务对照指纹生成失败", error);
        }
    }
}
