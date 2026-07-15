package com.finscope.service.quant.factor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 无市场语义的确定性时间序列算子。所有无定义结果都返回 empty，禁止用 NaN 进入因子链路。
 */
@Component
public class TimeSeriesFactorOperators {
    public static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Optional<BigDecimal> delta(List<BigDecimal> input) {
        List<BigDecimal> values = valid(input);
        return values.size() < 2 ? Optional.empty()
                : value(values.get(values.size() - 1).subtract(values.get(0)));
    }

    public Optional<BigDecimal> mean(List<BigDecimal> input) {
        List<BigDecimal> values = valid(input);
        if (values.isEmpty()) return Optional.empty();
        return value(rawSum(values).divide(BigDecimal.valueOf(values.size()), SCALE, ROUNDING));
    }

    public Optional<BigDecimal> sum(List<BigDecimal> input) {
        List<BigDecimal> values = valid(input);
        return values.isEmpty() ? Optional.empty() : value(rawSum(values));
    }

    public Optional<BigDecimal> std(List<BigDecimal> input) {
        List<BigDecimal> values = valid(input);
        if (values.size() < 2) return Optional.empty();
        BigDecimal mean = mean(values).get();
        BigDecimal variance = values.stream()
                .map(item -> item.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 12, ROUNDING);
        return value(BigDecimal.valueOf(Math.sqrt(variance.doubleValue())));
    }

    public Optional<BigDecimal> zScore(List<BigDecimal> input, BigDecimal current) {
        if (current == null) return Optional.empty();
        Optional<BigDecimal> mean = mean(input);
        Optional<BigDecimal> std = std(input);
        if (!mean.isPresent() || !std.isPresent() || std.get().signum() == 0) return Optional.empty();
        return value(current.subtract(mean.get()).divide(std.get(), SCALE, ROUNDING));
    }

    public Optional<BigDecimal> tsRank(List<BigDecimal> input, BigDecimal current) {
        List<BigDecimal> values = valid(input);
        if (current == null || values.isEmpty()) return Optional.empty();
        long atOrBelow = values.stream().filter(item -> item.compareTo(current) <= 0).count();
        return value(BigDecimal.valueOf(atOrBelow)
                .divide(BigDecimal.valueOf(values.size()), SCALE, ROUNDING));
    }

    public Optional<BigDecimal> quantile(List<BigDecimal> input, BigDecimal quantile) {
        List<BigDecimal> values = valid(input);
        if (values.isEmpty() || quantile == null || quantile.signum() < 0
                || quantile.compareTo(BigDecimal.ONE) > 0) return Optional.empty();
        Collections.sort(values);
        BigDecimal position = quantile.multiply(BigDecimal.valueOf(values.size() - 1));
        int lower = position.intValue();
        int upper = Math.min(values.size() - 1, lower + 1);
        BigDecimal weight = position.subtract(BigDecimal.valueOf(lower));
        BigDecimal interpolated = values.get(lower).multiply(BigDecimal.ONE.subtract(weight))
                .add(values.get(upper).multiply(weight));
        return value(interpolated);
    }

    public Optional<BigDecimal> slope(List<BigDecimal> input) {
        List<BigDecimal> values = valid(input);
        if (values.size() < 2) return Optional.empty();
        BigDecimal xMean = BigDecimal.valueOf(values.size() - 1)
                .divide(BigDecimal.valueOf(2), 12, ROUNDING);
        BigDecimal yMean = mean(values).get();
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (int i = 0; i < values.size(); i++) {
            BigDecimal xDelta = BigDecimal.valueOf(i).subtract(xMean);
            numerator = numerator.add(xDelta.multiply(values.get(i).subtract(yMean)));
            denominator = denominator.add(xDelta.pow(2));
        }
        return denominator.signum() == 0 ? Optional.empty()
                : value(numerator.divide(denominator, SCALE, ROUNDING));
    }

    public Optional<BigDecimal> rSquare(List<BigDecimal> input) {
        List<BigDecimal> values = valid(input);
        if (values.size() < 2) return Optional.empty();
        List<BigDecimal> x = new ArrayList<BigDecimal>();
        for (int i = 0; i < values.size(); i++) x.add(BigDecimal.valueOf(i));
        Optional<BigDecimal> correlation = correlation(x, values);
        return correlation.map(item -> item.multiply(item).setScale(SCALE, ROUNDING));
    }

    public Optional<BigDecimal> correlation(List<BigDecimal> leftInput, List<BigDecimal> rightInput) {
        if (leftInput == null || rightInput == null || leftInput.size() != rightInput.size()) return Optional.empty();
        List<BigDecimal> left = new ArrayList<BigDecimal>();
        List<BigDecimal> right = new ArrayList<BigDecimal>();
        for (int i = 0; i < leftInput.size(); i++) {
            if (leftInput.get(i) != null && rightInput.get(i) != null) {
                left.add(leftInput.get(i));
                right.add(rightInput.get(i));
            }
        }
        if (left.size() < 2) return Optional.empty();
        BigDecimal leftMean = mean(left).get();
        BigDecimal rightMean = mean(right).get();
        BigDecimal covariance = BigDecimal.ZERO;
        BigDecimal leftVariance = BigDecimal.ZERO;
        BigDecimal rightVariance = BigDecimal.ZERO;
        for (int i = 0; i < left.size(); i++) {
            BigDecimal leftDelta = left.get(i).subtract(leftMean);
            BigDecimal rightDelta = right.get(i).subtract(rightMean);
            covariance = covariance.add(leftDelta.multiply(rightDelta));
            leftVariance = leftVariance.add(leftDelta.pow(2));
            rightVariance = rightVariance.add(rightDelta.pow(2));
        }
        if (leftVariance.signum() == 0 || rightVariance.signum() == 0) return Optional.empty();
        double denominator = Math.sqrt(leftVariance.doubleValue() * rightVariance.doubleValue());
        return value(BigDecimal.valueOf(covariance.doubleValue() / denominator));
    }

    public Optional<BigDecimal> min(List<BigDecimal> input) {
        List<BigDecimal> values = valid(input);
        return values.isEmpty() ? Optional.empty() : value(Collections.min(values));
    }

    public Optional<BigDecimal> max(List<BigDecimal> input) {
        List<BigDecimal> values = valid(input);
        return values.isEmpty() ? Optional.empty() : value(Collections.max(values));
    }

    public Optional<BigDecimal> positiveShare(List<BigDecimal> input) {
        List<BigDecimal> values = valid(input);
        if (values.isEmpty()) return Optional.empty();
        long positive = values.stream().filter(item -> item.signum() > 0).count();
        return value(BigDecimal.valueOf(positive)
                .divide(BigDecimal.valueOf(values.size()), SCALE, ROUNDING));
    }

    private BigDecimal rawSum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<BigDecimal> valid(List<BigDecimal> input) {
        List<BigDecimal> result = new ArrayList<BigDecimal>();
        if (input != null) input.stream().filter(item -> item != null).forEach(result::add);
        return result;
    }

    private Optional<BigDecimal> value(BigDecimal number) {
        return Optional.of(number.setScale(SCALE, ROUNDING));
    }
}
