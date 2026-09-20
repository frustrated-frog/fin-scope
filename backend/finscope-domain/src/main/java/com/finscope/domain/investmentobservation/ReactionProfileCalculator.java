package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionWindowStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** 仅使用已收盘日频数据描述过程，价格路径与相对收益差分别计算。 */
public class ReactionProfileCalculator {
    public ReactionProfile calculate(ReactionCalculation calculation, LocalDateTime now) {
        ReactionProfile result = new ReactionProfile();
        List<ReactionPoint> points = calculation.getPoints();
        result.setWindowEnded(points.stream().anyMatch(point -> point.getSession() == 5 && !point.getTradeDate().atTime(15, 0).isAfter(now)));
        result.setDataComplete(points.size() == 11 && points.stream().allMatch(point -> point.getStatus() == ReactionWindowStatus.READY));
        result.setHasGaps(points.stream().anyMatch(point -> point.getStatus() == ReactionWindowStatus.MISSING_DATA || point.getStatus() == ReactionWindowStatus.SUSPENDED));
        points.stream().filter(point -> point.getSession() == -5).findFirst().ifPresent(point -> {
            result.setBeforeStockPct(invert(point.getStockReturnPct()));
            result.setBeforeBenchmarkPct(invert(point.getBenchmarkReturnPct()));
        });
        if (result.getBeforeStockPct() != null && result.getBeforeBenchmarkPct() != null) {
            result.setBeforeRelativePp(result.getBeforeStockPct().subtract(result.getBeforeBenchmarkPct()));
        }
        List<ReactionPoint> after = points.stream().filter(point -> point.getSession() > 0 && point.getStatus() == ReactionWindowStatus.READY
                && point.getRelativeReturnPp() != null).sorted(Comparator.comparingInt(ReactionPoint::getSession)).toList();
        if (after.isEmpty()) {
            result.setSummary(result.isWindowEnded() ? "观察窗口已结束，行情存在缺口，保留已知结果。" : "等待首个交易日收盘；可先查看报道前走势。");
            return result;
        }
        ReactionPoint current = after.get(after.size() - 1);
        ReactionPoint peak = after.stream().max(Comparator.comparing(ReactionPoint::getRelativeReturnPp)
                .thenComparing(point -> -point.getSession())).orElseThrow();
        result.setObservedSessions(current.getSession());
        result.setCurrentRelativePp(current.getRelativeReturnPp());
        result.setPeakRelativePp(peak.getRelativeReturnPp());
        result.setPeakSession(peak.getSession());
        result.setGivebackPp(peak.getRelativeReturnPp().subtract(current.getRelativeReturnPp()));
        after.stream().filter(point -> point.getSession() == 1).findFirst().ifPresent(point -> result.setFirstRelativePp(point.getRelativeReturnPp()));
        BigDecimal high = BigDecimal.valueOf(100);
        BigDecimal drawdown = BigDecimal.ZERO;
        for (ReactionPoint point : after) {
            if (point.getStockReturnPct() == null) {
                continue;
            }
            BigDecimal price = BigDecimal.valueOf(100).add(point.getStockReturnPct());
            high = high.max(price);
            drawdown = drawdown.max(high.subtract(price).multiply(BigDecimal.valueOf(100)).divide(high, 4, RoundingMode.HALF_UP));
        }
        result.setMaxDrawdownPct(drawdown);
        List<BigDecimal> preVolumes = points.stream().filter(point -> point.getSession() >= -4 && point.getSession() <= 0
                && point.getStatus() == ReactionWindowStatus.READY && point.getVolume() != null && point.getVolume().signum() > 0)
                .map(ReactionPoint::getVolume).toList();
        if (preVolumes.size() == 5) {
            BigDecimal mean = preVolumes.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(5), 4, RoundingMode.HALF_UP);
            result.setCurrentVolumeRatio(ratio(current.getVolume(), mean));
            after.stream().filter(point -> point.getSession() == 1).findFirst().ifPresent(point -> result.setFirstVolumeRatio(ratio(point.getVolume(), mean)));
        }
        String before = result.getBeforeRelativePp() == null ? "事前行情不足" : "报道前五日相对市场 " + number(result.getBeforeRelativePp()) + "pp";
        String path;
        if (result.getGivebackPp().compareTo(BigDecimal.valueOf(2)) >= 0) {
            path = "第" + peak.getSession() + "日达到相对高点，随后回吐 " + number(result.getGivebackPp()) + "pp";
        } else if (drawdown.compareTo(BigDecimal.valueOf(5)) >= 0 && current.getRelativeReturnPp().signum() > 0) {
            path = "经历明显收盘回撤后修复，目前仍强于市场";
        } else {
            path = "当前相对市场 " + number(current.getRelativeReturnPp()) + "pp";
        }
        result.setSummary(before + "；截至第" + current.getSession() + "个交易日，" + path + "。"
                + (result.isHasGaps() ? "存在行情缺口，路径特征仅覆盖已知数据。" : "")
                + (result.isWindowEnded() ? "五日观察窗口已结束。" : "观察仍在进行。"));
        return result;
    }

    private BigDecimal invert(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.valueOf(-100)) <= 0) {
            return null;
        }
        return BigDecimal.valueOf(10000).divide(BigDecimal.valueOf(100).add(value), 4, RoundingMode.HALF_UP).subtract(BigDecimal.valueOf(100));
    }

    private BigDecimal ratio(BigDecimal value, BigDecimal mean) {
        return value == null || mean.signum() <= 0 ? null : value.divide(mean, 2, RoundingMode.HALF_UP);
    }

    private String number(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
