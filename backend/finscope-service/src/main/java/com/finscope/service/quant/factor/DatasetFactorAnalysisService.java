package com.finscope.service.quant.factor;

import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.domain.quant.factor.FactorAnalysis;
import com.finscope.service.quant.data.QuantDatasetService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.*;

@Service
public class DatasetFactorAnalysisService {
    @Resource private QuantDatasetService datasets;
    @Resource private QuantMarketDataRepository marketData;
    @Resource private FactorRegistry registry;
    private final FactorCalculator calculator = new FactorCalculator();
    private final FactorAnalysisService analysis = new FactorAnalysisService();

    public FactorAnalysis analyze(Long datasetId, String factorCode) {
        datasets.get(datasetId); registry.get(factorCode);
        List<QuantDailyBar> bars = marketData.findBars(datasetId); List<QuantFundamentalSnapshot> fundamentals = marketData.findFundamentals(datasetId);
        List<QuantUniverseMember> events = marketData.findUniverseMembers(datasetId);
        TreeMap<LocalDate, Map<String, QuantDailyBar>> byDate = new TreeMap<LocalDate, Map<String, QuantDailyBar>>();
        for (QuantDailyBar bar : bars) byDate.computeIfAbsent(bar.getTradeDate(), key -> new LinkedHashMap<String, QuantDailyBar>()).put(bar.getInstrumentCode(), bar);
        List<LocalDate> dates = new ArrayList<LocalDate>(byDate.keySet()); Map<String,List<QuantDailyBar>> histories = new LinkedHashMap<String,List<QuantDailyBar>>();
        Set<String> active = new LinkedHashSet<String>(); int eventCursor = 0; List<Double> dailyIc = new ArrayList<Double>();
        for (int dateIndex = 0; dateIndex + 1 < dates.size(); dateIndex++) {
            LocalDate date = dates.get(dateIndex); while (eventCursor < events.size() && !events.get(eventCursor).getTradeDate().isAfter(date)) {
                QuantUniverseMember event = events.get(eventCursor++); if (event.isMember()) active.add(event.getInstrumentCode()); else active.remove(event.getInstrumentCode());
            }
            Map<String, QuantDailyBar> today = byDate.get(date); Map<String, QuantDailyBar> tomorrow = byDate.get(dates.get(dateIndex + 1));
            for (QuantDailyBar bar : today.values()) histories.computeIfAbsent(bar.getInstrumentCode(), key -> new ArrayList<QuantDailyBar>()).add(bar);
            List<Double> factorValues = new ArrayList<Double>(), nextReturns = new ArrayList<Double>();
            for (Map.Entry<String, QuantDailyBar> entry : today.entrySet()) {
                String code = entry.getKey(); if (!events.isEmpty() && !active.contains(code)) continue; QuantDailyBar next = tomorrow.get(code);
                if (next == null || next.getOpen().signum() <= 0) continue;
                double factor = calculator.value(factorCode, histories.get(code), latestVisible(fundamentals, code, date));
                if (Double.isFinite(factor)) { factorValues.add(factor); nextReturns.add(next.getClose().doubleValue() / next.getOpen().doubleValue() - 1d); }
            }
            if (factorValues.size() >= 2) dailyIc.add(analysis.rankIc(factorValues, nextReturns));
        }
        return analysis.summarize(factorCode, dailyIc);
    }

    private QuantFundamentalSnapshot latestVisible(List<QuantFundamentalSnapshot> values, String code, LocalDate date) {
        QuantFundamentalSnapshot latest = null;
        for (QuantFundamentalSnapshot value : values) if (code.equals(value.getInstrumentCode()) && !value.getDisclosedAt().isAfter(date)
                && (latest == null || value.getDisclosedAt().isAfter(latest.getDisclosedAt()))) latest = value;
        return latest;
    }
}
