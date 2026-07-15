package com.finscope.service.marketintel.factor;

import com.finscope.domain.marketintel.CapitalFactorDefinition;
import com.finscope.domain.marketintel.CapitalFactorObservation;
import com.finscope.domain.marketintel.CapitalFactorResult;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.service.quant.factor.TimeSeriesFactorOperators;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CapitalFactorEngine {
    private static final int SCALE = TimeSeriesFactorOperators.SCALE;
    private final CapitalFactorRegistry registry;
    private final TimeSeriesFactorOperators operators;

    public CapitalFactorEngine(CapitalFactorRegistry registry, TimeSeriesFactorOperators operators) {
        this.registry = registry;
        this.operators = operators;
    }

    public CapitalFactorResult calculate(List<CapitalFlowPoint> input) {
        List<CapitalFlowPoint> facts = sorted(input);
        List<CapitalFlowPoint> daily = facts.stream().filter(item -> "DAY_1".equals(item.getGranularity()))
                .collect(Collectors.toList());
        List<CapitalFlowPoint> minute = latestMinuteSession(facts);
        List<CapitalFactorObservation> observations = new ArrayList<CapitalFactorObservation>();
        List<String> gaps = new ArrayList<String>();
        for (CapitalFactorDefinition definition : registry.published()) {
            Optional<CapitalFactorObservation> observation = calculate(definition, daily, minute);
            if (observation.isPresent()) observations.add(observation.get());
            else gaps.add("因子【" + definition.getName() + "】数据不足：至少需要 "
                    + definition.getMinimumSamples() + " 个有效样本及字段 "
                    + String.join("/", definition.getRequiredFields()));
        }
        return new CapitalFactorResult(CapitalFactorRegistry.VERSION, observations, gaps);
    }

    private Optional<CapitalFactorObservation> calculate(CapitalFactorDefinition definition,
                                                          List<CapitalFlowPoint> daily,
                                                          List<CapitalFlowPoint> minute) {
        String code = definition.getCalculationKey();
        List<BigDecimal> amounts = values(daily, CapitalFlowPoint::getIntervalTradeAmount);
        List<BigDecimal> flows = values(daily, CapitalFlowPoint::getMainNetInflow);
        CapitalFlowPoint latest = daily.isEmpty() ? null : daily.get(daily.size() - 1);
        if ("AMOUNT_RATIO_5D".equals(code)) return ratio(definition, latest, amounts, 5);
        if ("AMOUNT_RATIO_20D".equals(code)) return ratio(definition, latest, amounts, 20);
        if ("AMOUNT_ZSCORE_20D".equals(code)) return zScore(definition, daily, amounts, latest, 20, "intervalTradeAmount");
        if ("TURNOVER_PERCENTILE".equals(code)) return percentile(definition, daily, values(daily, CapitalFlowPoint::getTurnoverRate), latest, "turnoverRate");
        if ("VOLUME_RATIO_LATEST".equals(code)) return scalar(definition, latest, latest == null ? null : latest.getVolumeRatio(), "volumeRatio", null);
        if ("MAIN_FLOW_SHARE".equals(code)) return mainFlowShare(definition, latest);
        if (code.startsWith("MAIN_FLOW_SUM_")) return flowSum(definition, daily, window(code));
        if ("MAIN_FLOW_STREAK".equals(code)) return streak(definition, daily);
        if ("MAIN_FLOW_SLOPE_5D".equals(code)) return slope(definition, daily, flows, 5);
        if ("MAIN_FLOW_ZSCORE_20D".equals(code)) return zScore(definition, daily, flows, latest, 20, "mainNetInflow");
        if ("BIG_ORDER_NET".equals(code)) return orderNet(definition, latest, true);
        if ("SMALL_MID_ORDER_NET".equals(code)) return orderNet(definition, latest, false);
        if ("BIG_SMALL_DIVERGENCE".equals(code)) return orderDivergence(definition, latest);
        if ("SUPER_LARGE_CONTRIBUTION".equals(code)) return contribution(definition, latest);
        if ("PRICE_FLOW_ALIGNMENT".equals(code)) return priceFlowAlignment(definition, daily);
        if ("PRICE_VOLUME_ALIGNMENT".equals(code)) return priceVolumeAlignment(definition, daily);
        if ("PRICE_VOLUME_FLOW_REGIME".equals(code)) return regime(definition, daily);
        if ("INTRADAY_FLOW_REVERSALS".equals(code)) return reversals(definition, minute);
        if ("INTRADAY_FLOW_ACCELERATION".equals(code)) return intradaySlope(definition, minute);
        if ("LATE_SESSION_FLOW_SHARE".equals(code)) return lateShare(definition, minute);
        if ("PEAK_INFLOW_BUCKET".equals(code)) return peak(definition, minute, true);
        if ("PEAK_OUTFLOW_BUCKET".equals(code)) return peak(definition, minute, false);
        return Optional.empty();
    }

    private Optional<CapitalFactorObservation> ratio(CapitalFactorDefinition definition, CapitalFlowPoint latest,
                                                      List<BigDecimal> values, int window) {
        List<BigDecimal> sample = tail(values, window);
        if (latest == null || latest.getIntervalTradeAmount() == null || sample.size() < definition.getMinimumSamples()) return Optional.empty();
        Optional<BigDecimal> baseline = operators.mean(sample);
        if (!baseline.isPresent() || baseline.get().signum() == 0) return Optional.empty();
        BigDecimal value = latest.getIntervalTradeAmount().divide(baseline.get(), SCALE, RoundingMode.HALF_UP);
        return observation(definition, latest, value, baseline.get(), null, null,
                value.compareTo(BigDecimal.ONE) >= 0 ? "ABOVE_BASELINE" : "BELOW_BASELINE",
                sample.size(), refs(latest, "intervalTradeAmount"));
    }

    private Optional<CapitalFactorObservation> zScore(CapitalFactorDefinition definition, List<CapitalFlowPoint> points,
                                                       List<BigDecimal> values, CapitalFlowPoint latest, int window,
                                                       String metric) {
        List<BigDecimal> sample = tail(values, window);
        if (latest == null || sample.size() < definition.getMinimumSamples()) return Optional.empty();
        BigDecimal current = "mainNetInflow".equals(metric) ? latest.getMainNetInflow() : latest.getIntervalTradeAmount();
        Optional<BigDecimal> zScore = operators.zScore(sample, current);
        if (!zScore.isPresent()) return Optional.empty();
        return observation(definition, latest, zScore.get(), operators.mean(sample).orElse(null), null,
                zScore.get(), zState(zScore.get()), sample.size(), refs(tailPoints(points, window), metric));
    }

    private Optional<CapitalFactorObservation> percentile(CapitalFactorDefinition definition, List<CapitalFlowPoint> points,
                                                           List<BigDecimal> values, CapitalFlowPoint latest, String metric) {
        if (latest == null || latest.getTurnoverRate() == null || values.size() < definition.getMinimumSamples()) return Optional.empty();
        Optional<BigDecimal> percentile = operators.tsRank(tail(values, 60), latest.getTurnoverRate());
        if (!percentile.isPresent()) return Optional.empty();
        return observation(definition, latest, percentile.get(), operators.mean(values).orElse(null), percentile.get(), null,
                percentile.get().compareTo(new BigDecimal("0.8")) >= 0 ? "HIGH" : "NORMAL",
                values.size(), refs(points, metric));
    }

    private Optional<CapitalFactorObservation> mainFlowShare(CapitalFactorDefinition definition, CapitalFlowPoint latest) {
        if (latest == null || latest.getMainNetInflow() == null || latest.getIntervalTradeAmount() == null
                || latest.getIntervalTradeAmount().signum() == 0) return Optional.empty();
        BigDecimal value = latest.getMainNetInflow().divide(latest.getIntervalTradeAmount(), SCALE, RoundingMode.HALF_UP);
        return observation(definition, latest, value, null, null, null, direction(value), 1,
                refs(latest, "mainNetInflow", "intervalTradeAmount"));
    }

    private Optional<CapitalFactorObservation> flowSum(CapitalFactorDefinition definition, List<CapitalFlowPoint> daily, int window) {
        List<CapitalFlowPoint> points = tailPoints(daily, window);
        List<BigDecimal> values = values(points, CapitalFlowPoint::getMainNetInflow);
        Optional<BigDecimal> sum = operators.sum(values);
        return sum.isPresent() && !points.isEmpty()
                ? observation(definition, points.get(points.size() - 1), sum.get(), null, null, null,
                direction(sum.get()), values.size(), refs(points, "mainNetInflow")) : Optional.empty();
    }

    private Optional<CapitalFactorObservation> streak(CapitalFactorDefinition definition, List<CapitalFlowPoint> daily) {
        if (daily.isEmpty()) return Optional.empty();
        int sign = 0;
        int count = 0;
        for (int i = daily.size() - 1; i >= 0; i--) {
            BigDecimal value = daily.get(i).getMainNetInflow();
            if (value == null || value.signum() == 0) break;
            if (sign == 0) sign = value.signum();
            if (sign != value.signum()) break;
            count++;
        }
        if (count == 0) return Optional.empty();
        CapitalFlowPoint latest = daily.get(daily.size() - 1);
        return observation(definition, latest, BigDecimal.valueOf(count * sign), null, null, null,
                sign > 0 ? "INFLOW" : "OUTFLOW", count,
                refs(tailPoints(daily, count), "mainNetInflow"));
    }

    private Optional<CapitalFactorObservation> slope(CapitalFactorDefinition definition, List<CapitalFlowPoint> daily,
                                                      List<BigDecimal> values, int window) {
        List<BigDecimal> sample = tail(values, window);
        Optional<BigDecimal> slope = operators.slope(sample);
        return slope.isPresent() ? observation(definition, daily.get(daily.size() - 1), slope.get(), null, null, null,
                direction(slope.get()), sample.size(), refs(tailPoints(daily, window), "mainNetInflow")) : Optional.empty();
    }

    private Optional<CapitalFactorObservation> orderNet(CapitalFactorDefinition definition, CapitalFlowPoint latest, boolean big) {
        if (latest == null) return Optional.empty();
        BigDecimal left = big ? latest.getSuperLargeNetInflow() : latest.getMediumNetInflow();
        BigDecimal right = big ? latest.getLargeNetInflow() : latest.getSmallNetInflow();
        if (left == null || right == null) return Optional.empty();
        BigDecimal value = left.add(right).setScale(SCALE, RoundingMode.HALF_UP);
        return observation(definition, latest, value, null, null, null, direction(value), 1,
                refs(latest, big ? "superLargeNetInflow" : "mediumNetInflow",
                        big ? "largeNetInflow" : "smallNetInflow"));
    }

    private Optional<CapitalFactorObservation> orderDivergence(CapitalFactorDefinition definition, CapitalFlowPoint latest) {
        Optional<CapitalFactorObservation> big = orderNet(registry.find("BIG_ORDER_NET").get(), latest, true);
        Optional<CapitalFactorObservation> small = orderNet(registry.find("SMALL_MID_ORDER_NET").get(), latest, false);
        if (!big.isPresent() || !small.isPresent()) return Optional.empty();
        boolean divergent = big.get().getValue().signum() != 0 && small.get().getValue().signum() != 0
                && big.get().getValue().signum() != small.get().getValue().signum();
        return observation(definition, latest, divergent ? BigDecimal.ONE : BigDecimal.ZERO, null, null, null,
                divergent ? "DIVERGENT" : "ALIGNED", 1, refs(latest,
                        "superLargeNetInflow", "largeNetInflow", "mediumNetInflow", "smallNetInflow"));
    }

    private Optional<CapitalFactorObservation> contribution(CapitalFactorDefinition definition, CapitalFlowPoint latest) {
        if (latest == null || latest.getSuperLargeNetInflow() == null || latest.getMainNetInflow() == null
                || latest.getMainNetInflow().signum() == 0) return Optional.empty();
        BigDecimal value = latest.getSuperLargeNetInflow().divide(latest.getMainNetInflow(), SCALE, RoundingMode.HALF_UP);
        return observation(definition, latest, value, null, null, null,
                value.abs().compareTo(new BigDecimal("0.5")) >= 0 ? "DOMINANT" : "MIXED", 1,
                refs(latest, "superLargeNetInflow", "mainNetInflow"));
    }

    private Optional<CapitalFactorObservation> priceFlowAlignment(CapitalFactorDefinition definition, List<CapitalFlowPoint> daily) {
        if (daily.size() < 2) return Optional.empty();
        CapitalFlowPoint previous = daily.get(daily.size() - 2);
        CapitalFlowPoint latest = daily.get(daily.size() - 1);
        if (previous.getPrice() == null || latest.getPrice() == null || latest.getMainNetInflow() == null) return Optional.empty();
        int price = latest.getPrice().compareTo(previous.getPrice());
        int flow = latest.getMainNetInflow().signum();
        boolean aligned = price == 0 || flow == 0 || Integer.signum(price) == flow;
        return observation(definition, latest, aligned ? BigDecimal.ONE : BigDecimal.ZERO, null, null, null,
                aligned ? "ALIGNED" : "DIVERGENT", 2,
                refs(Arrays.asList(previous, latest), "price", "mainNetInflow"));
    }

    private Optional<CapitalFactorObservation> priceVolumeAlignment(CapitalFactorDefinition definition, List<CapitalFlowPoint> daily) {
        if (daily.size() < 2) return Optional.empty();
        CapitalFlowPoint previous = daily.get(daily.size() - 2);
        CapitalFlowPoint latest = daily.get(daily.size() - 1);
        if (previous.getPrice() == null || latest.getPrice() == null || previous.getIntervalTradeAmount() == null
                || latest.getIntervalTradeAmount() == null) return Optional.empty();
        int price = latest.getPrice().compareTo(previous.getPrice());
        int amount = latest.getIntervalTradeAmount().compareTo(previous.getIntervalTradeAmount());
        boolean aligned = price == 0 || amount == 0 || Integer.signum(price) == Integer.signum(amount);
        return observation(definition, latest, aligned ? BigDecimal.ONE : BigDecimal.ZERO, null, null, null,
                aligned ? "ALIGNED" : "DIVERGENT", 2,
                refs(Arrays.asList(previous, latest), "price", "intervalTradeAmount"));
    }

    private Optional<CapitalFactorObservation> regime(CapitalFactorDefinition definition, List<CapitalFlowPoint> daily) {
        if (daily.size() < 2) return Optional.empty();
        CapitalFlowPoint previous = daily.get(daily.size() - 2);
        CapitalFlowPoint latest = daily.get(daily.size() - 1);
        if (previous.getPrice() == null || latest.getPrice() == null || previous.getIntervalTradeAmount() == null
                || latest.getIntervalTradeAmount() == null || latest.getMainNetInflow() == null) return Optional.empty();
        String state = token(latest.getPrice().compareTo(previous.getPrice()), "PRICE") + "_"
                + token(latest.getIntervalTradeAmount().compareTo(previous.getIntervalTradeAmount()), "VOLUME") + "_"
                + (latest.getMainNetInflow().signum() >= 0 ? "FLOW_IN" : "FLOW_OUT");
        return observation(definition, latest, BigDecimal.valueOf(latest.getMainNetInflow().signum()), null, null, null,
                state, 2, refs(Arrays.asList(previous, latest), "price", "intervalTradeAmount", "mainNetInflow"));
    }

    private Optional<CapitalFactorObservation> reversals(CapitalFactorDefinition definition, List<CapitalFlowPoint> minute) {
        List<CapitalFlowPoint> valid = minute.stream().filter(item -> item.getMainNetInflow() != null
                && item.getMainNetInflow().signum() != 0).collect(Collectors.toList());
        if (valid.size() < definition.getMinimumSamples()) return Optional.empty();
        int reversals = 0;
        for (int i = 1; i < valid.size(); i++) if (valid.get(i - 1).getMainNetInflow().signum()
                != valid.get(i).getMainNetInflow().signum()) reversals++;
        return observation(definition, valid.get(valid.size() - 1), BigDecimal.valueOf(reversals), null, null, null,
                reversals > 0 ? "REVERSAL" : "ONE_WAY", valid.size(), refs(valid, "mainNetInflow"));
    }

    private Optional<CapitalFactorObservation> intradaySlope(CapitalFactorDefinition definition, List<CapitalFlowPoint> minute) {
        List<CapitalFlowPoint> points = tailPoints(minute, 3);
        List<BigDecimal> values = values(points, CapitalFlowPoint::getMainNetInflow);
        if (values.size() < definition.getMinimumSamples()) return Optional.empty();
        Optional<BigDecimal> slope = operators.slope(values);
        return slope.isPresent() ? observation(definition, points.get(points.size() - 1), slope.get(), null, null, null,
                slope.get().signum() > 0 ? "ACCELERATING_INFLOW" : slope.get().signum() < 0
                        ? "ACCELERATING_OUTFLOW" : "STABLE", values.size(), refs(points, "mainNetInflow")) : Optional.empty();
    }

    private Optional<CapitalFactorObservation> lateShare(CapitalFactorDefinition definition, List<CapitalFlowPoint> minute) {
        List<CapitalFlowPoint> valid = minute.stream().filter(item -> item.getMainNetInflow() != null).collect(Collectors.toList());
        if (valid.size() < definition.getMinimumSamples()) return Optional.empty();
        int cut = Math.max(0, (int) Math.floor(valid.size() * 0.75));
        BigDecimal denominator = valid.stream().map(item -> item.getMainNetInflow().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (denominator.signum() == 0) return Optional.empty();
        BigDecimal late = valid.subList(cut, valid.size()).stream().map(CapitalFlowPoint::getMainNetInflow)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal value = late.divide(denominator, SCALE, RoundingMode.HALF_UP);
        return observation(definition, valid.get(valid.size() - 1), value, null, null, null,
                direction(value), valid.size(), refs(valid, "mainNetInflow"));
    }

    private Optional<CapitalFactorObservation> peak(CapitalFactorDefinition definition, List<CapitalFlowPoint> minute, boolean max) {
        List<CapitalFlowPoint> valid = minute.stream().filter(item -> item.getMainNetInflow() != null).collect(Collectors.toList());
        if (valid.isEmpty()) return Optional.empty();
        CapitalFlowPoint peak = valid.stream().max(max
                ? Comparator.comparing(CapitalFlowPoint::getMainNetInflow)
                : Comparator.comparing(CapitalFlowPoint::getMainNetInflow).reversed()).get();
        String state = peak.getObservedAt().toLocalTime().toString();
        return observation(definition, peak, peak.getMainNetInflow(), null, null, null, state, valid.size(),
                refs(peak, "mainNetInflow"));
    }

    private Optional<CapitalFactorObservation> scalar(CapitalFactorDefinition definition, CapitalFlowPoint point,
                                                       BigDecimal value, String metric, String state) {
        return point == null || value == null ? Optional.empty()
                : observation(definition, point, value, null, null, null,
                state == null ? direction(value) : state, 1, refs(point, metric));
    }

    private Optional<CapitalFactorObservation> observation(CapitalFactorDefinition definition,
                                                            CapitalFlowPoint point,
                                                            BigDecimal value,
                                                            BigDecimal baseline,
                                                            BigDecimal percentile,
                                                            BigDecimal zScore,
                                                            String state,
                                                            int sampleCount,
                                                            List<String> refs) {
        if (point == null || point.getObservedAt() == null || value == null || sampleCount < definition.getMinimumSamples()) return Optional.empty();
        CapitalFactorObservation result = new CapitalFactorObservation();
        result.setFactorCode(definition.getCode());
        result.setLabel(definition.getName());
        result.setCategory(definition.getCategory());
        result.setObservedAt(point.getObservedAt());
        result.setWindow(definition.getWindow());
        result.setValue(value.setScale(SCALE, RoundingMode.HALF_UP));
        result.setBaseline(baseline);
        result.setPercentile(percentile);
        result.setZScore(zScore);
        result.setState(state);
        result.setSampleCount(sampleCount);
        result.setMetricRefs(refs);
        result.setQualityStatus("COMPLETE".equals(point.getQualityStatus()) ? "COMPLETE" : "PARTIAL");
        result.setCalculationVersion(definition.getCalculationVersion());
        result.setInterpretationBoundary(definition.getInterpretationBoundary());
        return Optional.of(result);
    }

    private List<CapitalFlowPoint> sorted(List<CapitalFlowPoint> input) {
        List<CapitalFlowPoint> values = new ArrayList<CapitalFlowPoint>(input == null
                ? Collections.<CapitalFlowPoint>emptyList() : input);
        values.sort(Comparator.comparing(CapitalFlowPoint::getObservedAt)
                .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()));
        return values;
    }

    private List<CapitalFlowPoint> latestMinuteSession(List<CapitalFlowPoint> facts) {
        List<CapitalFlowPoint> minute = facts.stream().filter(item -> item.getGranularity() != null
                && item.getGranularity().startsWith("MINUTE_")).collect(Collectors.toList());
        Optional<LocalDate> latest = minute.stream().map(CapitalFlowPoint::getDataDate)
                .filter(item -> item != null).max(LocalDate::compareTo);
        return latest.isPresent() ? minute.stream().filter(item -> latest.get().equals(item.getDataDate()))
                .collect(Collectors.toList()) : minute;
    }

    private List<BigDecimal> values(List<CapitalFlowPoint> points, Function<CapitalFlowPoint, BigDecimal> getter) {
        return points.stream().map(getter).filter(item -> item != null).collect(Collectors.toList());
    }

    private List<BigDecimal> tail(List<BigDecimal> values, int window) {
        return new ArrayList<BigDecimal>(values.subList(Math.max(0, values.size() - window), values.size()));
    }

    private List<CapitalFlowPoint> tailPoints(List<CapitalFlowPoint> values, int window) {
        return new ArrayList<CapitalFlowPoint>(values.subList(Math.max(0, values.size() - window), values.size()));
    }

    private List<String> refs(CapitalFlowPoint point, String... metrics) {
        return refs(Collections.singletonList(point), metrics);
    }

    private List<String> refs(List<CapitalFlowPoint> points, String... metrics) {
        List<String> result = new ArrayList<String>();
        for (CapitalFlowPoint point : points) {
            if (point.getId() == null) continue;
            for (String metric : metrics) result.add(point.metricRef(metric));
        }
        return result;
    }

    private int window(String code) {
        return Integer.parseInt(code.replaceAll("\\D+", ""));
    }

    private String direction(BigDecimal value) {
        return value.signum() > 0 ? "INFLOW" : value.signum() < 0 ? "OUTFLOW" : "FLAT";
    }

    private String zState(BigDecimal value) {
        return value.compareTo(new BigDecimal("1")) >= 0 ? "ABNORMALLY_HIGH"
                : value.compareTo(new BigDecimal("-1")) <= 0 ? "ABNORMALLY_LOW" : "NORMAL";
    }

    private String token(int comparison, String prefix) {
        return prefix + (comparison > 0 ? "_UP" : comparison < 0 ? "_DOWN" : "_FLAT");
    }
}
