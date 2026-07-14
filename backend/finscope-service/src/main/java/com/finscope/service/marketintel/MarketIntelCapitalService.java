package com.finscope.service.marketintel;

import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.marketintel.CapitalBehaviorSnapshotRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MarketIntelCapitalService {
    private final InstrumentRepository instruments;
    private final CapitalBehaviorSnapshotRepository snapshots;
    private final CapitalFlowAggregationService aggregation;
    private final CapitalRuleExplanationService rules;
    private final CapitalBehaviorMetricsService metrics;

    public MarketIntelCapitalService(InstrumentRepository instruments,
                                     CapitalBehaviorSnapshotRepository snapshots,
                                     CapitalFlowAggregationService aggregation,
                                     CapitalRuleExplanationService rules,
                                     CapitalBehaviorMetricsService metrics) {
        this.instruments = instruments;
        this.snapshots = snapshots;
        this.aggregation = aggregation;
        this.rules = rules;
        this.metrics = metrics;
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
                .collect(Collectors.toList());

        MarketIntelCapitalView view = new MarketIntelCapitalView();
        view.setInstrument(instrument);
        view.setSnapshot(snapshot);
        view.setIntradayTimeline(timeline);
        view.setDailyTrend(dailyTrend);
        view.setMetrics(metrics.derive(timeline, dailyTrend, snapshot.getSignals()));
        view.setRuleExplanation(rules.explain(snapshot.getFacts(), snapshot.getSignals()));
        view.setHealth(health(snapshot));
        return view;
    }

    public Instrument stock(Long id) {
        Instrument value = instruments.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("instrument not found: " + id));
        if (!"STOCK".equals(value.getType())) {
            throw new IllegalArgumentException("Market Intel currently supports STOCK instruments only");
        }
        return value;
    }

    private MarketIntelCapitalView emptyView(Instrument instrument) {
        MarketIntelCapitalView view = new MarketIntelCapitalView();
        view.setInstrument(instrument);

        MarketIntelCapitalView.Health health = new MarketIntelCapitalView.Health();
        health.setStatus("EMPTY");
        health.setProviderCode("");
        health.setWarnings(Collections.singletonList("尚未生成资金快照"));
        view.setHealth(health);
        return view;
    }

    private MarketIntelCapitalView.Health health(CapitalBehaviorSnapshot snapshot) {
        MarketIntelCapitalView.Health health = new MarketIntelCapitalView.Health();
        health.setAsOf(snapshot.getAsOf());
        health.setStatus(snapshot.getAsOf().isBefore(LocalDateTime.now().minusHours(36)) ? "STALE" : "FRESH");
        health.setProviderCode(snapshot.getFacts().isEmpty() ? "" : snapshot.getFacts().get(0).getProviderCode());
        health.setWarnings(snapshot.getFacts().stream().anyMatch(value -> !"COMPLETE".equals(value.getQualityStatus()))
                ? Collections.singletonList("部分时间点数据不完整")
                : Collections.emptyList());
        return health;
    }
}
