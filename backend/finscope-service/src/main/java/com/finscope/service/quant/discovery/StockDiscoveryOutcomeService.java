package com.finscope.service.quant.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.quant.StockDiscoveryRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.discovery.StockDiscoveryAccuracyReport;
import com.finscope.domain.quant.discovery.StockDiscoveryCandidate;
import com.finscope.domain.quant.discovery.StockDiscoveryEvaluationRequest;
import com.finscope.domain.quant.discovery.StockDiscoveryModelPrediction;
import com.finscope.rpc.quant.PythonStockDiscoveryEvaluationClient;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class StockDiscoveryOutcomeService {
    private static final int FETCH_LIMIT = 120;
    private static final double ROUND_TRIP_COST_RATE = 0.0015d;
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    @Resource
    private StockDiscoveryRepository repository;
    @Resource
    private QuantDailyBarSource dailyBarSource;
    @Resource
    private PythonStockDiscoveryEvaluationClient evaluationClient;
    @Resource
    private ObjectMapper objectMapper;

    public int settlePending() {
        List<StockDiscoveryCandidate> pending = repository.findPendingCandidates(200);
        Map<String, QuantDailyBarBatch> batches = new HashMap<>();
        int settled = 0;
        for (StockDiscoveryCandidate candidate : pending) {
            try {
                QuantDailyBarBatch batch = batches.get(candidate.getInstrumentCode());
                if (batch == null) {
                    batch = dailyBarSource.fetch(candidate.getInstrumentCode(), FETCH_LIMIT);
                    batches.put(candidate.getInstrumentCode(), batch);
                }
                if (settle(candidate, batch)) {
                    settled++;
                }
            } catch (RuntimeException error) {
                log.warn("股票发现真实结果暂未结算，instrument={},runId={}",
                        candidate.getInstrumentCode(), candidate.getRunId(), error);
            }
        }
        return settled;
    }

    public StockDiscoveryAccuracyReport accuracy() {
        StockDiscoveryEvaluationRequest request = new StockDiscoveryEvaluationRequest();
        request.setAsOfDate(LocalDate.now(CHINA_ZONE).toString());
        request.setPendingCount(repository.countPendingCandidates());
        for (StockDiscoveryCandidate candidate : repository.findMaturedCandidates(5000)) {
            request.getObservations().add(outcome(candidate));
        }
        for (StockDiscoveryModelPrediction prediction : repository.findMaturedModelPredictions(20000)) {
            request.getModelObservations().add(model(prediction));
        }
        return evaluationClient.evaluate(request);
    }

    private boolean settle(StockDiscoveryCandidate candidate, QuantDailyBarBatch batch) {
        if (candidate.getAsOfDate() == null || candidate.getHorizonDays() <= 0) {
            return repository.markCandidateUnavailable(
                    candidate, LocalDateTime.now(CHINA_ZONE), "冻结日期或预测周期无效");
        }
        List<QuantDailyBar> future = new ArrayList<>();
        for (QuantDailyBar bar : batch.getBars()) {
            if (bar.getTradeDate() != null && bar.getTradeDate().isAfter(candidate.getAsOfDate())
                    && positive(bar.getOpen())) {
                future.add(bar);
            }
        }
        future.sort(Comparator.comparing(QuantDailyBar::getTradeDate));
        if (future.size() <= candidate.getHorizonDays()) {
            LocalDate latest = latestDate(batch);
            if (latest != null && latest.isAfter(candidate.getAsOfDate().plusDays(45))) {
                return repository.markCandidateUnavailable(
                        candidate, LocalDateTime.now(CHINA_ZONE), "45 个自然日后仍不足以形成完整持有期");
            }
            return false;
        }
        QuantDailyBar entry = future.get(0);
        QuantDailyBar exit = future.get(candidate.getHorizonDays());
        double entryOpen = entry.getOpen().doubleValue();
        double exitOpen = exit.getOpen().doubleValue();
        double netReturn = round(exitOpen / entryOpen - 1d - ROUND_TRIP_COST_RATE);
        String direction = netReturn > 0d ? "UP" : "DOWN";
        Boolean correct = candidate.getCalibratedProbability() == null ? null
                : (candidate.getCalibratedProbability() >= 0.5d) == "UP".equals(direction);
        return repository.settleCandidate(candidate, entry.getTradeDate(), entryOpen,
                exit.getTradeDate(), exitOpen, netReturn, direction, correct,
                LocalDateTime.now(CHINA_ZONE), batch.getSourceCode());
    }

    private StockDiscoveryEvaluationRequest.OutcomeObservation outcome(StockDiscoveryCandidate candidate) {
        StockDiscoveryEvaluationRequest.OutcomeObservation value =
                new StockDiscoveryEvaluationRequest.OutcomeObservation();
        value.setRunId(candidate.getRunId());
        value.setInstrumentCode(candidate.getInstrumentCode());
        value.setAsOfDate(candidate.getAsOfDate().toString());
        value.setHorizonDays(candidate.getHorizonDays());
        value.setAdmitted(candidate.isAdmitted());
        value.setFinalRank(candidate.getFinalRank());
        value.setCalibratedProbability(candidate.getCalibratedProbability());
        value.setActualNetReturn(candidate.getActualNetReturn());
        value.setActualDirection(candidate.getActualDirection());
        value.setSectorNames(sectorNames(candidate.getSectorNamesJson()));
        return value;
    }

    private StockDiscoveryEvaluationRequest.ModelObservation model(StockDiscoveryModelPrediction prediction) {
        StockDiscoveryEvaluationRequest.ModelObservation value =
                new StockDiscoveryEvaluationRequest.ModelObservation();
        value.setRunId(prediction.getRunId());
        value.setInstrumentCode(prediction.getInstrumentCode());
        value.setAsOfDate(prediction.getAsOfDate().toString());
        value.setHorizonDays(prediction.getHorizonDays());
        value.setModelCode(prediction.getModelCode());
        value.setModelName(prediction.getModelName());
        value.setRole(prediction.getRole());
        value.setCalibratedProbability(prediction.getCalibratedProbability());
        value.setShadowDecision(prediction.getShadowDecision());
        value.setQualificationStatus(prediction.getQualificationStatus());
        value.setActualDirection(prediction.getActualDirection());
        return value;
    }

    private List<String> sectorNames(String value) {
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (Exception error) {
            log.warn("股票发现板块快照解析失败，使用空板块集合", error);
            return List.of();
        }
    }

    private LocalDate latestDate(QuantDailyBarBatch batch) {
        LocalDate latest = batch.getAsOfDate();
        for (QuantDailyBar bar : batch.getBars()) {
            if (bar.getTradeDate() != null && (latest == null || bar.getTradeDate().isAfter(latest))) {
                latest = bar.getTradeDate();
            }
        }
        return latest;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).doubleValue();
    }
}
