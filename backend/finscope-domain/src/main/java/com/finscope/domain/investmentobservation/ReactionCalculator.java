package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionPathType;
import com.finscope.common.enums.investmentobservation.ReactionWindowStatus;
import com.finscope.domain.quant.data.QuantDailyBar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 纯日频领域计算。窗口由交易所日历提供，不由个股有行情的日期推断。 */
public class ReactionCalculator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public ReactionCalculation calculate(LocalDateTime publishedAt, List<LocalDate> sessions,
                                         List<QuantDailyBar> stock, List<QuantDailyBar> benchmark,
                                         LocalDateTime now) {
        if (sessions.size() != 11) {
            throw new IllegalArgumentException("事件窗口必须包含 -5 至 +5 的交易日");
        }
        Map<LocalDate, QuantDailyBar> stockByDate = index(stock);
        Map<LocalDate, QuantDailyBar> benchmarkByDate = index(benchmark);
        ReactionCalculation result = new ReactionCalculation();
        result.setBaselineDate(sessions.get(5));
        result.setFirstSession(sessions.get(6));
        result.setCalculatedAt(now);
        if (publishedAt.toLocalDate().equals(result.getFirstSession())
                && !publishedAt.toLocalTime().isBefore(LocalTime.of(9, 30))) {
            result.getWarnings().add("盘中公开信息：日频窗口包含当日公告前行情，不能隔离即时反应。");
        }
        for (int i = 0; i < sessions.size(); i++) {
            LocalDate date = sessions.get(i);
            ReactionPoint point = point(i - 5, date, result.getBaselineDate(), stockByDate, benchmarkByDate, now);
            result.getPoints().add(point);
        }
        for (int size : new int[]{1, 3, 5}) {
            result.getWindows().add(window(size, result.getPoints()));
        }
        result.setProfile(new ReactionProfileCalculator().calculate(result, now));
        result.setPathType(classify(result.getWindows()));
        if (result.getProfile().isDataComplete() && result.getProfile().getGivebackPp() != null) {
            if (result.getProfile().getPeakRelativePp().compareTo(BigDecimal.valueOf(2)) >= 0
                    && result.getProfile().getGivebackPp().compareTo(BigDecimal.valueOf(2)) >= 0) {
                result.setPathType(result.getProfile().getPeakSession() == 1 ? ReactionPathType.GIVEBACK : ReactionPathType.PEAK_GIVEBACK);
            } else if (result.getProfile().getMaxDrawdownPct().compareTo(BigDecimal.valueOf(5)) >= 0
                    && result.getProfile().getCurrentRelativePp().signum() > 0) {
                result.setPathType(ReactionPathType.RECOVERED);
            }
        }
        return result;
    }

    private ReactionPoint point(int offset, LocalDate date, LocalDate baseline,
                                Map<LocalDate, QuantDailyBar> stock, Map<LocalDate, QuantDailyBar> benchmark,
                                LocalDateTime now) {
        ReactionPoint point = new ReactionPoint();
        point.setSession(offset);
        point.setTradeDate(date);
        if (date.atTime(15, 0).isAfter(now)) {
            point.setStatus(ReactionWindowStatus.NOT_DUE);
            return point;
        }
        QuantDailyBar bar = stock.get(date);
        if (bar != null) {
            point.setClose(bar.getClose());
            point.setAdjustedClose(bar.getAdjustedClose());
            point.setVolume(bar.getVolume());
            point.setAmount(bar.getAmount());
        }
        point.setStockReturnPct(returnPct(stock.get(baseline), stock.get(date)));
        point.setBenchmarkReturnPct(returnPct(benchmark.get(baseline), benchmark.get(date)));
        if (point.getStockReturnPct() == null || point.getBenchmarkReturnPct() == null) {
            point.setStatus(ReactionWindowStatus.MISSING_DATA);
        } else if (offset > 0 && noTrades(stock.get(date))) {
            point.setStockReturnPct(null);
            point.setStatus(ReactionWindowStatus.SUSPENDED);
        } else {
            point.setRelativeReturnPp(point.getStockReturnPct().subtract(point.getBenchmarkReturnPct()));
            point.setStatus(ReactionWindowStatus.READY);
        }
        return point;
    }

    private ReactionWindow window(int size, List<ReactionPoint> points) {
        ReactionPoint end = points.get(5 + size);
        ReactionWindow window = new ReactionWindow();
        window.setSessions(size);
        window.setEndDate(end.getTradeDate());
        ReactionWindowStatus status = end.getStatus();
        if (status != ReactionWindowStatus.NOT_DUE) {
            for (int i = 6; i <= 5 + size; i++) {
                if (points.get(i).getStatus() != ReactionWindowStatus.READY) {
                    status = points.get(i).getStatus();
                    break;
                }
            }
        }
        window.setStatus(status);
        if (status == ReactionWindowStatus.READY) {
            window.setStockReturnPct(end.getStockReturnPct());
            window.setBenchmarkReturnPct(end.getBenchmarkReturnPct());
            window.setRelativeReturnPp(end.getRelativeReturnPp());
        }
        return window;
    }

    private ReactionPathType classify(List<ReactionWindow> windows) {
        if (windows.stream().anyMatch(window -> window.getStatus() != ReactionWindowStatus.READY)) {
            return ReactionPathType.OBSERVING;
        }
        BigDecimal first = windows.get(0).getRelativeReturnPp();
        BigDecimal fifth = windows.get(2).getRelativeReturnPp();
        BigDecimal two = BigDecimal.valueOf(2);
        if (first.compareTo(two) >= 0 && fifth.compareTo(first.subtract(two)) <= 0) {
            return ReactionPathType.GIVEBACK;
        }
        if (first.compareTo(two) >= 0 && fifth.compareTo(first.divide(two)) >= 0) {
            return ReactionPathType.PERSISTENT_STRENGTH;
        }
        if (first.abs().compareTo(BigDecimal.ONE) < 0 && fifth.compareTo(two) >= 0) {
            return ReactionPathType.DELAYED_STRENGTH;
        }
        if (fifth.compareTo(two.negate()) <= 0) {
            return ReactionPathType.RELATIVE_WEAKNESS;
        }
        return ReactionPathType.NO_CLEAR_PATTERN;
    }

    private Map<LocalDate, QuantDailyBar> index(List<QuantDailyBar> bars) {
        Map<LocalDate, QuantDailyBar> result = new TreeMap<>();
        for (QuantDailyBar bar : bars) {
            if (bar.getTradeDate() == null || result.putIfAbsent(bar.getTradeDate(), bar) != null) {
                throw new IllegalArgumentException("日线日期缺失或重复");
            }
        }
        return result;
    }

    private BigDecimal returnPct(QuantDailyBar baseline, QuantDailyBar end) {
        if (baseline == null || end == null || baseline.getAdjustedClose() == null
                || end.getAdjustedClose() == null || baseline.getAdjustedClose().signum() <= 0
                || end.getAdjustedClose().signum() <= 0) {
            return null;
        }
        return end.getAdjustedClose().divide(baseline.getAdjustedClose(), 10, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
    }

    private boolean noTrades(QuantDailyBar bar) {
        return bar != null && bar.getVolume() != null && bar.getVolume().signum() == 0;
    }
}
