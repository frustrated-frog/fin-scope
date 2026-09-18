package com.finscope.service.attribution;

import com.finscope.domain.attribution.AttributionMarketContext;
import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.quote.PythonDailyBarClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** 只以目标日期已存在的日线计算行情对照；缺失时保留缺口，不借用最新行情。 */
@Service
@Slf4j
public class AttributionMarketContextService {
    @Resource
    private PythonDailyBarClient dailyBarClient;

    public AttributionMarketContext capture(Instrument instrument, LocalDate date) {
        AttributionMarketContext result = new AttributionMarketContext();
        result.setInstrumentCode(instrument.getCode());
        result.setReportDate(date == null ? null : date.toString());
        result.setCapturedAt(LocalDateTime.now().toString());
        result.setSource("本地行情服务日线快照");
        result.getLimitations().add("未提供可靠的目标日行业与同行对照，不能判断是否领先同行。");
        result.getLimitations().add("相对基准的差值仅为表现对照，不代表公司事件的收益贡献。");
        if (date == null || instrument.getCode() == null || !instrument.getCode().matches("[0-9]{6}")) {
            result.getLimitations().add("当前标的缺少适配的历史行情，不能确认异动幅度。");
            return result;
        }
        try {
            List<DailyBarPoint> bars = historical(dailyBarClient.fetchDailyBars(instrument.getCode(), 250), date);
            DailyBarPoint target = target(bars, date);
            if (target != null) {
                result.setStockChangePct(change(bars));
                result.setQuoteVerified(result.getStockChangePct() != null);
                int n = bars.size();
                if (n >= 7) {
                    result.setPriorFiveSessionChangePct(percent(bars.get(n - 2).getClose(), bars.get(n - 7).getClose()));
                }
                if (n >= 6 && target.getAmount() != null) {
                    BigDecimal total = BigDecimal.ZERO;
                    boolean complete = true;
                    for (int i = n - 6; i < n - 1; i++) {
                        if (bars.get(i).getAmount() == null) {
                            complete = false;
                            break;
                        }
                        total = total.add(bars.get(i).getAmount());
                    }
                    if (complete && total.signum() > 0) {
                        result.setAmountRatio(target.getAmount().doubleValue() * 5 / total.doubleValue());
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("研判个股行情不可用 code={} date={} error={}", instrument.getCode(), date, ex.getClass().getSimpleName());
        }
        if (!result.isQuoteVerified()) {
            result.getLimitations().add("目标日个股日线缺失，未用其他日期或前端数值代替。");
        }
        result.setBenchmarkName("沪深300（宽基参照）");
        result.setBenchmarkCode("000300.SH");
        try {
            List<DailyBarPoint> bars = historical(dailyBarClient.fetchMarketBenchmark(250), date);
            if (target(bars, date) != null) {
                result.setBenchmarkChangePct(change(bars));
            }
        } catch (RuntimeException ex) {
            log.warn("研判基准行情不可用 date={} error={}", date, ex.getClass().getSimpleName());
        }
        if (result.getBenchmarkChangePct() == null) {
            result.getLimitations().add("目标日基准行情缺失，不能判断相对市场强弱。");
        } else if (result.getStockChangePct() != null) {
            result.setRelativeChangePct(result.getStockChangePct() - result.getBenchmarkChangePct());
        }
        result.getLimitations().add("按日展示；当日日线可能尚未收盘，不能据此判断盘中事件与价格先后。");
        return result;
    }

    private List<DailyBarPoint> historical(List<DailyBarPoint> rows, LocalDate date) {
        TreeMap<LocalDate, DailyBarPoint> byDate = new TreeMap<>();
        for (DailyBarPoint bar : rows) {
            if (bar.getTradeDate() != null && !bar.getTradeDate().isAfter(date)) {
                byDate.put(bar.getTradeDate(), bar);
            }
        }
        return new ArrayList<>(byDate.values());
    }

    private DailyBarPoint target(List<DailyBarPoint> bars, LocalDate date) {
        return !bars.isEmpty() && date.equals(bars.get(bars.size() - 1).getTradeDate()) ? bars.get(bars.size() - 1) : null;
    }

    private Double change(List<DailyBarPoint> bars) {
        DailyBarPoint last = bars.get(bars.size() - 1);
        if (last.getChangePct() != null) {
            return last.getChangePct().doubleValue();
        }
        return bars.size() < 2 ? null : percent(last.getClose(), bars.get(bars.size() - 2).getClose());
    }

    private Double percent(BigDecimal value, BigDecimal base) {
        return value == null || base == null || base.signum() <= 0 ? null : (value.doubleValue() / base.doubleValue() - 1) * 100;
    }
}
