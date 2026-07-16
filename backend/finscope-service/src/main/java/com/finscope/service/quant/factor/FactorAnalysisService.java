package com.finscope.service.quant.factor;

import com.finscope.domain.quant.factor.FactorAnalysis;
import com.finscope.domain.quant.factor.FactorHorizonAnalysis;
import com.finscope.domain.quant.factor.FactorRobustnessReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class FactorAnalysisService {
    public double rankIc(List<Double> factors, List<Double> returns) {
        if (factors == null || returns == null || factors.size() != returns.size() || factors.size() < 2) return Double.NaN;
        return correlation(ranks(factors), ranks(returns));
    }

    public FactorAnalysis summarize(String factorCode, List<Double> values) {
        FactorAnalysis result = new FactorAnalysis(); result.setFactorCode(factorCode);
        List<Double> finite = new ArrayList<Double>();
        for (Double value : values) if (value != null && Double.isFinite(value)) finite.add(value);
        result.setSampleCount(finite.size());
        if (finite.isEmpty()) return result;
        double mean = finite.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = 0; int positive = 0, negative = 0, zero = 0;
        for (double value : finite) {
            variance += (value - mean) * (value - mean);
            if (value > 0) positive++; else if (value < 0) negative++; else zero++;
        }
        double std = finite.size() < 2 ? 0 : Math.sqrt(variance / (finite.size() - 1));
        result.setIcMean(mean); result.setIcStd(std); result.setIcIr(std == 0 ? 0 : mean / std);
        result.setPositiveIcRatio((double) positive / finite.size());
        result.setNegativeIcRatio((double) negative / finite.size());
        result.setZeroIcRatio((double) zero / finite.size());
        double margin = 1.96d * hacMeanStandardError(finite, mean);
        result.setIcMeanCiLower(mean - margin); result.setIcMeanCiUpper(mean + margin);
        return result;
    }

    public double quantileSpread(List<Double> factors, List<Double> returns) {
        if (!validPair(factors, returns) || factors.size() < 10) return Double.NaN;
        List<Integer> order = order(factors); int bucket = factors.size() / 5;
        double low = 0d, high = 0d;
        for (int i = 0; i < bucket; i++) { low += returns.get(order.get(i)); high += returns.get(order.get(order.size() - 1 - i)); }
        return high / bucket - low / bucket;
    }

    public double quantileMonotonicity(List<Double> factors, List<Double> returns) {
        if (!validPair(factors, returns) || factors.size() < 10) return Double.NaN;
        List<Integer> order = order(factors); List<Double> groups = new ArrayList<Double>();
        for (int bucket = 0; bucket < 5; bucket++) {
            int from = bucket * order.size() / 5, to = (bucket + 1) * order.size() / 5; double sum = 0d;
            for (int i = from; i < to; i++) sum += returns.get(order.get(i));
            groups.add(sum / (to - from));
        }
        return rankIc(java.util.Arrays.asList(1d, 2d, 3d, 4d, 5d), groups);
    }

    public void attachQuantileEvidence(FactorAnalysis result, List<Double> spreads, List<Double> monotonicities) {
        List<Double> finiteSpread = finite(spreads), finiteMonotonicity = finite(monotonicities);
        result.setQuantileSampleDays(Math.min(finiteSpread.size(), finiteMonotonicity.size()));
        result.setQuantileSpreadMean(mean(finiteSpread));
        result.setFavorableQuantileSpreadRatio(finiteSpread.isEmpty() ? 0d
                : (double) finiteSpread.stream().filter(value -> value > 0d).count() / finiteSpread.size());
        result.setQuantileMonotonicityMean(mean(finiteMonotonicity));
    }

    public FactorHorizonAnalysis summarizeHorizon(int horizonDays, List<Double> icValues,
                                                   int totalEligibleDays, int minCrossSectionSize,
                                                   List<Double> spreads, List<Double> monotonicities) {
        FactorAnalysis summary = summarize("", icValues);
        attachQuantileEvidence(summary, spreads, monotonicities);
        FactorHorizonAnalysis result = new FactorHorizonAnalysis();
        result.setHorizonDays(horizonDays);
        result.setSampleCount(summary.getSampleCount());
        result.setTotalEligibleDays(totalEligibleDays);
        result.setMinCrossSectionSize(minCrossSectionSize == Integer.MAX_VALUE ? 0 : minCrossSectionSize);
        result.setCoverageRatio(totalEligibleDays == 0 ? 0d : (double) summary.getSampleCount() / totalEligibleDays);
        result.setIcMean(summary.getIcMean());
        result.setIcStd(summary.getIcStd());
        result.setIcIr(summary.getIcIr());
        result.setPositiveIcRatio(summary.getPositiveIcRatio());
        result.setNegativeIcRatio(summary.getNegativeIcRatio());
        result.setIcMeanCiLower(summary.getIcMeanCiLower());
        result.setIcMeanCiUpper(summary.getIcMeanCiUpper());
        result.setDirectionAdjustedQuantileSpread(summary.getQuantileSpreadMean());
        result.setDirectionAdjustedMonotonicity(summary.getQuantileMonotonicityMean());
        result.setFavorableIcRatio(summary.getPositiveIcRatio());
        result.setDirectionAdjustedIcMean(summary.getIcMean());
        return result;
    }

    public FactorRobustnessReport robustness(List<Double> primaryIc,
                                             List<Map<String, Double>> factorSnapshots) {
        List<Double> finiteIc = finite(primaryIc);
        int split = finiteIc.size() < 2 ? finiteIc.size() : Math.max(1, (int) Math.floor(finiteIc.size() * 0.70d));
        List<Double> inSample = new ArrayList<Double>(finiteIc.subList(0, split));
        List<Double> outOfSample = new ArrayList<Double>(finiteIc.subList(split, finiteIc.size()));
        FactorRobustnessReport result = new FactorRobustnessReport();
        result.setInSampleCount(inSample.size());
        result.setOutOfSampleCount(outOfSample.size());
        result.setInSampleIcMean(mean(inSample));
        result.setOutOfSampleIcMean(mean(outOfSample));
        result.setRankTurnoverProxy(rankTurnoverProxy(factorSnapshots));
        return result;
    }

    private double rankTurnoverProxy(List<Map<String, Double>> snapshots) {
        if (snapshots == null || snapshots.size() < 2) return 0d;
        List<Double> changes = new ArrayList<Double>();
        for (int i = 1; i < snapshots.size(); i++) {
            Map<String, Double> previous = snapshots.get(i - 1);
            Map<String, Double> current = snapshots.get(i);
            List<String> common = new ArrayList<String>();
            for (String code : previous.keySet()) if (current.containsKey(code)) common.add(code);
            if (common.size() < 2) continue;
            List<Double> before = new ArrayList<Double>();
            List<Double> after = new ArrayList<Double>();
            for (String code : common) {
                before.add(previous.get(code));
                after.add(current.get(code));
            }
            double correlation = rankIc(before, after);
            if (Double.isFinite(correlation)) changes.add(Math.max(0d, Math.min(1d, (1d - correlation) / 2d)));
        }
        return mean(changes);
    }

    private List<Double> ranks(List<Double> values) {
        List<Integer> indexes = new ArrayList<Integer>(); for (int i = 0; i < values.size(); i++) indexes.add(i);
        indexes.sort(Comparator.comparing(values::get));
        Double[] result = new Double[values.size()]; int cursor = 0;
        while (cursor < indexes.size()) {
            int end = cursor; double value = values.get(indexes.get(cursor));
            while (end + 1 < indexes.size() && Double.compare(values.get(indexes.get(end + 1)), value) == 0) end++;
            double rank = (cursor + end) / 2d + 1d;
            for (int i = cursor; i <= end; i++) result[indexes.get(i)] = rank;
            cursor = end + 1;
        }
        return java.util.Arrays.asList(result);
    }

    private boolean validPair(List<Double> factors, List<Double> returns) { return factors != null && returns != null && factors.size() == returns.size(); }
    private List<Integer> order(List<Double> values) { List<Integer> indexes = new ArrayList<Integer>(); for (int i = 0; i < values.size(); i++) indexes.add(i); indexes.sort(Comparator.comparing(values::get)); return indexes; }
    private List<Double> finite(List<Double> values) { List<Double> result = new ArrayList<Double>(); if (values != null) for (Double value : values) if (value != null && Double.isFinite(value)) result.add(value); return result; }
    private double mean(List<Double> values) { return values.isEmpty() ? 0d : values.stream().mapToDouble(Double::doubleValue).average().orElse(0d); }

    private double correlation(List<Double> x, List<Double> y) {
        double mx = x.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double my = y.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double numerator = 0, dx = 0, dy = 0;
        for (int i = 0; i < x.size(); i++) {
            double a = x.get(i) - mx, b = y.get(i) - my; numerator += a * b; dx += a * a; dy += b * b;
        }
        return dx == 0 || dy == 0 ? Double.NaN : numerator / Math.sqrt(dx * dy);
    }

    /** Newey-West/HAC standard error of the daily IC mean. */
    private double hacMeanStandardError(List<Double> values, double mean) {
        int n = values.size();
        if (n < 2) return 0d;
        int maxLag = Math.min(n - 1, (int) Math.floor(4d * Math.pow(n / 100d, 2d / 9d)));
        double longRunVariance = 0d;
        for (int i = 0; i < n; i++) {
            double centered = values.get(i) - mean;
            longRunVariance += centered * centered / n;
        }
        for (int lag = 1; lag <= maxLag; lag++) {
            double covariance = 0d;
            for (int i = lag; i < n; i++) {
                covariance += (values.get(i) - mean) * (values.get(i - lag) - mean) / n;
            }
            double bartlettWeight = 1d - (double) lag / (maxLag + 1d);
            longRunVariance += 2d * bartlettWeight * covariance;
        }
        return Math.sqrt(Math.max(0d, longRunVariance) / n);
    }
}
