package com.finscope.service.marketpulse;

import com.finscope.domain.marketpulse.MarketRegimeFeatures;
import com.finscope.domain.marketpulse.MarketRegimeSnapshot;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 以指定交易日为截面计算市场状态特征，避免未来数据泄漏。 */
@Service
public class MarketPulseFeatureService {
    private static final String BENCHMARK_CODE = "000300.SH";
    private static final int FETCH_LIMIT = 180;

    @Resource
    private QuantDailyBarSource dailyBarSource;
    @Resource
    private MarketRegimeClassifier classifier;

    public LocalDate latestBusinessDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        try {
            QuantDailyBarBatch batch = dailyBarSource.fetch(BENCHMARK_CODE, FETCH_LIMIT);
            if (batch != null && batch.getAsOfDate() != null && !batch.getAsOfDate().isAfter(today)) {
                return batch.getAsOfDate();
            }
        } catch (RuntimeException ignored) {
            return previousWeekday(today);
        }
        return previousWeekday(today);
    }

    public MarketRegimeSnapshot calculate(LocalDate businessDate, double sectorDispersion) {
        return calculate(businessDate, sectorDispersion, null);
    }

    public MarketRegimeSnapshot calculate(LocalDate businessDate, double sectorDispersion, Double marketBreadth) {
        MarketRegimeFeatures features = new MarketRegimeFeatures();
        features.setSectorDispersion(sectorDispersion);
        features.setMarketBreadth(marketBreadth);
        try {
            QuantDailyBarBatch batch = dailyBarSource.fetch(BENCHMARK_CODE, FETCH_LIMIT);
            List<QuantDailyBar> bars = eligibleBars(batch, businessDate);
            String fingerprint = fingerprint(batch, bars);
            if (bars.size() < 61) {
                return classifier.classify(businessDate, features, fingerprint, LocalDateTime.now());
            }
            populate(features, bars);
            return classifier.classify(businessDate, features, fingerprint, LocalDateTime.now());
        } catch (RuntimeException error) {
            return classifier.classify(businessDate, features, "FETCH_FAILED", LocalDateTime.now());
        }
    }

    private List<QuantDailyBar> eligibleBars(QuantDailyBarBatch batch, LocalDate businessDate) {
        List<QuantDailyBar> values = new ArrayList<>();
        if (batch == null || batch.getBars() == null) {
            return values;
        }
        for (QuantDailyBar bar : batch.getBars()) {
            if (bar.getTradeDate() != null && !bar.getTradeDate().isAfter(businessDate)
                    && positive(bar.getClose()) && positive(bar.getAmount())) {
                values.add(bar);
            }
        }
        values.sort(Comparator.comparing(QuantDailyBar::getTradeDate));
        return values;
    }

    private void populate(MarketRegimeFeatures features, List<QuantDailyBar> bars) {
        int last = bars.size() - 1;
        features.setReturn1d(returnBetween(bars, last - 1, last));
        features.setReturn5d(returnBetween(bars, last - 5, last));
        features.setReturn20d(returnBetween(bars, last - 20, last));
        features.setPriceVsMa20(relativeToAverage(bars, last, 20, true));
        features.setPriceVsMa60(relativeToAverage(bars, last, 60, true));
        features.setVolatility20(volatility(bars, last, 20));
        features.setMaxDrawdown20(maxDrawdown(bars, last, 20));
        features.setAmountRatio5To20(amountRatio(bars, last));
    }

    private double returnBetween(List<QuantDailyBar> bars, int start, int end) {
        double first = bars.get(start).getClose().doubleValue();
        double last = bars.get(end).getClose().doubleValue();
        return last / first - 1D;
    }

    private double relativeToAverage(List<QuantDailyBar> bars, int last, int window, boolean close) {
        double total = 0D;
        for (int index = last - window + 1; index <= last; index++) {
            total += close ? bars.get(index).getClose().doubleValue() : bars.get(index).getAmount().doubleValue();
        }
        double average = total / window;
        return bars.get(last).getClose().doubleValue() / average - 1D;
    }

    private double volatility(List<QuantDailyBar> bars, int last, int window) {
        List<Double> returns = new ArrayList<>();
        for (int index = last - window + 1; index <= last; index++) {
            double previous = bars.get(index - 1).getClose().doubleValue();
            double current = bars.get(index).getClose().doubleValue();
            returns.add(Math.log(current / previous));
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        double variance = 0D;
        for (Double value : returns) {
            variance += Math.pow(value - mean, 2D);
        }
        variance /= Math.max(1, returns.size() - 1);
        return Math.sqrt(variance) * Math.sqrt(252D);
    }

    private double maxDrawdown(List<QuantDailyBar> bars, int last, int window) {
        double peak = bars.get(last - window).getClose().doubleValue();
        double drawdown = 0D;
        for (int index = last - window + 1; index <= last; index++) {
            double close = bars.get(index).getClose().doubleValue();
            peak = Math.max(peak, close);
            drawdown = Math.min(drawdown, close / peak - 1D);
        }
        return drawdown;
    }

    private double amountRatio(List<QuantDailyBar> bars, int last) {
        double amount5 = 0D;
        double amount20 = 0D;
        for (int index = last - 19; index <= last; index++) {
            double amount = bars.get(index).getAmount().doubleValue();
            amount20 += amount;
            if (index >= last - 4) {
                amount5 += amount;
            }
        }
        return (amount5 / 5D) / (amount20 / 20D);
    }

    private String fingerprint(QuantDailyBarBatch batch, List<QuantDailyBar> bars) {
        if (batch == null) {
            return "NO_BATCH";
        }
        String last = bars.isEmpty() ? "NO_BAR"
                : bars.get(bars.size() - 1).getTradeDate() + "@" + bars.get(bars.size() - 1).getClose();
        return batch.getSourceCode() + ":" + batch.getAsOfDate() + ":" + last;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private LocalDate previousWeekday(LocalDate date) {
        LocalDate value = date;
        while (value.getDayOfWeek().getValue() > 5) {
            value = value.minusDays(1);
        }
        return value;
    }
}
