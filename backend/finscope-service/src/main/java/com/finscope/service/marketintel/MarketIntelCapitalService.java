package com.finscope.service.marketintel;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.marketintel.CapitalBehaviorEvaluationRepository;
import com.finscope.dao.marketintel.CapitalBehaviorSnapshotRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalFactorResult;
import com.finscope.service.marketintel.factor.CapitalFactorEngine;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MarketIntelCapitalService {

    private final InstrumentRepository instruments;
    private final CapitalBehaviorSnapshotRepository snapshots;
    private final CapitalBehaviorEvaluationRepository evaluations;
    private final CapitalFlowAggregationService aggregation;
    private final CapitalRuleExplanationService rules;
    private final CapitalBehaviorMetricsService metrics;
    private final CapitalFactorEngine factors;
    private final CapitalBehaviorSignalService signals;
    private final CapitalSnapshotFreshnessPolicy freshness;

    public MarketIntelCapitalService(InstrumentRepository instruments,
                                     CapitalBehaviorSnapshotRepository snapshots,
                                     CapitalBehaviorEvaluationRepository evaluations,
                                     CapitalFlowAggregationService aggregation,
                                     CapitalRuleExplanationService rules,
                                     CapitalBehaviorMetricsService metrics,
                                     CapitalFactorEngine factors,
                                     CapitalBehaviorSignalService signals,
                                     CapitalSnapshotFreshnessPolicy freshness) {
        this.instruments = instruments;
        this.snapshots = snapshots;
        this.evaluations = evaluations;
        this.aggregation = aggregation;
        this.rules = rules;
        this.metrics = metrics;
        this.factors = factors;
        this.signals = signals;
        this.freshness = freshness;
    }

    public List<Instrument> listStockInstruments() {
        return instruments.findAll().stream()
                .filter(value -> "STOCK".equals(value.getType()))
                .collect(Collectors.toList());
    }

    public MarketIntelCapitalView view(Long instrumentId, String range, String granularity) {
        Instrument instrument = stock(instrumentId);
        Optional<CapitalBehaviorSnapshot> latest = snapshots.findLatest(instrumentId);
        if (!latest.isPresent()) {
            return emptyView(instrument);
        }

        CapitalBehaviorSnapshot snapshot = latest.get();
        List<CapitalFlowPoint> minutePoints = snapshot.getFacts().stream()
                .filter(value -> value.getGranularity() != null && value.getGranularity().startsWith("MINUTE_"))
                .collect(Collectors.toList());
        List<CapitalFlowPoint> timeline = "5m".equalsIgnoreCase(granularity)
                ? aggregation.aggregate(minutePoints, 5)
                : minutePoints;
        List<CapitalFlowPoint> dailyTrend = snapshot.getFacts().stream()
                .filter(value -> "DAY_1".equals(value.getGranularity()))
                .sorted(Comparator.comparing(CapitalFlowPoint::getObservedAt))
                .collect(Collectors.toList());
        int dayWindow = tradingDayWindow(range);
        if (dailyTrend.size() > dayWindow) {
            dailyTrend = new ArrayList<CapitalFlowPoint>(dailyTrend.subList(dailyTrend.size() - dayWindow, dailyTrend.size()));
        }

        MarketIntelCapitalView view = new MarketIntelCapitalView();
        CapitalFactorResult factorResult = factors.calculate(snapshot.getFacts());
        view.setInstrument(instrument);
        view.setSnapshot(snapshot);
        view.setIntradayTimeline(timeline);
        view.setDailyTrend(dailyTrend);
        view.setMetrics(metrics.derive(timeline, dailyTrend, snapshot.getSignals()));
        view.setRuleExplanation(rules.explain(snapshot.getFacts(), snapshot.getSignals()));
        view.setHistoricalEvaluation(evaluations.findBySnapshotId(snapshot.getId()).orElse(null));
        view.setFactorObservations(factorResult.getObservations());
        view.setWatchConditions(signals.watchConditions(factorResult));
        view.setFactorVersion(factorResult.getFactorVersion());
        view.setSignalVersion(CapitalBehaviorSignalService.VERSION);
        view.setHealth(health(snapshot));
        return view;
    }

    public Instrument stock(Long id) {
        Instrument value = instruments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("标的不存在：" + id));
        if (!"STOCK".equals(value.getType())) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "市场情报当前仅支持股票标的");
        }
        return value;
    }

    private MarketIntelCapitalView emptyView(Instrument instrument) {
        MarketIntelCapitalView view = new MarketIntelCapitalView();
        view.setInstrument(instrument);

        MarketIntelCapitalView.Health health = new MarketIntelCapitalView.Health();
        health.setStatus("UNAVAILABLE");
        health.setProviderCode("");
        health.setWarnings(Collections.singletonList("尚未生成资金快照"));
        view.setHealth(health);
        return view;
    }

    private MarketIntelCapitalView.Health health(CapitalBehaviorSnapshot snapshot) {
        MarketIntelCapitalView.Health health = new MarketIntelCapitalView.Health();
        health.setAsOf(snapshot.getAsOf());
        health.setProviderCode(snapshot.getFacts().isEmpty() ? "" : snapshot.getFacts().get(0).getProviderCode());
        List<String> warnings = new ArrayList<String>(MarketIntelWarnings.normalize(snapshot.getWarnings()));
        if (snapshot.getFacts().stream().anyMatch(value -> !"COMPLETE".equals(value.getQualityStatus()))) {
            if (!warnings.contains("部分时间点行情未与资金流对齐")) warnings.add("部分时间点行情未与资金流对齐");
        }
        CapitalFlowPoint latestDaily = snapshot.getFacts().stream()
                .filter(value -> "DAY_1".equals(value.getGranularity()))
                .max(Comparator.comparing(CapitalFlowPoint::getObservedAt)).orElse(null);
        CapitalFlowPoint latestMinute = snapshot.getFacts().stream()
                .filter(value -> value.getGranularity() != null && value.getGranularity().startsWith("MINUTE_"))
                .max(Comparator.comparing(CapitalFlowPoint::getObservedAt)).orElse(null);
        CapitalFlowPoint currentContext = latestMinute != null && (latestDaily == null
                || latestMinute.getDataDate().isAfter(latestDaily.getDataDate())) ? latestMinute : latestDaily;
        if (currentContext == null || currentContext.getIntervalTradeAmount() == null
                || currentContext.getTradeVolume() == null || currentContext.getTurnoverRate() == null
                || currentContext.getVolumeRatio() == null) {
            warnings.add("成交额、成交量、换手率或量比尚未补齐");
        }
        LocalDateTime factAsOf = currentContext == null ? snapshot.getAsOf() : currentContext.getObservedAt();
        boolean stale = !freshness.isFresh(factAsOf);
        if (stale && !warnings.contains("资金数据已落后于当前交易时点，请刷新成功后再判断")) {
            warnings.add("资金数据已落后于当前交易时点，请刷新成功后再判断");
        }
        health.setStatus(stale ? "STALE_FALLBACK"
                : warnings.isEmpty() ? "FRESH_PRIMARY" : "PARTIAL_FRESH");
        health.setWarnings(warnings);
        return health;
    }

    private int tradingDayWindow(String range) {
        if (range == null || !range.matches("\\d+d")) return 20;
        try {
            int requested = Integer.parseInt(range.substring(0, range.length() - 1));
            return Math.max(1, Math.min(requested, 120));
        } catch (NumberFormatException ignored) {
            return 20;
        }
    }
}
