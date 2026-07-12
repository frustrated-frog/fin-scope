package com.finscope.service.quant.data;

import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

/** 仅用于学习闭环；代码和价格均为虚拟数据，调用方必须保留 LEARNING_SAMPLE 标识。 */
@Component
public class QuantLearningDatasetFactory {
    public List<QuantDailyBar> bars(Long datasetId) {
        List<QuantDailyBar> result = new ArrayList<QuantDailyBar>();
        Random random = new Random(20260713L);
        LocalDate date = LocalDate.of(2024, 1, 2);
        double[] prices = new double[30];
        for (int i = 0; i < prices.length; i++) prices[i] = 12 + i * 1.7;
        int tradingDays = 0;
        while (tradingDays < 320) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                date = date.plusDays(1); continue;
            }
            for (int i = 0; i < prices.length; i++) {
                double drift = (i % 5 - 2) * 0.0005;
                double open = prices[i] * (1 + (random.nextDouble() - 0.5) * 0.01);
                double close = open * (1 + drift + (random.nextDouble() - 0.5) * 0.025);
                double high = Math.max(open, close) * 1.01;
                double low = Math.min(open, close) * 0.99;
                QuantDailyBar bar = new QuantDailyBar();
                bar.setDatasetId(datasetId); bar.setTradeDate(date);
                bar.setInstrumentCode(String.format("L%05d.SIM", i + 1));
                bar.setOpen(decimal(open)); bar.setHigh(decimal(high)); bar.setLow(decimal(low));
                bar.setClose(decimal(close)); bar.setAdjustedClose(decimal(close));
                bar.setVolume(decimal(100000 + random.nextInt(900000)));
                bar.setAmount(decimal(close * bar.getVolume().doubleValue())); bar.setTradeStatus("TRADING");
                result.add(bar); prices[i] = close;
            }
            tradingDays++; date = date.plusDays(1);
        }
        return result;
    }

    public List<QuantFundamentalSnapshot> fundamentals(Long datasetId) {
        List<QuantFundamentalSnapshot> result = new ArrayList<QuantFundamentalSnapshot>();
        for (int i = 0; i < 30; i++) {
            QuantFundamentalSnapshot value = new QuantFundamentalSnapshot();
            value.setDatasetId(datasetId); value.setInstrumentCode(String.format("L%05d.SIM", i + 1));
            value.setReportPeriod(LocalDate.of(2023, 12, 31)); value.setDisclosedAt(LocalDate.of(2024, 4, 20));
            value.setPe(decimal(8 + i)); value.setPb(decimal(0.8 + i * 0.12));
            value.setMarketCap(decimal(5_000_000_000d + i * 1_000_000_000d));
            value.setRoe(decimal(0.06 + i * 0.006)); value.setRevenueGrowth(decimal(0.02 + i * 0.004));
            value.setProfitGrowth(decimal(-0.05 + i * 0.007)); value.setDebtRatio(decimal(0.2 + (i % 10) * 0.04));
            result.add(value);
        }
        return result;
    }

    private BigDecimal decimal(double value) { return BigDecimal.valueOf(value).setScale(6, BigDecimal.ROUND_HALF_UP); }
}
