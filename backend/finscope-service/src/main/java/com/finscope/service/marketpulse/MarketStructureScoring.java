package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.*;
import com.finscope.domain.marketpulse.*;
import com.finscope.domain.marketpulse.ai.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.ToDoubleFunction;
import static com.finscope.common.enums.marketpulse.MarketStructureDimension.*;

/** 可重放的启发式基准；各分项等权，历史位置只辅助，不表示收益概率。 */
@Service
public class MarketStructureScoring {
    public static final String VERSION = "structure-v2";
    private static final int MIN_MARKET_SAMPLE = 100;
    private static final int MIN_SECTORS = 20;
    private static final double MIN_TREND_COVERAGE = .8;

    public List<MarketStructureScore> calculate(MarketPulseWorkspace current, List<MarketPulseWorkspace> history) {
        var rows = new ArrayList<MarketStructureScore>();
        var repair = row(REPAIR_QUALITY, "修复质量");
        var expansion = row(EXPANSION, "扩散程度");
        var leadership = row(LEADERSHIP, "主线持续性");
        var fragility = row(FRAGILITY, "结构脆弱性");
        var turn = row(TURN_CONFIRMATION, "转折证据");
        rows.addAll(List.of(repair, expansion, leadership, fragility, turn));
        MarketBreadthSnapshot breadth = usable(current);
        if (breadth == null || !ratio(breadth.getAdvanceRatio())) {
            rows.forEach(r -> r.setScope("当日宽度过期、有效家数不足或参与比例缺失，暂停评分"));
            return rows;
        }
        var prior = history.stream().filter(w -> w.getBusinessDate() != null && w.getBusinessDate().isBefore(current.getBusinessDate()))
                .sorted(Comparator.comparing(MarketPulseWorkspace::getBusinessDate).reversed()).limit(20).toList();
        // 只比较相邻已保存交易日；中间缺失或样本变化时不跳过该日去挑选有利样本。
        MarketBreadthSnapshot previous = prior.isEmpty() ? null : usable(prior.getFirst());
        boolean comparable = comparable(breadth, previous);
        double participation = breadth.getAdvanceRatio();
        part(repair, "当日上涨参与", participation * 100);
        part(expansion, "当日上涨参与", participation * 100);
        part(fragility, "下跌参与", (1 - participation) * 100);
        Double pressure = pressure(breadth);
        if (ratio(pressure)) {
            part(repair, "上涨成交额支持", pressure * 100);
            part(fragility, "下跌成交额压力", (1 - pressure) * 100);
        }
        if (comparable && ratio(previous.getAdvanceRatio())) {
            part(repair, "参与度较前日变化", 50 + (participation - previous.getAdvanceRatio()) * 300);
            part(turn, "当前上涨参与支持", participation * 100);
            if (finite(breadth.getMedianChangePct())) {
                part(turn, "当前个股中位表现", 50 + breadth.getMedianChangePct() * 12);
            }
            part(turn, "参与度较前日变化", 50 + (participation - previous.getAdvanceRatio()) * 300);
            if (ratio(pressure) && ratio(pressure(previous))) {
                part(turn, "成交方向较前日变化", 50 + (pressure - pressure(previous)) * 300);
            }
            int improving = 0;
            MarketBreadthSnapshot newer = breadth;
            for (MarketPulseWorkspace item : prior) {
                var older = usable(item);
                if (!comparable(newer, older) || !ratio(older.getAdvanceRatio()) || newer.getAdvanceRatio() <= older.getAdvanceRatio()) {
                    break;
                }
                improving++;
                newer = older;
            }
            part(repair, "改善持续性（最多五日）", Math.min(5, improving) * 20);
            part(turn, "改善持续性（最多五日）", Math.min(5, improving) * 20);
        }
        var observations = prior.stream().map(this::usable).filter(b -> comparable(breadth, b) && ratio(b.getAdvanceRatio())).toList();
        if (observations.size() >= 5) {
            // 相同值采用中位秩，避免平盘历史被打成满分。
            double rank = observations.stream().mapToDouble(b -> b.getAdvanceRatio() < participation ? 1 : b.getAdvanceRatio() == participation ? .5 : 0).average().orElse(.5);
            part(repair, "近期可比参与度分位（" + observations.size() + "日）", rank * 100);
        }
        var trend = breadth.getTrendBreadth();
        if (trend != null && coverage(trend.getMa20ValidCount(), breadth.getValidCount()) && ratio(trend.getMa20Ratio())) {
            part(expansion, "覆盖充分的MA20宽度", trend.getMa20Ratio() * 100);
            if (comparable && previous.getTrendBreadth() != null
                    && Objects.equals(trend.getMa20ValidCount(), previous.getTrendBreadth().getMa20ValidCount())
                    && ratio(previous.getTrendBreadth().getMa20Ratio())) {
                part(turn, "同分母MA20变化", 50 + (trend.getMa20Ratio() - previous.getTrendBreadth().getMa20Ratio()) * 300);
            }
        }
        var sectors = current.getSectors().stream().filter(s -> s.getSectorCode() != null)
                .collect(java.util.stream.Collectors.toMap(SectorRotationItem::getSectorCode, s -> s, (a, b) -> a, LinkedHashMap::new)).values().stream().toList();
        var daily = sectors.stream().filter(s -> finite(s.getReturn1d())).toList();
        var leaders = sectors.stream().filter(s -> finite(s.getReturn5d()) && finite(s.getExcessReturn5d())).toList();
        if (daily.size() >= MIN_SECTORS) {
            part(expansion, "已覆盖行业上涨占比", mean(daily, s -> s.getReturn1d() > 0 ? 100 : 0));
        }
        if (leaders.size() >= MIN_SECTORS) {
            part(leadership, "行业五日正收益占比", mean(leaders, s -> s.getReturn5d() > 0 ? 100 : 0));
            part(leadership, "行业五日超额为正占比", mean(leaders, s -> s.getExcessReturn5d() > 0 ? 100 : 0));
            // 有正收益且超额为正才计为延续，零值与缺失不当作持续强势。
            part(leadership, "强势延续占比", mean(leaders, s -> s.getReturn5d() > 0 && s.getExcessReturn5d() > 0 && s.getPersistenceDays() >= 3 ? 100 : 0));
        }
        if (finite(breadth.getMedianChangePct())) {
            part(fragility, "个股中位涨跌压力", 50 - breadth.getMedianChangePct() * 12);
        }
        String scope = "全市场有效家数 " + breadth.getValidCount() + "；可用行业 " + daily.size()
                + "；均线覆盖不足八成时不纳入；跨日" + (comparable ? "采用相邻可比快照" : "不可比或缺失，未使用变化分项");
        for (MarketStructureScore score : rows) {
            score.setScope(scope);
            // 单一指标不足以代表结构维度，保持空值。
            if (score.getComponents().size() >= 2) {
                int base = (int) Math.round(score.getComponents().values().stream().mapToInt(Integer::intValue).average().orElseThrow());
                score.setBaselineScore(base);
                score.setFinalScore(base);
            } else {
                score.setScope(scope + "；本维度有效分项不足两项，暂停评分");
            }
        }
        return rows;
    }

    public List<MarketStructureScore> apply(List<MarketStructureScore> rules, List<MarketScoreAdjustment> adjustments) {
        var result = new ArrayList<MarketStructureScore>();
        for (var baseline : rules) {
            var copy = row(baseline.getDimension(), baseline.getLabel());
            copy.setBaselineScore(baseline.getBaselineScore());
            copy.setFinalScore(baseline.getBaselineScore());
            copy.setScope(baseline.getScope());
            copy.setComponents(new LinkedHashMap<>(baseline.getComponents()));
            copy.setFactIds(new ArrayList<>(baseline.getFactIds()));
            for (var adjustment : adjustments) {
                if (baseline.getDimension() == adjustment.getDimension() && baseline.getBaselineScore() != null) {
                    copy.setAdjustment(adjustment.getDelta());
                    copy.setFinalScore(clamp(baseline.getBaselineScore() + adjustment.getDelta()));
                    copy.setReason(adjustment.getReason());
                    copy.setCounterEvidence(adjustment.getCounterEvidence());
                    copy.setFactIds(adjustment.getFactIds());
                }
            }
            result.add(copy);
        }
        return result;
    }

    public void observations(MarketAiInput input, MarketPulseWorkspace workspace) {
        var breadth = usable(workspace);
        if (breadth == null) {
            return;
        }
        observe(input, MarketScenarioMetric.ADVANCE_RATIO, ratio(breadth.getAdvanceRatio()) ? breadth.getAdvanceRatio() : null, breadth.getValidCount());
        observe(input, MarketScenarioMetric.MEDIAN_RETURN, breadth.getMedianChangePct(), breadth.getValidCount());
        observe(input, MarketScenarioMetric.TOTAL_AMOUNT, finite(breadth.getTotalAmount()) && breadth.getTotalAmount() > 0 ? breadth.getTotalAmount() : null, breadth.getValidCount());
        observe(input, MarketScenarioMetric.ADVANCE_AMOUNT_RATIO, ratio(pressure(breadth)) ? pressure(breadth) : null, breadth.getValidCount());
        var trend = breadth.getTrendBreadth();
        if (trend != null && coverage(trend.getMa20ValidCount(), breadth.getValidCount()) && ratio(trend.getMa20Ratio())) {
            observe(input, MarketScenarioMetric.MA20_RATIO, trend.getMa20Ratio(), trend.getMa20ValidCount());
        }
    }
    private void observe(MarketAiInput input, MarketScenarioMetric metric, Double value, Integer sample) {
        if (finite(value)) {
            input.getObservations().put(metric, value);
            input.getObservationSamples().put(metric, sample);
        }
    }
    public List<MarketScenarioReview> review(MarketAiView previous, MarketAiInput current) {
        var result = new ArrayList<MarketScenarioReview>();
        if (previous.getReport() == null || previous.getReport().getScenarios() == null) {
            return result;
        }
        for (var scenario : previous.getReport().getScenarios()) {
            var review = new MarketScenarioReview();
            review.setSourceDate(previous.getBusinessDate().toString());
            review.setTitle(scenario.getThesis().getTitle());
            review.setConfirmation(evaluate(scenario.getConfirmation(), previous, current));
            review.setInvalidation(evaluate(scenario.getInvalidation(), previous, current));
            review.setConfirmationDetail(description(scenario.getConfirmation(), previous, current));
            review.setInvalidationDetail(description(scenario.getInvalidation(), previous, current));
            result.add(review);
        }
        return result;
    }
    private MarketConditionStatus evaluate(MarketScenarioCondition condition, MarketAiView previous, MarketAiInput current) {
        if (condition == null || condition.getMetric() == null || condition.getDirection() == null) {
            return MarketConditionStatus.UNAVAILABLE;
        }
        var metric = condition.getMetric();
        Double before = previous.getObservations().get(metric);
        Double after = current.getObservations().get(metric);
        Integer oldSample = previous.getObservationSamples().get(metric);
        Integer newSample = current.getObservationSamples().get(metric);
        if (!finite(before) || !finite(after) || oldSample == null || newSample == null
                || (metric == MarketScenarioMetric.MA20_RATIO ? !oldSample.equals(newSample) : Math.abs(oldSample - newSample) > oldSample * .02)) {
            return MarketConditionStatus.UNAVAILABLE;
        }
        boolean met = condition.getDirection() == MarketScenarioDirection.RISE ? after > before : after < before;
        return met ? MarketConditionStatus.MET : MarketConditionStatus.NOT_MET;
    }
    private String description(MarketScenarioCondition condition, MarketAiView previous, MarketAiInput current) {
        if (condition == null || condition.getMetric() == null) {
            return "条件缺失";
        }
        var metric = condition.getMetric();
        String label = switch (metric) {
            case ADVANCE_RATIO -> "上涨比例";
            case MEDIAN_RETURN -> "涨跌中位数";
            case TOTAL_AMOUNT -> "成交额";
            case ADVANCE_AMOUNT_RATIO -> "上涨成交额占比";
            case MA20_RATIO -> "MA20上方比例";
        };
        if (evaluate(condition, previous, current) == MarketConditionStatus.UNAVAILABLE) {
            return label + "：数据缺失或样本不可比";
        }
        return label + "：" + formatted(metric, previous.getObservations().get(metric)) + " → " + formatted(metric, current.getObservations().get(metric))
                + "；观察方向为" + (condition.getDirection() == MarketScenarioDirection.RISE ? "上升" : "下降");
    }
    private String formatted(MarketScenarioMetric metric, double value) {
        return switch (metric) {
            case TOTAL_AMOUNT -> String.format(Locale.ROOT, "%.2f亿元", value / 1e8);
            case MEDIAN_RETURN -> String.format(Locale.ROOT, "%+.2f%%", value);
            default -> String.format(Locale.ROOT, "%.2f%%", value * 100);
        };
    }

    private MarketBreadthSnapshot usable(MarketPulseWorkspace workspace) {
        var b = workspace.getBreadth();
        if (workspace.getQualityStatus() == com.finscope.common.enums.marketpulse.MarketPulseQualityStatus.STALE
                || workspace.getQualityStatus() == com.finscope.common.enums.marketpulse.MarketPulseQualityStatus.UNAVAILABLE
                || b == null || !Objects.equals(workspace.getBusinessDate(), b.getBusinessDate()) || b.getValidCount() == null
                || b.getValidCount() < MIN_MARKET_SAMPLE || Set.of("STALE", "UNAVAILABLE").contains(Objects.toString(b.getQualityStatus(), ""))) {
            return null;
        }
        return b;
    }
    private boolean comparable(MarketBreadthSnapshot a, MarketBreadthSnapshot b) {
        return a != null && b != null && Math.abs(a.getValidCount() - b.getValidCount()) <= a.getValidCount() * .02;
    }
    private boolean coverage(Integer sample, Integer total) {
        return sample != null && total != null && sample >= MIN_MARKET_SAMPLE && sample <= total && sample >= total * MIN_TREND_COVERAGE;
    }
    private Double pressure(MarketBreadthSnapshot b) {
        return b.getVolumePressure() == null ? null : b.getVolumePressure().getAdvanceAmountRatio();
    }
    private boolean finite(Double n) {
        return n != null && Double.isFinite(n);
    }
    private boolean ratio(Double n) {
        return finite(n) && n >= 0 && n <= 1;
    }
    private double mean(List<SectorRotationItem> values, ToDoubleFunction<SectorRotationItem> fn) {
        return values.stream().mapToDouble(fn).average().orElseThrow();
    }
    private MarketStructureScore row(MarketStructureDimension dimension, String label) {
        var row = new MarketStructureScore();
        row.setDimension(dimension);
        row.setLabel(label);
        return row;
    }
    private void part(MarketStructureScore row, String label, double value) {
        row.getComponents().put(label, clamp(value));
    }
    private int clamp(double value) {
        return (int) Math.round(Math.max(0, Math.min(100, value)));
    }
}
