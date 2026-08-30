package com.finscope.service.marketpulse;

import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketBreadthChangeSummary;
import com.finscope.domain.marketpulse.MarketIndexPerformance;
import com.finscope.domain.marketpulse.MarketInternalHistoryPoint;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.rpc.marketpulse.MarketBreadthSource;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class MarketBreadthService {
    private static final int INDEX_BAR_LIMIT = 30;
    private static final List<IndexDefinition> INDICES = Arrays.asList(
            new IndexDefinition("000001.SH", "上证指数"),
            new IndexDefinition("399001.SZ", "深证成指"),
            new IndexDefinition("399006.SZ", "创业板指"),
            new IndexDefinition("000300.SH", "沪深300"),
            new IndexDefinition("000852.SH", "中证1000"));

    @Resource
    private MarketBreadthSource breadthSource;
    @Resource
    private QuantDailyBarSource dailyBarSource;

    public MarketBreadthSnapshot calculate(LocalDate businessDate) {
        MarketBreadthSnapshot breadth;
        try {
            breadth = breadthSource.fetch(businessDate);
        } catch (RuntimeException error) {
            breadth = unavailable(businessDate, error);
        }
        for (IndexDefinition definition : INDICES) {
            try {
                MarketIndexPerformance index = index(definition, businessDate);
                if (index != null) {
                    breadth.getIndices().add(index);
                }
            } catch (RuntimeException error) {
                breadth.getWarnings().add(definition.name + "行情不可用：" + safe(error));
            }
        }
        breadth.setChangeSummary(changeSummary(breadth.getHistory()));
        breadth.setInterpretation(interpretation(breadth));
        return breadth;
    }

    private MarketBreadthChangeSummary changeSummary(List<MarketInternalHistoryPoint> history) {
        if (history == null || history.size() < 2) {
            return null;
        }
        MarketInternalHistoryPoint previous = history.get(history.size() - 2);
        MarketInternalHistoryPoint current = history.get(history.size() - 1);
        MarketBreadthChangeSummary value = new MarketBreadthChangeSummary();
        value.setPreviousBusinessDate(previous.getBusinessDate());
        value.setAdvanceRatioChange(difference(current.getAdvanceRatio(), previous.getAdvanceRatio()));
        value.setMedianChangePctChange(difference(
                current.getMedianChangePct(), previous.getMedianChangePct()));
        value.setMa20RatioChange(difference(current.getMa20Ratio(), previous.getMa20Ratio()));
        value.setTotalAmountChangeRatio(amountChange(current.getTotalAmount(), previous.getTotalAmount()));
        value.setNewHighLowBalanceChange(balanceChange(current, previous));
        value.setNetAdvancesChange(integerDifference(
                current.getNetAdvances(), previous.getNetAdvances()));
        value.setAdvanceAmountRatioChange(difference(
                current.getAdvanceAmountRatio(), previous.getAdvanceAmountRatio()));
        value.setMcclellanOscillatorChange(difference(
                current.getMcclellanOscillator(), previous.getMcclellanOscillator()));
        value.setHeadline(changeHeadline(value.getAdvanceRatioChange()));
        appendRatioChange(value.getChanges(), "上涨比例", value.getAdvanceRatioChange());
        appendRatioChange(value.getChanges(), "MA20 趋势宽度", value.getMa20RatioChange());
        appendCountChange(value.getChanges(), "20日新高减新低", value.getNewHighLowBalanceChange());
        appendPercentChange(value.getChanges(), "成交额", value.getTotalAmountChangeRatio());
        appendCountChange(value.getChanges(), "净上涨家数", value.getNetAdvancesChange());
        appendRatioChange(value.getChanges(), "上涨成交额占比",
                value.getAdvanceAmountRatioChange());
        appendMomentumChange(value.getChanges(), value.getMcclellanOscillatorChange());
        return value;
    }

    private String changeHeadline(Double advanceRatioChange) {
        if (advanceRatioChange == null) {
            return "市场结构变化等待更多相邻交易日数据";
        }
        if (advanceRatioChange >= 0.08D) {
            return "市场参与快速扩散";
        }
        if (advanceRatioChange <= -0.08D) {
            return "市场参与明显收缩";
        }
        return "市场结构延续震荡分化";
    }

    private void appendRatioChange(List<String> changes, String label, Double change) {
        if (change == null) {
            return;
        }
        String direction = change >= 0D ? "提升" : "回落";
        changes.add(String.format(Locale.ROOT, "%s%s %.1f 个百分点",
                label, direction, Math.abs(change) * 100D));
    }

    private void appendPercentChange(List<String> changes, String label, Double change) {
        if (change == null) {
            return;
        }
        String direction = change >= 0D ? "放大" : "缩减";
        changes.add(String.format(Locale.ROOT, "%s%s %.1f%%",
                label, direction, Math.abs(change) * 100D));
    }

    private void appendCountChange(List<String> changes, String label, Integer change) {
        if (change == null) {
            return;
        }
        String direction = change >= 0 ? "改善" : "转弱";
        changes.add(label + direction + " " + Math.abs(change) + " 家");
    }

    private void appendMomentumChange(List<String> changes, Double change) {
        if (change == null) {
            return;
        }
        String direction = change >= 0D ? "增强" : "减弱";
        changes.add(String.format(Locale.ROOT, "宽度动量%s %.1f", direction, Math.abs(change)));
    }

    private Double difference(Double current, Double previous) {
        if (current == null || previous == null) {
            return null;
        }
        return current - previous;
    }

    private Double amountChange(Double current, Double previous) {
        if (current == null || previous == null || previous <= 0D) {
            return null;
        }
        return current / previous - 1D;
    }

    private Integer balanceChange(MarketInternalHistoryPoint current,
                                  MarketInternalHistoryPoint previous) {
        if (current.getNewHigh20Count() == null || current.getNewLow20Count() == null
                || previous.getNewHigh20Count() == null || previous.getNewLow20Count() == null) {
            return null;
        }
        int currentBalance = current.getNewHigh20Count() - current.getNewLow20Count();
        int previousBalance = previous.getNewHigh20Count() - previous.getNewLow20Count();
        return currentBalance - previousBalance;
    }

    private Integer integerDifference(Integer current, Integer previous) {
        if (current == null || previous == null) {
            return null;
        }
        return current - previous;
    }

    private MarketIndexPerformance index(IndexDefinition definition, LocalDate businessDate) {
        QuantDailyBarBatch batch = dailyBarSource.fetch(definition.code, INDEX_BAR_LIMIT);
        if (batch == null || batch.getBars() == null) {
            throw new IllegalStateException("业务日期不一致");
        }
        List<QuantDailyBar> bars = new ArrayList<>();
        for (QuantDailyBar bar : batch.getBars()) {
            if (bar.getTradeDate() != null && !bar.getTradeDate().isAfter(businessDate)
                    && bar.getClose() != null && bar.getClose().signum() > 0) {
                bars.add(bar);
            }
        }
        bars.sort(Comparator.comparing(QuantDailyBar::getTradeDate));
        if (bars.size() < 21 || !businessDate.equals(bars.get(bars.size() - 1).getTradeDate())) {
            throw new IllegalStateException("业务日期不一致或历史不足");
        }
        int last = bars.size() - 1;
        MarketIndexPerformance value = new MarketIndexPerformance();
        value.setCode(definition.code);
        value.setName(definition.name);
        value.setBusinessDate(businessDate);
        value.setClose(bars.get(last).getClose().doubleValue());
        value.setReturn1d(percentReturn(bars, last - 1, last));
        value.setReturn5d(percentReturn(bars, last - 5, last));
        value.setReturn20d(percentReturn(bars, last - 20, last));
        value.setSourceCode(batch.getSourceCode());
        value.setQualityStatus(batch.getQualityStatus());
        return value;
    }

    private double percentReturn(List<QuantDailyBar> bars, int start, int end) {
        double first = bars.get(start).getClose().doubleValue();
        double last = bars.get(end).getClose().doubleValue();
        return (last / first - 1D) * 100D;
    }

    private String interpretation(MarketBreadthSnapshot value) {
        Double ratio = value.getAdvanceRatio();
        double indexReturn = value.getIndices().stream().filter(item -> item.getReturn1d() != null)
                .mapToDouble(MarketIndexPerformance::getReturn1d).average().orElse(0D);
        if (ratio == null) {
            return "市场宽度不可用，指数表现仅供低置信度参考";
        }
        if (ratio >= 0.58D && indexReturn > 0D) {
            return withPressure("主要指数与个股宽度共振走强", value);
        }
        if (ratio <= 0.42D && indexReturn > 0D) {
            return withPressure("指数上涨但个股宽度偏弱，行情可能由权重驱动", value);
        }
        if (ratio <= 0.42D) {
            return withPressure("主要指数与多数个股共同承压", value);
        }
        return withPressure("市场涨跌分布相对均衡，尚未形成广泛共振", value);
    }

    private String withPressure(String base, MarketBreadthSnapshot value) {
        List<String> clauses = new ArrayList<>();
        if (value.getVolumePressure() != null
                && value.getVolumePressure().getAdvanceAmountRatio() != null) {
            double pressure = value.getVolumePressure().getAdvanceAmountRatio();
            if (pressure >= 0.6D) {
                clauses.add("上涨成交额占优");
            } else if (pressure <= 0.4D) {
                clauses.add("下跌成交额占优");
            }
        }
        if (value.getBreadthMomentum() != null) {
            String status = value.getBreadthMomentum().getStatus();
            if ("BULLISH_THRUST".equals(status) || "RECOVERING".equals(status)) {
                clauses.add("宽度动量改善");
            } else if ("WEAKENING".equals(status)) {
                clauses.add("宽度动量转弱");
            }
        }
        return clauses.isEmpty() ? base : base + "；" + String.join("，", clauses);
    }

    private MarketBreadthSnapshot unavailable(LocalDate businessDate, RuntimeException error) {
        MarketBreadthSnapshot value = new MarketBreadthSnapshot();
        value.setBusinessDate(businessDate);
        value.setSourceCode("UNAVAILABLE");
        value.setSourceFamily("UNAVAILABLE");
        value.setQualityStatus("UNAVAILABLE");
        value.getWarnings().add("全A市场宽度不可用：" + safe(error));
        return value;
    }

    private String safe(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static final class IndexDefinition {
        private final String code;
        private final String name;

        private IndexDefinition(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
