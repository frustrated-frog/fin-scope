package com.finscope.service.quant.forecast;

import com.finscope.dao.quant.ForecastCandidateRunRepository;
import com.finscope.domain.quant.forecast.ForecastCandidateRun;
import com.finscope.domain.quant.forecast.ForecastModelRace;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ForecastModelRaceService {
    private static final int WINDOW_SIZE = 20;
    private static final int MINIMUM_PROMOTION_SAMPLES = 12;
    private static final double MINIMUM_BRIER_IMPROVEMENT = .01d;
    private static final double MINIMUM_COVERAGE = .40d;
    private final ForecastCandidateRunRepository candidates;

    public ForecastModelRaceService(ForecastCandidateRunRepository candidates) {
        this.candidates = candidates;
    }

    public ForecastModelRace evaluate(String instrumentCode, int horizonDays) {
        List<ForecastCandidateRun> evidence = candidates.findMaturedEvidence(
                instrumentCode, horizonDays, WINDOW_SIZE);
        ForecastModelRace result = new ForecastModelRace();
        result.setInstrumentCode(instrumentCode);
        result.setHorizonDays(horizonDays);
        result.setMinimumPromotionSamples(MINIMUM_PROMOTION_SAMPLES);
        if (evidence.isEmpty()) {
            result.setStatus("EVIDENCE_ACCUMULATING");
            result.setConclusion("尚无 v6 候选模型到期结果；需要真实未来行情后才能比较。");
            return result;
        }
        Map<String, List<ForecastCandidateRun>> grouped = new LinkedHashMap<String, List<ForecastCandidateRun>>();
        for (ForecastCandidateRun value : evidence) {
            grouped.computeIfAbsent(value.getModelCode(), ignored -> new ArrayList<ForecastCandidateRun>())
                    .add(value);
            if ("CHAMPION".equals(value.getRole())) {
                result.setChampionCode(value.getModelCode());
            }
        }
        if (result.getChampionCode() == null) {
            result.setStatus("EVIDENCE_INCOMPLETE");
            result.setConclusion("真实赛马缺少冠军成对证据，暂不比较。");
            return result;
        }
        List<ForecastCandidateRun> championEvidence = grouped.get(result.getChampionCode());
        ForecastModelRace.CandidateMetric champion = metric(championEvidence);
        result.setSampleCount(champion.getSampleCount());
        result.setFirstAsOfDate(evidence.stream().map(ForecastCandidateRun::getAsOfDate)
                .min(Comparator.naturalOrder()).orElse(null));
        result.setLastAsOfDate(evidence.stream().map(ForecastCandidateRun::getAsOfDate)
                .max(Comparator.naturalOrder()).orElse(null));
        List<ForecastModelRace.CandidateMetric> metrics = new ArrayList<ForecastModelRace.CandidateMetric>();
        for (List<ForecastCandidateRun> values : grouped.values()) {
            ForecastModelRace.CandidateMetric value = metric(values);
            value.setRole(role(value.getModelCode(), result.getChampionCode(), values));
            value.setBrierDeltaVsChampion(value.getBrierScore() - champion.getBrierScore());
            value.setLogLossDeltaVsChampion(value.getLogLoss() - champion.getLogLoss());
            value.setPromotionEligible(eligible(value, champion));
            metrics.add(value);
        }
        metrics.sort(Comparator.comparing(ForecastModelRace.CandidateMetric::getBrierScore)
                .thenComparing(ForecastModelRace.CandidateMetric::getModelCode));
        result.setCandidates(metrics);
        ForecastModelRace.CandidateMetric eligible = metrics.stream()
                .filter(ForecastModelRace.CandidateMetric::isPromotionEligible)
                .findFirst().orElse(null);
        classify(result, champion, metrics, eligible);
        return result;
    }

    private String role(String modelCode, String championCode,
                        List<ForecastCandidateRun> values) {
        if (modelCode.equals(championCode)) {
            return "CHAMPION";
        }
        ForecastCandidateRun latest = values.stream()
                .max(Comparator.comparing(ForecastCandidateRun::getAsOfDate)
                        .thenComparing(ForecastCandidateRun::getForecastRunId))
                .orElse(values.get(0));
        return "BASELINE".equals(latest.getRole()) ? "BASELINE" : "CHALLENGER";
    }

    private ForecastModelRace.CandidateMetric metric(List<ForecastCandidateRun> values) {
        ForecastCandidateRun first = values.get(0);
        int count = values.size();
        double brier = 0d;
        double logLoss = 0d;
        int covered = 0;
        int correct = 0;
        for (ForecastCandidateRun value : values) {
            double label = "UP".equals(value.getActualDirection()) ? 1d : 0d;
            double probability = bounded(value.getCalibratedProbability());
            brier += square(probability - label);
            logLoss += -(label * Math.log(probability) + (1d - label) * Math.log(1d - probability));
            if ("UP".equals(value.getShadowDecision()) || "DOWN".equals(value.getShadowDecision())) {
                covered++;
                if (Boolean.TRUE.equals(value.getPredictionCorrect())) {
                    correct++;
                }
            }
        }
        ForecastModelRace.CandidateMetric result = new ForecastModelRace.CandidateMetric();
        result.setModelCode(first.getModelCode());
        result.setModelName(first.getModelName());
        result.setRole(first.getRole());
        result.setSampleCount(count);
        result.setBrierScore(brier / count);
        result.setLogLoss(logLoss / count);
        result.setBrierSkillScore(1d - result.getBrierScore() / .25d);
        result.setCoveredCount(covered);
        result.setCoverage((double) covered / count);
        result.setCoveredAccuracy(covered == 0 ? null : (double) correct / covered);
        return result;
    }

    private boolean eligible(ForecastModelRace.CandidateMetric value,
                             ForecastModelRace.CandidateMetric champion) {
        if ("CHAMPION".equals(value.getRole()) || "BASELINE".equals(value.getRole())
                || value.getSampleCount() < MINIMUM_PROMOTION_SAMPLES) {
            return false;
        }
        double valueAccuracy = value.getCoveredAccuracy() == null ? 0d : value.getCoveredAccuracy();
        double championAccuracy = champion.getCoveredAccuracy() == null
                ? 0d : champion.getCoveredAccuracy();
        return value.getBrierDeltaVsChampion() <= -MINIMUM_BRIER_IMPROVEMENT
                && value.getLogLossDeltaVsChampion() <= 0d
                && value.getCoverage() >= MINIMUM_COVERAGE
                && valueAccuracy >= championAccuracy;
    }

    private void classify(ForecastModelRace result,
                          ForecastModelRace.CandidateMetric champion,
                          List<ForecastModelRace.CandidateMetric> metrics,
                          ForecastModelRace.CandidateMetric eligible) {
        if (result.getSampleCount() < MINIMUM_PROMOTION_SAMPLES) {
            result.setStatus("EVIDENCE_ACCUMULATING");
            result.setConclusion("真实成对样本尚未达到 12 次；继续影子运行，不作模型晋升判断。");
            return;
        }
        if (eligible != null) {
            result.setStatus("PROMOTION_REVIEW");
            result.setPromotionCandidateCode(eligible.getModelCode());
            result.setConclusion("挑战者在真实成对概率质量、方向覆盖和命中上达到人工晋升审查门槛；不会自动换模。");
            return;
        }
        ForecastModelRace.CandidateMetric best = metrics.get(0);
        if (champion.getModelCode().equals(best.getModelCode())) {
            result.setStatus("CHAMPION_LEADS");
            result.setConclusion("当前冠军在真实到期概率质量上保持领先，继续使用并观察衰减。");
            return;
        }
        result.setStatus("NO_STABLE_EDGE");
        result.setConclusion("存在局部指标改善，但尚未同时跨过概率、覆盖和命中门槛，不支持换模。");
    }

    private double bounded(Double value) {
        return Math.max(.000001d, Math.min(.999999d, value == null ? .5d : value));
    }

    private double square(double value) {
        return value * value;
    }
}
