package com.finscope.service.valuation;

import com.finscope.domain.valuation.StockValuationSnapshot;
import com.finscope.domain.valuation.ValuationMetricSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class ValuationPercentileCalculator {
    private static final int MINIMUM_SAMPLE_COUNT = 20;

    public ValuationMetricSummary summarize(
            String metricCode, BigDecimal latestValue, LocalDate latestDate,
            List<StockValuationSnapshot> history,
            Function<StockValuationSnapshot, BigDecimal> extractor) {
        List<BigDecimal> values3y = values(history, latestDate.minusYears(3), extractor);
        List<BigDecimal> values5y = values(history, latestDate.minusYears(5), extractor);
        ValuationMetricSummary result = new ValuationMetricSummary();
        result.setMetricCode(metricCode);
        result.setValue(latestValue);
        result.setSampleCount3y(values3y.size());
        result.setSampleCount5y(values5y.size());
        result.setPercentile3y(percentile(latestValue, values3y));
        result.setPercentile5y(percentile(latestValue, values5y));
        result.setHistoryStatus(values3y.size() >= MINIMUM_SAMPLE_COUNT
                ? "READY" : "ACCUMULATING");
        return result;
    }

    private static List<BigDecimal> values(
            List<StockValuationSnapshot> history, LocalDate fromDate,
            Function<StockValuationSnapshot, BigDecimal> extractor) {
        List<BigDecimal> result = new ArrayList<BigDecimal>();
        for (StockValuationSnapshot item : history) {
            BigDecimal value = extractor.apply(item);
            if (item.getObservedDate() != null && !item.getObservedDate().isBefore(fromDate)
                    && value != null && value.signum() > 0) {
                result.add(value);
            }
        }
        return result;
    }

    private static BigDecimal percentile(BigDecimal latestValue, List<BigDecimal> values) {
        if (latestValue == null || latestValue.signum() <= 0
                || values.size() < MINIMUM_SAMPLE_COUNT) {
            return null;
        }
        long notGreater = 0L;
        for (BigDecimal value : values) {
            if (value.compareTo(latestValue) <= 0) {
                notGreater++;
            }
        }
        return BigDecimal.valueOf(notGreater)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
