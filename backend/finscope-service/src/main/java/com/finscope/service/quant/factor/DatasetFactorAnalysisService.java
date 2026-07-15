package com.finscope.service.quant.factor;

import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.dao.factorresearch.QuantCapitalFlowRepository;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.factorresearch.FactorObservation;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.domain.quant.factor.FactorAnalysis;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.service.quant.data.QuantDatasetService;
import com.finscope.service.factorresearch.FactorCalculationContext;
import com.finscope.service.factorresearch.FactorProviderRegistry;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.*;

@Service
public class DatasetFactorAnalysisService {
    @Resource private QuantDatasetService datasets;
    @Resource private QuantMarketDataRepository marketData;
    @Resource private FactorRegistry registry;
    @Resource private FactorProviderRegistry providers;
    @Resource private QuantCapitalFlowRepository capitalFlows;
    private final FactorAnalysisService analysis = new FactorAnalysisService();

    public FactorAnalysis analyze(Long datasetId, String factorCode) {
        QuantDataset dataset = datasets.get(datasetId);
        if (!providerRegistry().contains(factorCode)) throw new BusinessException(ErrorCode.BAD_REQUEST, "未知因子：" + factorCode);
        if (!"READY".equals(dataset.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "只有通过质量门禁的数据集才能运行因子诊断");
        if (!datasets.availableFactorCodes(datasetId).contains(factorCode)) throw new BusinessException(ErrorCode.CONFLICT, "当前数据集不具备该因子的有效覆盖");
        List<QuantDailyBar> bars = marketData.findBars(datasetId); List<QuantFundamentalSnapshot> fundamentals = marketData.findFundamentals(datasetId);
        List<QuantUniverseMember> events = marketData.findUniverseMembers(datasetId);
        Map<String, QuantCapitalFlowDaily> capital = capital(datasetId);
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
                QuantCapitalFlowDaily capitalFlow = capital.get(key(date, code));
                FactorObservation observation = providerRegistry().calculate(factorCode,
                        new FactorCalculationContext(String.valueOf(datasetId), code, date,
                                dates.get(dateIndex + 1).atTime(9, 30),
                                histories.get(code), latestVisible(fundamentals, code, date), capitalFlow));
                double factor = observation.getProcessedValue() == null
                        ? Double.NaN : observation.getProcessedValue().doubleValue();
                if (Double.isFinite(factor)) { factorValues.add(factor); nextReturns.add(next.getClose().doubleValue() / next.getOpen().doubleValue() - 1d); }
            }
            if (factorValues.size() >= 2) dailyIc.add(analysis.rankIc(factorValues, nextReturns));
        }
        FactorAnalysis result = analysis.summarize(factorCode, dailyIc); result.setDatasetId(datasetId);
        result.setDatasetFingerprint(dataset.getFingerprint()); return result;
    }

    private FactorProviderRegistry providerRegistry() {
        return providers == null ? FactorProviderRegistry.legacyOnly() : providers;
    }

    private Map<String, QuantCapitalFlowDaily> capital(Long datasetId) {
        Map<String, QuantCapitalFlowDaily> result = new LinkedHashMap<String, QuantCapitalFlowDaily>();
        if (capitalFlows == null) return result;
        for (QuantCapitalFlowDaily value : capitalFlows.findByDatasetId(datasetId)) {
            result.put(key(value.getTradeDate(), value.getInstrumentCode()), value);
        }
        return result;
    }

    private String key(LocalDate date, String code) { return date + "|" + code; }

    private QuantFundamentalSnapshot latestVisible(List<QuantFundamentalSnapshot> values, String code, LocalDate date) {
        QuantFundamentalSnapshot latest = null;
        for (QuantFundamentalSnapshot value : values) if (code.equals(value.getInstrumentCode()) && !value.getDisclosedAt().isAfter(date)
                && (latest == null || value.getDisclosedAt().isAfter(latest.getDisclosedAt()))) latest = value;
        return latest;
    }
}
