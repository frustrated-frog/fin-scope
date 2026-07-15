package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CapitalBehaviorMetricsService {
    private static final String LEGACY_OBJECTIVE_TAG_VERSION = "capital-signal-v1";

    public CapitalBehaviorMetrics derive(List<CapitalFlowPoint> intraday,
                                         List<CapitalFlowPoint> daily,
                                         List<CapitalBehaviorSignal> signals) {
        List<CapitalFlowPoint> sortedIntraday = sorted(intraday);
        List<CapitalFlowPoint> sortedDaily = sorted(daily);
        CapitalBehaviorMetrics metrics = new CapitalBehaviorMetrics();
        metrics.setLatest(latest(sortedIntraday, sortedDaily));
        metrics.setIntradayStreak(streak(latestTradingDay(sortedIntraday), true));
        metrics.setDailyStreak(streak(sortedDaily, false));
        metrics.setObjectiveTags(tags(signals));
        return metrics;
    }

    private CapitalBehaviorMetrics.Latest latest(List<CapitalFlowPoint> intraday, List<CapitalFlowPoint> daily) {
        CapitalFlowPoint latestIntraday = last(intraday);
        CapitalFlowPoint latestDaily = last(daily);
        if (latestIntraday == null && latestDaily == null) return null;
        if (latestIntraday != null && (latestDaily == null
                || latestIntraday.getObservedAt().toLocalDate().isAfter(latestDaily.getObservedAt().toLocalDate()))) {
            return intradayLatest(latestTradingDay(intraday), latestDaily);
        }
        if (latestIntraday != null
                && latestIntraday.getObservedAt().toLocalDate().equals(latestDaily.getObservedAt().toLocalDate())
                && latestDaily.getIntervalTradeAmount() == null) {
            return intradayLatest(latestTradingDay(intraday), latestDaily);
        }
        return pointLatest(latestDaily, latestIntraday == null ? null : latestIntraday.getObservedAt());
    }

    private CapitalBehaviorMetrics.Latest pointLatest(CapitalFlowPoint point, LocalDateTime observedAtOverride) {
        CapitalBehaviorMetrics.Latest latest = new CapitalBehaviorMetrics.Latest();
        latest.setTradeAmount(point.getIntervalTradeAmount());
        latest.setTradeVolume(point.getTradeVolume());
        latest.setTurnoverRate(point.getTurnoverRate());
        latest.setVolumeRatio(point.getVolumeRatio());
        latest.setMainNetInflow(point.getMainNetInflow());
        latest.setMainNetInflowSharePct(share(point.getMainNetInflow(), point.getIntervalTradeAmount()));
        latest.setObservedAt(observedAtOverride == null ? point.getObservedAt() : observedAtOverride);
        return latest;
    }

    private CapitalBehaviorMetrics.Latest intradayLatest(List<CapitalFlowPoint> currentDay, CapitalFlowPoint sameDayDaily) {
        CapitalFlowPoint last = last(currentDay);
        BigDecimal tradeAmount = sum(currentDay, Metric.AMOUNT);
        BigDecimal tradeVolume = sum(currentDay, Metric.VOLUME);
        BigDecimal mainNetInflow = sum(currentDay, Metric.MAIN_NET_INFLOW);
        CapitalBehaviorMetrics.Latest latest = new CapitalBehaviorMetrics.Latest();
        latest.setTradeAmount(tradeAmount);
        latest.setTradeVolume(tradeVolume);
        latest.setMainNetInflow(mainNetInflow);
        latest.setMainNetInflowSharePct(share(mainNetInflow, tradeAmount));
        latest.setTurnoverRate(lastNonNull(currentDay, Metric.TURNOVER_RATE));
        latest.setVolumeRatio(lastNonNull(currentDay, Metric.VOLUME_RATIO));
        if (sameDayDaily != null && sameDayDaily.getObservedAt().toLocalDate().equals(last.getObservedAt().toLocalDate())) {
            if (latest.getTurnoverRate() == null) latest.setTurnoverRate(sameDayDaily.getTurnoverRate());
            if (latest.getVolumeRatio() == null) latest.setVolumeRatio(sameDayDaily.getVolumeRatio());
        }
        latest.setObservedAt(last.getObservedAt());
        return latest;
    }

    private BigDecimal share(BigDecimal mainNetInflow, BigDecimal tradeAmount) {
        if (mainNetInflow == null || tradeAmount == null || tradeAmount.signum() <= 0) return null;
        return mainNetInflow.multiply(new BigDecimal("100"))
                .divide(tradeAmount, 6, RoundingMode.HALF_UP);
    }

    private CapitalBehaviorMetrics.Streak streak(List<CapitalFlowPoint> points, boolean requireAdjacentBuckets) {
        CapitalBehaviorMetrics.Streak streak = new CapitalBehaviorMetrics.Streak();
        CapitalFlowPoint latest = last(points);
        streak.setGranularity(latest == null ? null : latest.getGranularity());
        if (latest == null || latest.getMainNetInflow() == null || latest.getMainNetInflow().signum() == 0) {
            streak.setDirection("FLAT");
            return streak;
        }
        int sign = latest.getMainNetInflow().signum();
        int periods = 0;
        CapitalFlowPoint earliest = latest;
        CapitalFlowPoint newer = null;
        for (int index = points.size() - 1; index >= 0; index--) {
            CapitalFlowPoint point = points.get(index);
            if (point.getMainNetInflow() == null || point.getMainNetInflow().signum() != sign) break;
            if (newer != null && requireAdjacentBuckets && !adjacent(point, newer)) break;
            periods++;
            earliest = point;
            newer = point;
        }
        streak.setDirection(sign > 0 ? "INFLOW" : "OUTFLOW");
        streak.setPeriods(periods);
        streak.setSince(earliest.getObservedAt());
        streak.setThrough(latest.getObservedAt());
        return streak;
    }

    private boolean adjacent(CapitalFlowPoint earlier, CapitalFlowPoint later) {
        int minutes = granularityMinutes(later.getGranularity());
        if (minutes <= 0 || !earlier.getObservedAt().toLocalDate().equals(later.getObservedAt().toLocalDate())) return false;
        if (Duration.between(earlier.getObservedAt(), later.getObservedAt()).toMinutes() == minutes) return true;
        LocalDateTime expected = earlier.getObservedAt().plusMinutes(minutes);
        LocalTime time = expected.toLocalTime();
        if (time.isAfter(LocalTime.of(11, 30)) && time.isBefore(LocalTime.of(13, 0))) {
            expected = expected.toLocalDate().atTime(13, 0);
        }
        return expected.equals(later.getObservedAt());
    }

    private int granularityMinutes(String granularity) {
        if (granularity == null || !granularity.startsWith("MINUTE_")) return -1;
        try { return Integer.parseInt(granularity.substring("MINUTE_".length())); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private List<CapitalBehaviorMetrics.ObjectiveTag> tags(List<CapitalBehaviorSignal> signals) {
        List<CapitalBehaviorMetrics.ObjectiveTag> result = new ArrayList<CapitalBehaviorMetrics.ObjectiveTag>();
        if (signals == null) return result;
        for (CapitalBehaviorSignal signal : signals) {
            if (!supportsObjectiveTagVersion(signal.getVersion())) continue;
            CapitalBehaviorMetrics.ObjectiveTag tag = new CapitalBehaviorMetrics.ObjectiveTag();
            tag.setCode(signal.getType());
            tag.setLabel(label(signal.getType()));
            tag.setExplanation(explanation(signal.getType()));
            tag.setWindow(signal.getWindow());
            tag.setVersion(signal.getVersion());
            tag.setMetricRefs(signal.getMetricRefs());
            tag.setActualValues(signal.getActualValues());
            tag.setThresholds(signal.getThresholds());
            result.add(tag);
        }
        return result;
    }

    private boolean supportsObjectiveTagVersion(String version) {
        return LEGACY_OBJECTIVE_TAG_VERSION.equals(version)
                || CapitalBehaviorSignalService.VERSION.equals(version);
    }

    private String label(String type) {
        switch (type) {
            case "AMOUNT_EXPANSION_WITH_OUTFLOW": return "放量净流出";
            case "LOW_AMOUNT_INFLOW": return "缩量净流入";
            case "LOW_AMOUNT_OUTFLOW": return "缩量净流出";
            case "PRICE_FLOW_DIVERGENCE": return "量价资金背离";
            case "BIG_SMALL_ORDER_DIVERGENCE": return "大单与中小单背离";
            case "INTRADAY_FLOW_REVERSAL": return "日内资金方向反转";
            case "LATE_SESSION_INFLOW": return "尾盘流入增强";
            case "LATE_SESSION_OUTFLOW": return "尾盘流出增强";
            case "INTRADAY_ACCELERATING_INFLOW": return "日内资金加速流入";
            case "INTRADAY_ACCELERATING_OUTFLOW": return "日内资金加速流出";
            default: return "资金行为异常";
        }
    }

    private String explanation(String type) {
        switch (type) {
            case "AMOUNT_EXPANSION_WITH_OUTFLOW": return "成交额明显放大，同时主力净流向为负。";
            case "LOW_AMOUNT_INFLOW": return "成交额相对收缩，但主力净流向为正。";
            case "LOW_AMOUNT_OUTFLOW": return "成交额相对收缩，主力净流向为负。";
            case "PRICE_FLOW_DIVERGENCE": return "价格变化方向与主力净流向相反。";
            case "BIG_SMALL_ORDER_DIVERGENCE": return "公开大单与中小单资金方向相反。";
            case "INTRADAY_FLOW_REVERSAL": return "日内公开资金净额出现方向反转。";
            case "LATE_SESSION_INFLOW": return "尾盘流入在当日资金中占比较高。";
            case "LATE_SESSION_OUTFLOW": return "尾盘流出在当日资金中占比较高。";
            case "INTRADAY_ACCELERATING_INFLOW": return "最近日内资金净额呈加速流入。";
            case "INTRADAY_ACCELERATING_OUTFLOW": return "最近日内资金净额呈加速流出。";
            default: return "检测到可复算的资金行为变化。";
        }
    }

    private List<CapitalFlowPoint> sorted(List<CapitalFlowPoint> points) {
        if (points == null) return new ArrayList<CapitalFlowPoint>();
        return points.stream().sorted(Comparator.comparing(CapitalFlowPoint::getObservedAt)).collect(Collectors.toList());
    }

    private List<CapitalFlowPoint> latestTradingDay(List<CapitalFlowPoint> points) {
        CapitalFlowPoint latest = last(points);
        if (latest == null) return new ArrayList<CapitalFlowPoint>();
        LocalDate date = latest.getObservedAt().toLocalDate();
        return points.stream().filter(point -> date.equals(point.getObservedAt().toLocalDate())).collect(Collectors.toList());
    }

    private BigDecimal sum(List<CapitalFlowPoint> points, Metric metric) {
        BigDecimal total = BigDecimal.ZERO;
        boolean present = false;
        for (CapitalFlowPoint point : points) {
            BigDecimal value = metric.value(point);
            if (value != null) { total = total.add(value); present = true; }
        }
        return present ? total : null;
    }

    private BigDecimal lastNonNull(List<CapitalFlowPoint> points, Metric metric) {
        for (int index = points.size() - 1; index >= 0; index--) {
            BigDecimal value = metric.value(points.get(index));
            if (value != null) return value;
        }
        return null;
    }

    private CapitalFlowPoint last(List<CapitalFlowPoint> points) {
        return points.isEmpty() ? null : points.get(points.size() - 1);
    }

    private enum Metric {
        AMOUNT { BigDecimal value(CapitalFlowPoint point) { return point.getIntervalTradeAmount(); } },
        VOLUME { BigDecimal value(CapitalFlowPoint point) { return point.getTradeVolume(); } },
        MAIN_NET_INFLOW { BigDecimal value(CapitalFlowPoint point) { return point.getMainNetInflow(); } },
        TURNOVER_RATE { BigDecimal value(CapitalFlowPoint point) { return point.getTurnoverRate(); } },
        VOLUME_RATIO { BigDecimal value(CapitalFlowPoint point) { return point.getVolumeRatio(); } };
        abstract BigDecimal value(CapitalFlowPoint point);
    }
}
