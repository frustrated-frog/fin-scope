package com.finscope.service.quant.backtest;

import com.finscope.domain.quant.backtest.BacktestRequest;
import com.finscope.domain.quant.backtest.BacktestResult;
import com.finscope.domain.quant.backtest.EquityPoint;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.factor.FactorValue;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.service.quant.factor.FactorCalculator;
import com.finscope.service.quant.factor.FactorPreprocessor;
import com.finscope.service.quant.factor.FactorRegistry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.LinkedHashSet;

public class QuantBacktestEngine {
    private final FactorRegistry registry = new FactorRegistry();
    private final FactorCalculator calculator = new FactorCalculator();
    private final FactorPreprocessor preprocessor = new FactorPreprocessor();

    public BacktestResult run(BacktestRequest request) {
        if (request == null || request.getSpec() == null || request.getBars() == null) throw new IllegalArgumentException("回测输入不完整");
        QuantStrategySpec spec = request.getSpec();
        List<QuantDailyBar> rangedBars = new ArrayList<QuantDailyBar>();
        for (QuantDailyBar bar : request.getBars()) if ((spec.getStartDate() == null || !bar.getTradeDate().isBefore(spec.getStartDate()))
                && (spec.getEndDate() == null || !bar.getTradeDate().isAfter(spec.getEndDate()))) rangedBars.add(bar);
        TreeMap<LocalDate, Map<String, QuantDailyBar>> byDate = group(rangedBars);
        Map<String, List<QuantDailyBar>> histories = new LinkedHashMap<String, List<QuantDailyBar>>();
        PortfolioLedger ledger = new PortfolioLedger(request.getInitialCapital()); BacktestResult result = new BacktestResult();
        LocalDate pendingSignal = null; Map<String, Double> pendingTargets = null; int index = 0;
        int startAt = spec.getFilters() == null ? 0 : spec.getFilters().getMinTradingDays();
        for (QuantStrategySpec.FactorWeight factor : spec.getFactors()) startAt = Math.max(startAt, registry.get(factor.getCode()).getLookbackDays());
        double benchmarkNav = 1d; Map<String, Double> previousClose = new LinkedHashMap<String, Double>();
        Map<LocalDate, Set<String>> universe = universe(request, byDate.keySet());
        if (universe.isEmpty()) result.getWarnings().add("未提供时点股票池，使用当日可见行情标的作为研究范围");
        for (Map.Entry<LocalDate, Map<String, QuantDailyBar>> day : byDate.entrySet()) {
            LocalDate date = day.getKey(); Map<String, QuantDailyBar> bars = day.getValue();
            if (pendingTargets != null) { ledger.rebalance(pendingSignal, date, pendingTargets, bars, spec, result.getWarnings()); pendingTargets = null; }
            for (QuantDailyBar bar : bars.values()) histories.computeIfAbsent(bar.getInstrumentCode(), key -> new ArrayList<QuantDailyBar>()).add(bar);
            Set<String> dayMembers = universe.isEmpty() ? null : universe.getOrDefault(date, java.util.Collections.<String>emptySet());
            benchmarkNav *= benchmarkDailyReturn(bars, previousClose, dayMembers); for (QuantDailyBar bar : bars.values()) previousClose.put(bar.getInstrumentCode(), bar.getClose().doubleValue());
            ledger.rememberClose(bars);
            double asset = ledger.totalAsset(bars, false, result.getWarnings(), date); EquityPoint point = new EquityPoint(); point.setTradeDate(date);
            point.setTotalAsset(asset); point.setCash(ledger.getCash()); point.setPortfolioNav(asset / request.getInitialCapital());
            point.setBenchmarkNav(benchmarkNav); result.getEquityCurve().add(point);
            result.getPositions().addAll(ledger.snapshot(date, bars, asset));
            if (index >= startAt && (index - startAt) % spec.getPortfolio().getRebalanceEvery() == 0 && index < byDate.size() - 1) {
                pendingTargets = select(date, bars, histories, request.getFundamentals(), spec, result.getWarnings(), dayMembers); pendingSignal = date;
            }
            index++;
        }
        result.setTrades(new ArrayList<com.finscope.domain.quant.backtest.BacktestTrade>(ledger.getTrades()));
        PerformanceMetrics performance = new PerformanceMetrics();
        result.setMetrics(performance.calculate(result.getEquityCurve(), request.getAnnualRiskFreeRate(), ledger.turnover()));
        result.setAnnualPerformance(performance.annual(result.getEquityCurve()));
        result.getMetrics().setTradeCount(result.getTrades().size());
        if (!result.getEquityCurve().isEmpty()) {
            result.getMetrics().setBenchmarkReturn(result.getEquityCurve().get(result.getEquityCurve().size() - 1).getBenchmarkNav() - 1d);
            result.getMetrics().setExcessReturn(result.getMetrics().getTotalReturn() - result.getMetrics().getBenchmarkReturn());
        }
        return result;
    }

    private Map<String, Double> select(LocalDate date, Map<String, QuantDailyBar> today,
                                       Map<String, List<QuantDailyBar>> histories, List<QuantFundamentalSnapshot> fundamentals,
                                       QuantStrategySpec spec, List<String> warnings, Set<String> members) {
        Map<String, Double> scores = new LinkedHashMap<String, Double>();
        for (QuantStrategySpec.FactorWeight factor : spec.getFactors()) {
            List<FactorValue> raw = new ArrayList<FactorValue>();
            for (Map.Entry<String, QuantDailyBar> entry : today.entrySet()) {
                if (members != null && !members.contains(entry.getKey())) continue;
                QuantDailyBar bar = entry.getValue(); List<QuantDailyBar> history = histories.get(entry.getKey());
                if (!eligible(bar, history, spec)) continue;
                double value = calculator.value(factor.getCode(), history, latestVisible(fundamentals, entry.getKey(), date));
                if (Double.isFinite(value)) raw.add(new FactorValue(date, entry.getKey(), factor.getCode(), value));
            }
            Map<String, Double> normalized = preprocessor.normalize(raw);
            for (Map.Entry<String, Double> value : normalized.entrySet()) {
                double direction = "LOW".equals(factor.getDirection()) ? -1d : 1d;
                scores.put(value.getKey(), scores.getOrDefault(value.getKey(), 0d) + value.getValue() * factor.getWeight() * direction);
            }
        }
        List<Map.Entry<String, Double>> ordered = new ArrayList<Map.Entry<String, Double>>(scores.entrySet());
        ordered.sort(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry::getKey));
        Map<String, Double> targets = new LinkedHashMap<String, Double>(); int count = Math.min(spec.getPortfolio().getTopN(), ordered.size());
        if (count == 0) { warnings.add(date + " 没有通过因子和交易门禁的标的"); return targets; }
        for (int i = 0; i < count; i++) targets.put(ordered.get(i).getKey(), 1d / count); return targets;
    }

    private boolean eligible(QuantDailyBar bar, List<QuantDailyBar> history, QuantStrategySpec spec) {
        return bar != null && "TRADING".equals(bar.getTradeStatus()) && (!spec.getFilters().isExcludeSt() || !bar.isSt())
                && bar.getAmount().doubleValue() >= spec.getFilters().getMinAmount()
                && history != null && history.size() > spec.getFilters().getMinTradingDays();
    }
    private QuantFundamentalSnapshot latestVisible(List<QuantFundamentalSnapshot> values, String code, LocalDate date) {
        if (values == null) return null;
        return values.stream().filter(value -> code.equals(value.getInstrumentCode()) && !value.getDisclosedAt().isAfter(date))
                .max(Comparator.comparing(QuantFundamentalSnapshot::getDisclosedAt)
                        .thenComparing(QuantFundamentalSnapshot::getReportPeriod)).orElse(null);
    }
    private TreeMap<LocalDate, Map<String, QuantDailyBar>> group(List<QuantDailyBar> values) {
        TreeMap<LocalDate, Map<String, QuantDailyBar>> result = new TreeMap<LocalDate, Map<String, QuantDailyBar>>();
        List<QuantDailyBar> ordered = new ArrayList<QuantDailyBar>(values);
        ordered.sort(Comparator.comparing(QuantDailyBar::getTradeDate).thenComparing(QuantDailyBar::getInstrumentCode));
        for (QuantDailyBar value : ordered) result.computeIfAbsent(value.getTradeDate(), key -> new LinkedHashMap<String, QuantDailyBar>())
                .put(value.getInstrumentCode(), value); return result;
    }
    private double benchmarkDailyReturn(Map<String, QuantDailyBar> bars, Map<String, Double> previous, Set<String> members) {
        double sum = 0; int count = 0;
        for (QuantDailyBar bar : bars.values()) {
            if (members != null && !members.contains(bar.getInstrumentCode())) continue;
            Double before = previous.get(bar.getInstrumentCode()); if (before != null && before > 0) { sum += bar.getClose().doubleValue() / before - 1d; count++; }
        }
        return 1d + (count == 0 ? 0 : sum / count);
    }
    private Map<LocalDate, Set<String>> universe(BacktestRequest request, Set<LocalDate> tradingDates) {
        Map<LocalDate, Set<String>> result = new LinkedHashMap<LocalDate, Set<String>>();
        if (request.getUniverse() == null || request.getUniverse().isEmpty()) return result;
        List<com.finscope.domain.quant.data.QuantUniverseMember> events = new ArrayList<com.finscope.domain.quant.data.QuantUniverseMember>(request.getUniverse());
        events.sort(Comparator.comparing(com.finscope.domain.quant.data.QuantUniverseMember::getTradeDate)
                .thenComparing(com.finscope.domain.quant.data.QuantUniverseMember::getInstrumentCode));
        Set<String> active = new LinkedHashSet<String>(); int cursor = 0;
        List<LocalDate> dates = new ArrayList<LocalDate>(tradingDates); java.util.Collections.sort(dates);
        for (LocalDate date : dates) {
            while (cursor < events.size() && !events.get(cursor).getTradeDate().isAfter(date)) {
                com.finscope.domain.quant.data.QuantUniverseMember event = events.get(cursor++);
                if (event.isMember()) active.add(event.getInstrumentCode()); else active.remove(event.getInstrumentCode());
            }
            result.put(date, new LinkedHashSet<String>(active));
        }
        return result;
    }
}
