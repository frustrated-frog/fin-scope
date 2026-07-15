package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalHistoryQuality;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 评价输入质量门禁；不将自然日间隔误当作交易日缺口。 */
@Service
public class CapitalHistoryQualityGate {
    public static final String VERSION = "capital-history-quality-v1";
    public static final int MINIMUM_DAILY_SAMPLES = 60;
    private static final int MAX_STALE_CALENDAR_DAYS = 7;
    private static final BigDecimal MINIMUM_PRICE_COVERAGE = new BigDecimal("0.95");
    private static final BigDecimal MINIMUM_AMOUNT_COVERAGE = new BigDecimal("0.90");
    private static final int SCALE = 6;

    public CapitalHistoryQuality evaluate(List<CapitalFlowPoint> facts, LocalDate asOfDate) {
        if (asOfDate == null) throw new IllegalArgumentException("history quality as-of date is required");
        Map<LocalDate, CapitalFlowPoint> unique = new LinkedHashMap<LocalDate, CapitalFlowPoint>();
        int duplicateDates = 0;
        if (facts != null) {
            for (CapitalFlowPoint point : facts) {
                if (point == null || !"DAY_1".equals(point.getGranularity()) || point.getDataDate() == null) continue;
                if (unique.put(point.getDataDate(), point) != null) duplicateDates++;
            }
        }
        int samples = unique.size();
        int priced = 0;
        int amounted = 0;
        LocalDate latest = null;
        for (CapitalFlowPoint point : unique.values()) {
            if (positive(point.getPrice())) priced++;
            if (positive(point.getIntervalTradeAmount())) amounted++;
            if (latest == null || point.getDataDate().isAfter(latest)) latest = point.getDataDate();
        }
        BigDecimal priceCoverage = rate(priced, samples);
        BigDecimal amountCoverage = rate(amounted, samples);
        List<String> gaps = new ArrayList<String>();
        if (samples < MINIMUM_DAILY_SAMPLES) {
            gaps.add("历史日线仅 " + samples + " 个交易日，至少需要 " + MINIMUM_DAILY_SAMPLES + " 个。");
        }
        if (latest == null) {
            gaps.add("历史日线没有有效交易日期。");
        } else if (latest.isBefore(asOfDate.minusDays(MAX_STALE_CALENDAR_DAYS))) {
            gaps.add("最新历史数据停留在 " + latest + "，超过 7 个自然日未更新。");
        }
        if (priceCoverage.compareTo(MINIMUM_PRICE_COVERAGE) < 0) {
            gaps.add("价格覆盖率 " + percent(priceCoverage) + "，低于 95% 评价门槛。");
        }
        if (amountCoverage.compareTo(MINIMUM_AMOUNT_COVERAGE) < 0) {
            gaps.add("成交额覆盖率 " + percent(amountCoverage) + "，低于 90% 评价门槛。");
        }
        if (duplicateDates > 0) {
            gaps.add("检测到 " + duplicateDates + " 个重复交易日，已拒绝扩大样本统计。");
        }
        CapitalHistoryQuality result = new CapitalHistoryQuality();
        result.setStatus(gaps.isEmpty() ? "RELIABLE" : "DATA_UNRELIABLE");
        result.setDailySampleCount(samples);
        result.setPriceCoverageRate(priceCoverage);
        result.setAmountCoverageRate(amountCoverage);
        result.setLatestDataDate(latest);
        result.setDataGaps(gaps);
        return result;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private BigDecimal rate(int numerator, int denominator) {
        if (denominator <= 0) return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), SCALE, RoundingMode.HALF_UP);
    }

    private String percent(BigDecimal value) {
        return value.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%";
    }
}
