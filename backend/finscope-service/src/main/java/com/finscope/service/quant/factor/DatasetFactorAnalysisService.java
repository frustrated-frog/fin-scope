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
import com.finscope.service.factorresearch.FactorEvidenceAssessmentService;
import com.finscope.service.factorresearch.ResearchFactorCatalog;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DatasetFactorAnalysisService {
    private static final int[] HORIZONS = {1, 3, 5, 10, 20};
    @Resource private QuantDatasetService datasets;
    @Resource private QuantMarketDataRepository marketData;
    @Resource private FactorRegistry registry;
    @Resource private FactorProviderRegistry providers;
    @Resource private QuantCapitalFlowRepository capitalFlows;
    @Resource private ResearchFactorCatalog researchCatalog;
    private final FactorAnalysisService analysis = new FactorAnalysisService();
    private final FactorEvidenceAssessmentService evidenceAssessment = new FactorEvidenceAssessmentService();

    public FactorAnalysis analyze(Long datasetId, String factorCode) {
        QuantDataset dataset = datasets.get(datasetId);
        if (!providerRegistry().contains(factorCode)) throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "未知因子：" + factorCode);
        if (!"READY".equals(dataset.getStatus())) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "只有通过质量门禁的数据集才能运行因子诊断");
        if (!datasets.availableFactorCodes(datasetId).contains(factorCode)) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "当前数据集不具备该因子的有效覆盖");
        List<QuantDailyBar> bars = marketData.findBars(datasetId); List<QuantFundamentalSnapshot> fundamentals = marketData.findFundamentals(datasetId);
        List<QuantUniverseMember> events = marketData.findUniverseMembers(datasetId);
        Map<String, QuantCapitalFlowDaily> capital = capital(datasetId);
        TreeMap<LocalDate, Map<String, QuantDailyBar>> byDate = new TreeMap<LocalDate, Map<String, QuantDailyBar>>();
        for (QuantDailyBar bar : bars) byDate.computeIfAbsent(bar.getTradeDate(), key -> new LinkedHashMap<String, QuantDailyBar>()).put(bar.getInstrumentCode(), bar);
        List<LocalDate> dates = new ArrayList<LocalDate>(byDate.keySet()); Map<String,List<QuantDailyBar>> histories = new LinkedHashMap<String,List<QuantDailyBar>>();
        Map<Integer, HorizonAccumulator> evidenceByHorizon = new LinkedHashMap<Integer, HorizonAccumulator>();
        for (int horizon : HORIZONS) evidenceByHorizon.put(horizon, new HorizonAccumulator());
        List<Map<String, Double>> factorSnapshots = new ArrayList<Map<String, Double>>();
        Set<String> active = new LinkedHashSet<String>(); int eventCursor = 0;
        for (int dateIndex = 0; dateIndex + 1 < dates.size(); dateIndex++) {
            LocalDate date = dates.get(dateIndex); while (eventCursor < events.size() && !events.get(eventCursor).getTradeDate().isAfter(date)) {
                QuantUniverseMember event = events.get(eventCursor++); if (event.isMember()) active.add(event.getInstrumentCode()); else active.remove(event.getInstrumentCode());
            }
            Map<String, QuantDailyBar> today = byDate.get(date);
            for (QuantDailyBar bar : today.values()) histories.computeIfAbsent(bar.getInstrumentCode(), key -> new ArrayList<QuantDailyBar>()).add(bar);
            LocalDateTime calculationCutoff = dates.get(dateIndex + 1).atTime(9, 30);
            Map<String, Double> factorByInstrument = new LinkedHashMap<String, Double>();
            for (Map.Entry<String, QuantDailyBar> entry : today.entrySet()) {
                String code = entry.getKey(); if (!events.isEmpty() && !active.contains(code)) continue;
                QuantCapitalFlowDaily capitalFlow = capital.get(key(date, code));
                FactorObservation observation = providerRegistry().calculate(factorCode,
                        new FactorCalculationContext(String.valueOf(datasetId), code, date,
                                calculationCutoff,
                                histories.get(code), latestVisible(fundamentals, code, date), capitalFlow,
                                visibleCapitalHistory(capital, code, date, calculationCutoff)));
                double factor = observation.getProcessedValue() == null
                        ? Double.NaN : observation.getProcessedValue().doubleValue();
                if (Double.isFinite(factor)) factorByInstrument.put(code, factor);
            }
            for (int horizon : HORIZONS) {
                if (dateIndex + horizon >= dates.size()) continue;
                HorizonAccumulator accumulator = evidenceByHorizon.get(horizon);
                accumulator.totalEligibleDays++;
                Map<String, QuantDailyBar> nextDay = byDate.get(dates.get(dateIndex + 1));
                Map<String, QuantDailyBar> horizonDay = byDate.get(dates.get(dateIndex + horizon));
                List<Double> factorValues = new ArrayList<Double>();
                List<Double> futureReturns = new ArrayList<Double>();
                Map<String, Double> eligibleFactors = new LinkedHashMap<String, Double>();
                for (Map.Entry<String, Double> factorEntry : factorByInstrument.entrySet()) {
                    QuantDailyBar next = nextDay.get(factorEntry.getKey());
                    QuantDailyBar future = horizonDay.get(factorEntry.getKey());
                    if (next == null || future == null || next.getOpen() == null || future.getClose() == null
                            || next.getOpen().signum() <= 0) continue;
                    factorValues.add(factorEntry.getValue());
                    futureReturns.add(future.getClose().doubleValue() / next.getOpen().doubleValue() - 1d);
                    eligibleFactors.put(factorEntry.getKey(), factorEntry.getValue());
                }
                if (factorValues.size() < com.finscope.service.factorresearch.FactorValidationPolicy.MIN_CROSS_SECTION_SIZE) continue;
                double ic = analysis.rankIc(factorValues, futureReturns);
                if (!Double.isFinite(ic)) continue;
                accumulator.dailyIc.add(ic);
                accumulator.dailySpread.add(analysis.quantileSpread(factorValues, futureReturns));
                accumulator.dailyMonotonicity.add(analysis.quantileMonotonicity(factorValues, futureReturns));
                accumulator.minCrossSection = Math.min(accumulator.minCrossSection, factorValues.size());
                if (horizon == 1) factorSnapshots.add(eligibleFactors);
            }
        }
        HorizonAccumulator primary = evidenceByHorizon.get(1);
        FactorAnalysis result = analysis.summarize(factorCode, primary.dailyIc); result.setDatasetId(datasetId);
        result.setDatasetFingerprint(dataset.getFingerprint());
        result.setTotalEligibleDays(primary.totalEligibleDays);
        result.setMinCrossSectionSize(primary.minCrossSection == Integer.MAX_VALUE ? 0 : primary.minCrossSection);
        result.setCoverageRatio(primary.totalEligibleDays == 0 ? 0d : (double) primary.dailyIc.size() / primary.totalEligibleDays);
        analysis.attachQuantileEvidence(result, primary.dailySpread, primary.dailyMonotonicity);
        List<com.finscope.domain.quant.factor.FactorHorizonAnalysis> horizons = new ArrayList<com.finscope.domain.quant.factor.FactorHorizonAnalysis>();
        for (Map.Entry<Integer, HorizonAccumulator> entry : evidenceByHorizon.entrySet()) {
            HorizonAccumulator value = entry.getValue();
            horizons.add(analysis.summarizeHorizon(entry.getKey(), value.dailyIc, value.totalEligibleDays,
                    value.minCrossSection, value.dailySpread, value.dailyMonotonicity));
        }
        result.setHorizons(horizons);
        result.setRobustness(analysis.robustness(primary.dailyIc, factorSnapshots));
        evidenceAssessment.assess(result, researchDirection(factorCode), dataset.getDataKind(), dataset.getDatasetLevel());
        return result;
    }

    private static final class HorizonAccumulator {
        private final List<Double> dailyIc = new ArrayList<Double>();
        private final List<Double> dailySpread = new ArrayList<Double>();
        private final List<Double> dailyMonotonicity = new ArrayList<Double>();
        private int totalEligibleDays;
        private int minCrossSection = Integer.MAX_VALUE;
    }

    private FactorProviderRegistry providerRegistry() {
        return providers == null ? FactorProviderRegistry.legacyOnly() : providers;
    }

    private String researchDirection(String factorCode) {
        if (researchCatalog != null) {
            com.finscope.domain.factorresearch.FactorIdentity identity = providerRegistry().identity(factorCode);
            return researchCatalog.get(identity.getNamespace(), identity.getCode(), identity.getVersion())
                    .getExpectedDirection();
        }
        return registry != null && registry.contains(factorCode) && "LOW".equals(registry.get(factorCode).getDirection())
                ? "NEGATIVE_HYPOTHESIS" : "POSITIVE_HYPOTHESIS";
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

    private List<QuantCapitalFlowDaily> visibleCapitalHistory(Map<String, QuantCapitalFlowDaily> values,
                                                               String code, LocalDate date,
                                                               java.time.LocalDateTime cutoff) {
        List<QuantCapitalFlowDaily> result = new ArrayList<QuantCapitalFlowDaily>();
        for (QuantCapitalFlowDaily value : values.values())
            if (code.equals(value.getInstrumentCode()) && !value.getTradeDate().isAfter(date)
                    && value.getAvailableAt() != null && !value.getAvailableAt().isAfter(cutoff)) result.add(value);
        result.sort(Comparator.comparing(QuantCapitalFlowDaily::getTradeDate)); return result;
    }

    private QuantFundamentalSnapshot latestVisible(List<QuantFundamentalSnapshot> values, String code, LocalDate date) {
        QuantFundamentalSnapshot latest = null;
        for (QuantFundamentalSnapshot value : values) if (code.equals(value.getInstrumentCode()) && !value.getDisclosedAt().isAfter(date)
                && (latest == null || value.getDisclosedAt().isAfter(latest.getDisclosedAt()))) latest = value;
        return latest;
    }
}
