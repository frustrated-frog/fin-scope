package com.finscope.service.factorresearch;

import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorObservation;
import com.finscope.domain.factorresearch.ObservationQuality;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.quant.data.QuantDailyBar;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CapitalFlowFactorProvider implements FactorProvider {
    public static final FactorIdentity MAIN_FLOW_SHARE =
            new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0");
    public static final FactorIdentity SUPER_LARGE_FLOW_SHARE =
            new FactorIdentity("capital", "SUPER_LARGE_FLOW_SHARE", "1.0.0");
    public static final FactorIdentity BIG_ORDER_FLOW_SHARE =
            new FactorIdentity("capital", "BIG_ORDER_FLOW_SHARE", "1.0.0");
    public static final FactorIdentity NORMALIZED_MAIN_FLOW_SUM_5D =
            new FactorIdentity("capital", "NORMALIZED_MAIN_FLOW_SUM_5D", "1.0.0");
    public static final FactorIdentity FLOW_PERSISTENCE_5D =
            new FactorIdentity("capital", "FLOW_PERSISTENCE_5D", "1.0.0");
    public static final FactorIdentity MAIN_FLOW_SHARE_ZSCORE_20D =
            new FactorIdentity("capital", "MAIN_FLOW_SHARE_ZSCORE_20D", "1.0.0");
    public static final FactorIdentity PRICE_FLOW_DIVERGENCE_5D =
            new FactorIdentity("capital", "PRICE_FLOW_DIVERGENCE_5D", "1.0.0");

    public Set<FactorIdentity> factors() {
        return new LinkedHashSet<FactorIdentity>(Arrays.asList(
                MAIN_FLOW_SHARE, SUPER_LARGE_FLOW_SHARE, BIG_ORDER_FLOW_SHARE,
                NORMALIZED_MAIN_FLOW_SUM_5D, FLOW_PERSISTENCE_5D,
                MAIN_FLOW_SHARE_ZSCORE_20D, PRICE_FLOW_DIVERGENCE_5D));
    }

    public FactorObservation calculate(FactorCalculationContext context, FactorIdentity factor) {
        if (!factors().contains(factor)) throw new IllegalArgumentException("unsupported capital factor: " + factor);
        if (isWindowFactor(factor)) return calculateWindow(context, factor);
        QuantCapitalFlowDaily source = context.getCapitalFlow();
        boolean complete = source != null && "COMPLETE".equals(source.getQualityStatus())
                && String.valueOf(source.getDatasetId()).equals(context.getDatasetId())
                && Objects.equals(source.getTradeDate(), context.getTradeDate())
                && Objects.equals(source.getInstrumentCode(), context.getInstrumentCode())
                && source.getAvailableAt() != null && !source.getAvailableAt().isAfter(context.getAvailableAt())
                && requiredValuePresent(source, factor) && source.getAmount() != null
                && source.getAmount().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal numerator = complete ? numerator(source, factor) : null;
        BigDecimal value = numerator == null ? null : numerator.divide(
                source.getAmount(), 10, RoundingMode.HALF_UP);
        String sourceFingerprint = source == null || source.getSourceFingerprint() == null
                ? "missing:" + context.getDatasetId() + ":" + context.getTradeDate()
                : source.getSourceFingerprint();
        return new FactorObservation(context.getDatasetId(), context.getInstrumentCode(), context.getTradeDate(),
                source == null || source.getAvailableAt() == null ? context.getAvailableAt() : source.getAvailableAt(),
                factor, value, value, complete ? ObservationQuality.COMPLETE : ObservationQuality.MISSING_INPUT,
                sourceFingerprint, providerCode() + ":" + factor.getCode() + ":"
                + calculationVersion() + ":" + sourceFingerprint);
    }

    private boolean requiredValuePresent(QuantCapitalFlowDaily source, FactorIdentity factor) {
        if (MAIN_FLOW_SHARE.equals(factor)) return source.getMainNetInflow() != null;
        if (SUPER_LARGE_FLOW_SHARE.equals(factor)) return source.getSuperLargeNetInflow() != null;
        return source.getSuperLargeNetInflow() != null && source.getLargeNetInflow() != null;
    }

    private BigDecimal numerator(QuantCapitalFlowDaily source, FactorIdentity factor) {
        if (MAIN_FLOW_SHARE.equals(factor)) return source.getMainNetInflow();
        if (SUPER_LARGE_FLOW_SHARE.equals(factor)) return source.getSuperLargeNetInflow();
        return source.getSuperLargeNetInflow().add(source.getLargeNetInflow());
    }

    public String providerCode() {
        return "FROZEN_CAPITAL_FLOW";
    }

    public String calculationVersion() {
        return "capital-flow-share-v1";
    }

    private boolean isWindowFactor(FactorIdentity factor) {
        return NORMALIZED_MAIN_FLOW_SUM_5D.equals(factor) || FLOW_PERSISTENCE_5D.equals(factor)
                || MAIN_FLOW_SHARE_ZSCORE_20D.equals(factor) || PRICE_FLOW_DIVERGENCE_5D.equals(factor);
    }

    private FactorObservation calculateWindow(FactorCalculationContext context, FactorIdentity factor) {
        int window = MAIN_FLOW_SHARE_ZSCORE_20D.equals(factor) ? 20 : 5;
        List<QuantCapitalFlowDaily> rows = strictWindow(context, window);
        BigDecimal value = null;
        if (!rows.isEmpty() && (!PRICE_FLOW_DIVERGENCE_5D.equals(factor) || hasPriceWindow(context, 6))) {
            List<Double> shares = new ArrayList<Double>();
            for (QuantCapitalFlowDaily row : rows)
                shares.add(row.getMainNetInflow().divide(row.getAmount(), 16, RoundingMode.HALF_UP).doubleValue());
            if (NORMALIZED_MAIN_FLOW_SUM_5D.equals(factor)) {
                value = decimal(shares.stream().mapToDouble(Double::doubleValue).sum());
            } else if (FLOW_PERSISTENCE_5D.equals(factor)) {
                value = decimal(shares.stream().mapToDouble(item -> Math.signum(item)).average().orElse(0d));
            } else if (MAIN_FLOW_SHARE_ZSCORE_20D.equals(factor)) {
                double mean = shares.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
                double variance = shares.stream().mapToDouble(item -> (item - mean) * (item - mean)).average().orElse(0d);
                if (variance > 0d) value = decimal((shares.get(shares.size() - 1) - mean) / Math.sqrt(variance));
            } else {
                List<com.finscope.domain.quant.data.QuantDailyBar> prices = context.getHistory();
                double priceReturn = prices.get(prices.size() - 1).getAdjustedClose().doubleValue()
                        / prices.get(prices.size() - 6).getAdjustedClose().doubleValue() - 1d;
                double flowSum = shares.stream().mapToDouble(Double::doubleValue).sum();
                value = decimal(-priceReturn * flowSum);
            }
        }
        QuantCapitalFlowDaily latest = rows.isEmpty() ? context.getCapitalFlow() : rows.get(rows.size() - 1);
        String sourceFingerprint = rows.isEmpty() ? "missing:" + context.getDatasetId() + ":" + context.getTradeDate()
                : rows.stream().map(row -> row.getTradeDate() + "@" + row.getAvailableAt() + "@" + row.getSourceFingerprint())
                .collect(java.util.stream.Collectors.joining("|"));
        return new FactorObservation(context.getDatasetId(), context.getInstrumentCode(), context.getTradeDate(),
                latest == null || latest.getAvailableAt() == null ? context.getAvailableAt() : latest.getAvailableAt(),
                factor, value, value, value == null ? ObservationQuality.MISSING_INPUT : ObservationQuality.COMPLETE,
                sourceFingerprint, providerCode() + ":" + factor.getCode() + ":capital-window-v1:" + sourceFingerprint);
    }

    private List<QuantCapitalFlowDaily> strictWindow(FactorCalculationContext context, int window) {
        List<com.finscope.domain.quant.data.QuantDailyBar> prices = context.getHistory();
        if (prices.size() < window || !context.getTradeDate().equals(prices.get(prices.size() - 1).getTradeDate()))
            return Collections.emptyList();
        List<java.time.LocalDate> dates = new ArrayList<java.time.LocalDate>();
        for (int i = prices.size() - window; i < prices.size(); i++) {
            LocalDate date = prices.get(i).getTradeDate();
            if (date == null || dates.contains(date)) return Collections.emptyList();
            dates.add(date);
        }
        Map<LocalDate, QuantCapitalFlowDaily> byDate = new LinkedHashMap<java.time.LocalDate, QuantCapitalFlowDaily>();
        for (QuantCapitalFlowDaily row : context.getCapitalHistory()) {
            if (row == null || row.getTradeDate() == null || byDate.put(row.getTradeDate(), row) != null)
                return Collections.emptyList();
        }
        List<QuantCapitalFlowDaily> result = new ArrayList<QuantCapitalFlowDaily>();
        for (LocalDate date : dates) {
            QuantCapitalFlowDaily row = byDate.get(date);
            if (!validMainRow(context, row)) return Collections.emptyList();
            result.add(row);
        }
        return result;
    }

    private boolean validMainRow(FactorCalculationContext context, QuantCapitalFlowDaily row) {
        return row != null && "COMPLETE".equals(row.getQualityStatus())
                && String.valueOf(row.getDatasetId()).equals(context.getDatasetId())
                && context.getInstrumentCode().equals(row.getInstrumentCode())
                && row.getAvailableAt() != null && !row.getAvailableAt().isAfter(context.getAvailableAt())
                && row.getMainNetInflow() != null && row.getAmount() != null && row.getAmount().signum() > 0;
    }

    private boolean hasPriceWindow(FactorCalculationContext context, int size) {
        List<QuantDailyBar> prices = context.getHistory();
        return prices.size() >= size && prices.get(prices.size() - 1).getAdjustedClose() != null
                && prices.get(prices.size() - size).getAdjustedClose() != null
                && prices.get(prices.size() - size).getAdjustedClose().signum() > 0;
    }

    private BigDecimal decimal(double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP) : null;
    }
}
