package com.finscope.domain.watchlistresearch;

import com.finscope.domain.instrument.DailyBarHistory;
import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.domain.instrument.WatchlistItem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 确定性日收益研究；价格、样本区间及训练/评价边界保持同一口径。 */
public final class MarketResearchCalculator {
    private static final int EVALUATION_DAYS = 20;
    private static final int MAX_TRAINING_DAYS = 120;
    private static final int MIN_TRAINING_DAYS = 60;
    private static final int RELATIONSHIP_DAYS = 60;
    private static final int MIN_RELATIONSHIP_DAYS = 50;
    private static final double MIN_VARIANCE = 1e-14;

    private MarketResearchCalculator() {
    }

    public static StockStrengthAnalysis strength(WatchlistItem stock, DailyBarHistory history,
                                                  DailyBarHistory benchmark) {
        StockStrengthAnalysis result = new StockStrengthAnalysis();
        result.setCode(stock.getCode());
        result.setName(stock.getName());
        result.setSourceCode(history == null ? null : history.getSourceCode());
        Map<Interval, Double> own = returns(history);
        Map<Interval, Double> market = returns(benchmark);
        List<Interval> calendar = new ArrayList<Interval>(market.keySet());
        if (own.isEmpty() || calendar.size() < MIN_TRAINING_DAYS + EVALUATION_DAYS) {
            result.getGaps().add("需要已完成的同口径前复权日线，以及至少80个共同日收益样本");
            return result;
        }
        List<Interval> evaluation = calendar.subList(calendar.size() - EVALUATION_DAYS, calendar.size());
        if (!own.keySet().containsAll(evaluation)) {
            result.getGaps().add("最近20个基准交易区间存在停牌或行情缺口，暂不比较强弱");
            return result;
        }
        List<Interval> training = new ArrayList<Interval>();
        for (Interval interval : calendar.subList(0, calendar.size() - EVALUATION_DAYS)) {
            if (own.containsKey(interval)) {
                training.add(interval);
            }
        }
        training = tail(training, MAX_TRAINING_DAYS);
        result.setTrainingSamples(training.size());
        if (training.size() < MIN_TRAINING_DAYS) {
            result.getGaps().add("评价期之前的共同训练样本不足60日");
            return result;
        }
        double[] x = values(market, training);
        double[] y = values(own, training);
        Fit fit = fit(x, y);
        if (fit == null) {
            result.getGaps().add("基准训练期波动不足，无法建立稳定统计基线");
            return result;
        }
        double actual = 1.0;
        double baseline = 1.0;
        int positive = 0;
        for (Interval interval : evaluation) {
            double predicted = fit.alpha + fit.beta * market.get(interval);
            if (predicted <= -1.0) {
                result.getGaps().add("模型基线超出日收益有效范围，暂不输出比较");
                return result;
            }
            actual *= 1.0 + own.get(interval);
            baseline *= 1.0 + predicted;
            if (own.get(interval) > predicted + 1e-10) {
                positive++;
            }
        }
        result.setTrainingFrom(training.get(0).start);
        result.setTrainingThrough(training.get(training.size() - 1).end);
        result.setEvaluatedFrom(evaluation.get(0).start);
        result.setEvaluatedThrough(evaluation.get(evaluation.size() - 1).end);
        result.setEvaluationSamples(evaluation.size());
        result.setBeta(fit.beta);
        result.setRSquared(fit.rSquared);
        result.setActualReturnPct((actual - 1.0) * 100.0);
        result.setBaselineReturnPct((baseline - 1.0) * 100.0);
        result.setExcessReturnPp((actual - baseline) * 100.0);
        result.setPositiveResidualDays(positive);
        return result;
    }

    public static List<StockRelationship> relationships(List<WatchlistItem> stocks,
                                                         Map<String, DailyBarHistory> histories,
                                                         DailyBarHistory benchmark) {
        Map<Interval, Double> market = returns(benchmark);
        List<Interval> calendar = tail(new ArrayList<Interval>(market.keySet()), RELATIONSHIP_DAYS);
        Map<String, Map<Interval, Double>> series = new LinkedHashMap<String, Map<Interval, Double>>();
        for (WatchlistItem stock : stocks) {
            series.put(stock.getCode(), returns(histories.get(stock.getCode())));
        }
        List<StockRelationship> result = new ArrayList<StockRelationship>();
        for (int left = 0; left < stocks.size(); left++) {
            for (int right = left + 1; right < stocks.size(); right++) {
                StockRelationship relation = relationship(stocks.get(left), stocks.get(right),
                        series, market, calendar);
                if (relation != null) {
                    result.add(relation);
                }
            }
        }
        result.sort(Comparator.comparing(StockRelationship::isStrong).reversed()
                .thenComparing(StockRelationship::getResidualCorrelation, Comparator.reverseOrder())
                .thenComparing(StockRelationship::getLeftCode)
                .thenComparing(StockRelationship::getRightCode));
        return new ArrayList<StockRelationship>(result.subList(0, Math.min(12, result.size())));
    }

    private static StockRelationship relationship(WatchlistItem left, WatchlistItem right,
                                                   Map<String, Map<Interval, Double>> series,
                                                   Map<Interval, Double> market, List<Interval> calendar) {
        Map<Interval, Double> a = series.get(left.getCode());
        Map<Interval, Double> b = series.get(right.getCode());
        List<Interval> common = new ArrayList<Interval>();
        for (Interval interval : calendar) {
            if (a.containsKey(interval) && b.containsKey(interval)) {
                common.add(interval);
            }
        }
        if (common.size() < MIN_RELATIONSHIP_DAYS || calendar.isEmpty()
                || !common.get(common.size() - 1).equals(calendar.get(calendar.size() - 1))) {
            return null;
        }
        double[] x = values(a, common);
        double[] y = values(b, common);
        double[] m = values(market, common);
        Fit leftFit = fit(m, x);
        Fit rightFit = fit(m, y);
        Double raw = correlation(x, y);
        if (leftFit == null || rightFit == null || raw == null) {
            return null;
        }
        Double residual = correlation(residuals(m, x, leftFit), residuals(m, y, rightFit));
        if (residual == null) {
            return null;
        }
        List<Interval> downside = new ArrayList<Interval>();
        for (Interval interval : common) {
            if (market.get(interval) < 0.0) {
                downside.add(interval);
            }
        }
        StockRelationship result = new StockRelationship();
        result.setLeftCode(left.getCode());
        result.setLeftName(left.getName());
        result.setRightCode(right.getCode());
        result.setRightName(right.getName());
        result.setFrom(common.get(0).start);
        result.setThrough(common.get(common.size() - 1).end);
        result.setSamples(common.size());
        result.setCorrelation(raw);
        result.setResidualCorrelation(residual);
        result.setDownsideSamples(downside.size());
        if (downside.size() >= 15) {
            result.setDownsideCorrelation(correlation(values(a, downside), values(b, downside)));
        }
        result.setStrong(raw >= 0.75 && residual >= 0.50);
        return result;
    }

    private static Map<Interval, Double> returns(DailyBarHistory history) {
        Map<Interval, Double> result = new TreeMap<Interval, Double>();
        if (history == null || history.getBars() == null || history.getBars().size() < 2
                || "UNAVAILABLE".equals(history.getQualityStatus())
                || "CONFLICT".equals(history.getQualityStatus())) {
            return result;
        }
        LocalDate previousDate = null;
        double previousClose = 0.0;
        for (DailyBarPoint point : history.getBars()) {
            if (point.getTradeDate() == null || !"QFQ".equals(point.getAdjustment())
                    || !Boolean.TRUE.equals(point.getIsComplete()) || point.getClose() == null
                    || point.getClose().signum() <= 0
                    || (previousDate != null && !point.getTradeDate().isAfter(previousDate))) {
                return Collections.emptyMap();
            }
            double close = point.getClose().doubleValue();
            if (!Double.isFinite(close)) {
                return Collections.emptyMap();
            }
            if (previousDate != null) {
                result.put(new Interval(previousDate, point.getTradeDate()), close / previousClose - 1.0);
            }
            previousDate = point.getTradeDate();
            previousClose = close;
        }
        return result;
    }

    private static Fit fit(double[] x, double[] y) {
        double xx = covariance(x, x);
        if (xx < MIN_VARIANCE) {
            return null;
        }
        double beta = covariance(x, y) / xx;
        double yy = covariance(y, y);
        double rSquared = yy < MIN_VARIANCE ? 0.0
                : Math.max(0.0, Math.min(1.0, beta * beta * xx / yy));
        return new Fit(mean(y) - beta * mean(x), beta, rSquared);
    }

    private static Double correlation(double[] x, double[] y) {
        double xx = covariance(x, x);
        double yy = covariance(y, y);
        if (xx < MIN_VARIANCE || yy < MIN_VARIANCE) {
            return null;
        }
        return Math.max(-1.0, Math.min(1.0, covariance(x, y) / Math.sqrt(xx * yy)));
    }

    private static double covariance(double[] x, double[] y) {
        double mx = mean(x);
        double my = mean(y);
        double sum = 0.0;
        for (int index = 0; index < x.length; index++) {
            sum += (x[index] - mx) * (y[index] - my);
        }
        return sum / x.length;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double[] residuals(double[] market, double[] own, Fit fit) {
        double[] result = new double[market.length];
        for (int index = 0; index < market.length; index++) {
            result[index] = own[index] - fit.alpha - fit.beta * market[index];
        }
        return result;
    }

    private static double[] values(Map<Interval, Double> source, List<Interval> intervals) {
        double[] result = new double[intervals.size()];
        for (int index = 0; index < intervals.size(); index++) {
            result[index] = source.get(intervals.get(index));
        }
        return result;
    }

    private static <T> List<T> tail(List<T> values, int limit) {
        return values.subList(Math.max(0, values.size() - limit), values.size());
    }

    private record Fit(double alpha, double beta, double rSquared) {
    }

    private record Interval(LocalDate start, LocalDate end) implements Comparable<Interval> {
        @Override
        public int compareTo(Interval other) {
            int compared = end.compareTo(other.end);
            return compared == 0 ? start.compareTo(other.start) : compared;
        }
    }
}
