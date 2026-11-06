package com.finscope.domain.watchlistresearch;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.domain.instrument.StockFinancialQuarter;
import com.finscope.domain.instrument.WatchlistItem;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** 只从实际单季事实计算滚动四季指标，缺失和不适用值不补零。 */
public final class GrowthQualityCalculator {
    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private GrowthQualityCalculator() {
    }

    public static StockGrowthQuality analyze(WatchlistItem stock, List<StockFinancialQuarter> input,
                                              LocalDate observedDate) {
        StockGrowthQuality result = new StockGrowthQuality();
        result.setCode(stock.getCode());
        result.setName(stock.getName());
        List<StockFinancialQuarter> quarters = new ArrayList<StockFinancialQuarter>();
        for (StockFinancialQuarter quarter : input) {
            if (quarter.getPeriodEnd() != null && !quarter.getPeriodEnd().isAfter(observedDate)) {
                quarters.add(quarter);
            }
        }
        quarters.sort(Comparator.comparing(StockFinancialQuarter::getPeriodEnd));
        if (quarters.size() < 4) {
            result.getGaps().add("连续单季度财报不足四期");
            return result;
        }
        List<StockFinancialQuarter> current = quarters.subList(quarters.size() - 4, quarters.size());
        if (!compatible(current)) {
            result.getGaps().add("最近四季存在期间、币种、来源或报告质量缺口");
            return result;
        }
        StockFinancialQuarter last = current.get(3);
        result.setCurrency(last.getCurrency());
        result.setSourceCode(last.getSourceCode());
        result.setPeriodFrom(current.get(0).getPeriodEnd());
        result.setPeriodThrough(last.getPeriodEnd());
        result.setRevenueTtm(sum(current, StockFinancialQuarter::getRevenue));
        result.setProfitTtm(sum(current, StockFinancialQuarter::getNetProfitParent));
        result.setOperatingCashFlowTtm(sum(current, StockFinancialQuarter::getOperatingCashFlow));
        result.setCashConversion(ratio(result.getOperatingCashFlowTtm(), result.getProfitTtm()));
        result.setGrossMarginPct(weightedMargin(current));
        for (StockFinancialQuarter quarter : current) {
            if (quarter.getQualityStatus() == FinancialQualityStatus.STALE) {
                result.getGaps().add(quarter.getPeriodEnd() + " 使用已有财报快照");
            }
            result.getGaps().addAll(quarter.getDataGaps());
        }
        if (quarters.size() < 8) {
            result.getGaps().add("前一年度的四季度样本不足，增长对比留空");
            return result;
        }
        List<StockFinancialQuarter> eight = quarters.subList(quarters.size() - 8, quarters.size());
        if (!compatible(eight)) {
            result.getGaps().add("跨年度财报口径不一致或不连续，增长对比留空");
            return result;
        }
        List<StockFinancialQuarter> previous = eight.subList(0, 4);
        result.setRevenueGrowthPct(growth(result.getRevenueTtm(), sum(previous, StockFinancialQuarter::getRevenue)));
        result.setProfitGrowthPct(growth(result.getProfitTtm(), sum(previous, StockFinancialQuarter::getNetProfitParent)));
        Double oldMargin = weightedMargin(previous);
        if (oldMargin != null && result.getGrossMarginPct() != null) {
            result.setGrossMarginChangePp(result.getGrossMarginPct() - oldMargin);
        }
        return result;
    }

    private static boolean compatible(List<StockFinancialQuarter> quarters) {
        StockFinancialQuarter first = quarters.get(0);
        if (first.getCurrency() == null || first.getCurrency().isBlank()
                || first.getSourceCode() == null || first.getSourceCode().isBlank()) {
            return false;
        }
        LocalDate previous = null;
        for (StockFinancialQuarter quarter : quarters) {
            LocalDate date = quarter.getPeriodEnd();
            FinancialQualityStatus quality = quarter.getQualityStatus();
            if (date.getMonthValue() % 3 != 0 || !date.equals(YearMonth.from(date).atEndOfMonth())
                    || (previous != null && !date.equals(YearMonth.from(previous).plusMonths(3).atEndOfMonth()))
                    || !Objects.equals(first.getCurrency(), quarter.getCurrency())
                    || !Objects.equals(first.getSourceCode(), quarter.getSourceCode())
                    || (quality != FinancialQualityStatus.FRESH && quality != FinancialQualityStatus.PARTIAL
                    && quality != FinancialQualityStatus.STALE)) {
                return false;
            }
            previous = date;
        }
        return true;
    }

    private static BigDecimal sum(List<StockFinancialQuarter> quarters,
                                   Function<StockFinancialQuarter, BigDecimal> metric) {
        BigDecimal total = BigDecimal.ZERO;
        for (StockFinancialQuarter quarter : quarters) {
            BigDecimal value = metric.apply(quarter);
            if (value == null) {
                return null;
            }
            total = total.add(value);
        }
        return total;
    }

    private static Double weightedMargin(List<StockFinancialQuarter> quarters) {
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        for (StockFinancialQuarter quarter : quarters) {
            if (quarter.getRevenue() == null || quarter.getRevenue().signum() <= 0
                    || quarter.getGrossMarginPct() == null) {
                return null;
            }
            revenue = revenue.add(quarter.getRevenue());
            weighted = weighted.add(quarter.getRevenue().multiply(quarter.getGrossMarginPct()));
        }
        return weighted.divide(revenue, PRECISION).doubleValue();
    }

    private static Double ratio(BigDecimal numerator, BigDecimal positiveBase) {
        if (numerator == null || positiveBase == null || positiveBase.signum() <= 0) {
            return null;
        }
        return numerator.divide(positiveBase, PRECISION).doubleValue();
    }

    private static Double growth(BigDecimal current, BigDecimal previous) {
        Double ratio = ratio(current, previous);
        return ratio == null ? null : (ratio - 1.0) * 100.0;
    }
}
