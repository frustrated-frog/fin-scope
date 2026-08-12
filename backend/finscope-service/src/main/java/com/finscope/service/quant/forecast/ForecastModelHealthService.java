package com.finscope.service.quant.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.domain.quant.forecast.ForecastModelHealth;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class ForecastModelHealthService {
    private static final int WINDOW = 20;
    private static final int MINIMUM_SAMPLE_COUNT = 8;
    private final SingleStockForecastRunRepository runs;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    public ForecastModelHealthService(SingleStockForecastRunRepository runs) {
        this.runs = runs;
    }

    public ForecastModelHealth evaluate(String instrumentCode, int horizonDays, String modelVersion) {
        List<SingleStockForecastRun> evidence = new ArrayList<SingleStockForecastRun>(
                runs.findHealthEvidence(instrumentCode, horizonDays, modelVersion, WINDOW));
        evidence.sort(Comparator.comparing(SingleStockForecastRun::getAsOfDate)
                .thenComparing(SingleStockForecastRun::getId).reversed());
        if (evidence.size() > WINDOW) {
            evidence = new ArrayList<SingleStockForecastRun>(evidence.subList(0, WINDOW));
        }
        Collections.reverse(evidence);
        ForecastModelHealth value = new ForecastModelHealth();
        value.setInstrumentCode(instrumentCode);
        value.setHorizonDays(horizonDays);
        value.setModelVersion(modelVersion);
        value.setSampleCount(evidence.size());
        if (evidence.isEmpty()) {
            insufficient(value);
            return value;
        }
        value.setFirstAsOfDate(evidence.get(0).getAsOfDate());
        value.setLastAsOfDate(evidence.get(evidence.size() - 1).getAsOfDate());
        double brier = 0d;
        double baselineBrier = 0d;
        double logLoss = 0d;
        int actualUp = 0;
        int covered = 0;
        int correct = 0;
        for (SingleStockForecastRun run : evidence) {
            boolean up = "UP".equals(run.getOutcome().getActualDirection());
            double label = up ? 1d : 0d;
            double probability = bounded(run.getUpProbability() == null ? .5d : run.getUpProbability());
            brier += square(probability - label);
            baselineBrier += square(.5d - label);
            logLoss += -(label * Math.log(probability) + (1d - label) * Math.log(1d - probability));
            if (up) {
                actualUp++;
            }
            String decision = report(run).getDecision();
            if ("UP".equals(decision) || "DOWN".equals(decision)) {
                covered++;
                if (decision.equals(run.getOutcome().getActualDirection())) {
                    correct++;
                }
            }
        }
        int count = evidence.size();
        value.setCoveredCount(covered);
        value.setAbstainedCount(count - covered);
        value.setCoverage((double) covered / count);
        value.setCoveredAccuracy(covered == 0 ? 0d : (double) correct / covered);
        value.setBrierScore(brier / count);
        value.setBaselineBrierScore(baselineBrier / count);
        value.setLogLoss(logLoss / count);
        value.setObservedUpRate((double) actualUp / count);
        classify(value);
        return value;
    }

    private void classify(ForecastModelHealth value) {
        if (value.getSampleCount() < MINIMUM_SAMPLE_COUNT) {
            insufficient(value);
            return;
        }
        boolean probabilityDecay = value.getBrierScore() > value.getBaselineBrierScore() + 0.02d;
        boolean directionDecay = value.getCoveredCount() >= 5 && value.getCoveredAccuracy() < 0.45d;
        if (probabilityDecay || directionDecay) {
            value.setStatus("PAUSED");
            value.setDirectionOutputPaused(true);
            value.setConclusion("真实到期结果显示概率质量或方向命中持续弱于门槛，新预测将保留研究证据但暂停方向输出。");
            return;
        }
        if (value.getBrierScore() <= value.getBaselineBrierScore()
                && (value.getCoveredCount() < 5 || value.getCoveredAccuracy() >= 0.5d)) {
            value.setStatus("HEALTHY");
            value.setConclusion("最近真实到期样本未显示概率质量衰减，方向门禁保持开放。");
        } else {
            value.setStatus("WATCH");
            value.setConclusion("真实样本接近门槛，继续观察，不把当前结果解释为稳定优势。");
        }
    }

    private void insufficient(ForecastModelHealth value) {
        value.setStatus("INSUFFICIENT_EVIDENCE");
        value.setDirectionOutputPaused(false);
        value.setConclusion("真实到期样本少于 8 个，暂不据此暂停模型，也不宣称线上准确率。");
    }

    private SingleStockForecast report(SingleStockForecastRun run) {
        if (run.getReport() != null) {
            return run.getReport();
        }
        try {
            return json.readValue(run.getReportJson(), SingleStockForecast.class);
        } catch (Exception error) {
            throw new IllegalStateException("无法读取模型健康度预测报告", error);
        }
    }

    private double bounded(double value) {
        return Math.min(.999999d, Math.max(.000001d, value));
    }

    private double square(double value) {
        return value * value;
    }
}
