package com.finscope.service.marketpulse;

import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketIndexPerformance;
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
        breadth.setInterpretation(interpretation(breadth));
        return breadth;
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
            return "主要指数与个股宽度共振走强";
        }
        if (ratio <= 0.42D && indexReturn > 0D) {
            return "指数上涨但个股宽度偏弱，行情可能由权重驱动";
        }
        if (ratio <= 0.42D) {
            return "主要指数与多数个股共同承压";
        }
        return "市场涨跌分布相对均衡，尚未形成广泛共振";
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
