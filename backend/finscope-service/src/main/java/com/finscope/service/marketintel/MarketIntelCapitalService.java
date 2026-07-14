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
import java.util.stream.Collectors;

@Service
public class MarketIntelCapitalService {
    private final InstrumentRepository instruments;private final CapitalBehaviorSnapshotRepository snapshots;private final CapitalFlowAggregationService aggregation;private final CapitalRuleExplanationService rules;
    public MarketIntelCapitalService(InstrumentRepository instruments,CapitalBehaviorSnapshotRepository snapshots,CapitalFlowAggregationService aggregation,CapitalRuleExplanationService rules){this.instruments=instruments;this.snapshots=snapshots;this.aggregation=aggregation;this.rules=rules;}
    public List<Instrument> listStockInstruments(){return instruments.findAll().stream().filter(v->"STOCK".equals(v.getType())).collect(Collectors.toList());}
    public MarketIntelCapitalView view(Long instrumentId,String range,String granularity){Instrument instrument=stock(instrumentId);CapitalBehaviorSnapshot snapshot=snapshots.findLatest(instrumentId).orElseThrow(()->new IllegalArgumentException("capital snapshot not found for instrument "+instrumentId));
        List<CapitalFlowPoint> minute=snapshot.getFacts().stream().filter(v->v.getGranularity()!=null&&v.getGranularity().startsWith("MINUTE_")).collect(Collectors.toList());
        List<CapitalFlowPoint> timeline="5m".equalsIgnoreCase(granularity)?aggregation.aggregate(minute,5):minute;
        List<CapitalFlowPoint> daily=snapshot.getFacts().stream().filter(v->"DAY_1".equals(v.getGranularity())).collect(Collectors.toList());
        MarketIntelCapitalView value=new MarketIntelCapitalView();value.setInstrument(instrument);value.setSnapshot(snapshot);value.setIntradayTimeline(timeline);value.setDailyTrend(daily);value.setRuleExplanation(rules.explain(snapshot.getFacts(),snapshot.getSignals()));
        MarketIntelCapitalView.Health health=new MarketIntelCapitalView.Health();health.setAsOf(snapshot.getAsOf());health.setStatus(snapshot.getAsOf().isBefore(LocalDateTime.now().minusHours(36))?"STALE":"FRESH");
        health.setProviderCode(snapshot.getFacts().isEmpty()?"":snapshot.getFacts().get(0).getProviderCode());health.setWarnings(snapshot.getFacts().stream().anyMatch(v->!"COMPLETE".equals(v.getQualityStatus()))?Collections.singletonList("部分时间点数据不完整"):Collections.emptyList());value.setHealth(health);return value;}
    public Instrument stock(Long id){Instrument value=instruments.findById(id).orElseThrow(()->new IllegalArgumentException("instrument not found: "+id));if(!"STOCK".equals(value.getType()))throw new IllegalArgumentException("Market Intel currently supports STOCK instruments only");return value;}
}
