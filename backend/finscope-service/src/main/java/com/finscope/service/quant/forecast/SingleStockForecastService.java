package com.finscope.service.quant.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.dao.quant.ForecastCandidateRunRepository;
import com.finscope.service.strategy.holding.StockTransactionService;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import com.finscope.domain.quant.forecast.ForecastCandidateRun;
import com.finscope.domain.quant.forecast.ForecastModelHealth;
import com.finscope.domain.quant.forecast.ForecastModelRace;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.rpc.quant.PythonSingleStockForecastClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Python owns analytics; this service freezes each report with personal context. */
@Service
public class SingleStockForecastService {
    private static final Logger log = LoggerFactory.getLogger(SingleStockForecastService.class);
    @Autowired
    private PythonSingleStockForecastClient client;
    @Autowired
    private SingleStockForecastRunRepository runs;
    @Autowired
    private ForecastCandidateRunRepository candidates;
    @Autowired
    private ForecastRunPersistenceService persistence;
    @Autowired
    private StockTransactionService transactions;
    @Autowired
    private ForecastOutcomeSettlementService settlement;
    @Autowired
    private ForecastModelHealthService health;
    @Autowired
    private ForecastModelRaceService race;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public SingleStockForecastRun forecast(String requestedCode, int horizonDays) {
        String code = normalizeCode(requestedCode);
        validateHorizon(horizonDays);
        settle(code + "." + market(code));
        SingleStockForecast report = client.forecast(code, horizonDays);
        ForecastModelHealth modelHealth = modelHealth(report.getInstrumentCode(), horizonDays,
                report.getModelVersion());
        applyHealthGate(report, modelHealth);
        SingleStockForecastRun.HoldingSnapshot holding = holdingSnapshot(report);
        SingleStockForecastRun value = new SingleStockForecastRun();
        value.setInstrumentCode(report.getInstrumentCode());
        value.setAsOfDate(report.getAsOfDate());
        value.setHorizonDays(report.getHorizonDays());
        value.setStatus(report.getStatus());
        value.setUpProbability(report.getUpProbability());
        value.setDataFingerprint(report.getDataFingerprint());
        value.setModelVersion(report.getModelVersion());
        value.setReportSchemaVersion(report.getReportSchemaVersion());
        value.setMaturityStatus("INSUFFICIENT_DATA".equals(report.getStatus())
                ? SingleStockForecastRun.MaturityStatus.UNAVAILABLE
                : SingleStockForecastRun.MaturityStatus.PENDING);
        try {
            value.setReportJson(json.writeValueAsString(report));
            value.setHoldingSnapshotJson(json.writeValueAsString(holding));
        } catch (Exception error) {
            throw new IllegalStateException("无法序列化单股预测研究记录", error);
        }
        List<ForecastCandidateRun> candidateRuns = candidateRuns(report);
        SingleStockForecastRun saved = persistence == null
                ? runs.save(value) : persistence.save(value, candidateRuns);
        if (persistence == null && candidates != null) {
            candidates.saveAll(saved.getId(), candidateRuns);
        }
        saved.setReport(report);
        saved.setHoldingSnapshot(holding);
        saved.setModelHealth(modelHealth);
        saved.setModelRace(modelRace(report.getInstrumentCode(), horizonDays));
        return saved;
    }

    private List<ForecastCandidateRun> candidateRuns(SingleStockForecast report) {
        List<ForecastCandidateRun> values = new java.util.ArrayList<ForecastCandidateRun>();
        if (report.getModelCompetition() == null
                || report.getModelCompetition().getCandidates() == null) {
            return values;
        }
        for (SingleStockForecast.ModelCandidate candidate
                : report.getModelCompetition().getCandidates()) {
            if (candidate.getLockedMetrics() == null) {
                continue;
            }
            ForecastCandidateRun value = new ForecastCandidateRun();
            value.setModelCode(candidate.getCode());
            value.setModelName(candidate.getName());
            value.setModelVersion(candidate.getModelVersion());
            value.setRole(candidate.getRole());
            value.setRawProbability(candidate.getRawProbability());
            value.setCalibratedProbability(candidate.getCalibratedProbability());
            value.setShadowDecision(candidate.getShadowDecision());
            value.setQualificationStatus(candidate.getQualificationStatus());
            value.setLockedSampleCount(candidate.getLockedMetrics().getSampleCount());
            value.setLockedAccuracy(candidate.getLockedMetrics().getAccuracy());
            value.setLockedBrierScore(candidate.getLockedMetrics().getBrierScore());
            value.setLockedLogLoss(candidate.getLockedMetrics().getLogLoss());
            value.setLockedBrierSkillScore(candidate.getLockedMetrics().getBrierSkillScore());
            value.setMaturityStatus("INSUFFICIENT_DATA".equals(report.getStatus())
                    ? "UNAVAILABLE" : "PENDING");
            values.add(value);
        }
        return values;
    }

    public List<SingleStockForecastRun> history(String requestedCode, int limit, Integer horizonDays) {
        String instrumentCode = null;
        if (requestedCode != null && !requestedCode.trim().isEmpty()) {
            String code = normalizeCode(requestedCode);
            instrumentCode = code + "." + market(code);
        }
        if (horizonDays != null) {
            validateHorizon(horizonDays);
        }
        if (instrumentCode != null) {
            settle(instrumentCode);
        }
        return runs.findAll(instrumentCode, limit, horizonDays);
    }

    public SingleStockForecastRun detail(Long id) {
        SingleStockForecastRun existing = runs.findById(id)
                .orElseThrow(() -> new BusinessException(BizErrorCode.SINGLE_STOCK_FORECAST_NOT_FOUND));
        settle(existing.getInstrumentCode());
        SingleStockForecastRun value = runs.findById(id).orElse(existing);
        try {
            value.setReport(json.readValue(value.getReportJson(), SingleStockForecast.class));
            if (value.getHoldingSnapshotJson() != null) {
                value.setHoldingSnapshot(json.readValue(
                        value.getHoldingSnapshotJson(), SingleStockForecastRun.HoldingSnapshot.class));
            }
            value.setModelHealth(modelHealth(value.getInstrumentCode(), value.getHorizonDays(),
                    value.getModelVersion()));
            value.setModelRace(modelRace(value.getInstrumentCode(), value.getHorizonDays()));
            return value;
        } catch (Exception error) {
            throw new IllegalStateException("无法读取单股预测研究记录", error);
        }
    }

    public ForecastModelHealth health(String requestedCode, int horizonDays, String modelVersion) {
        String code = normalizeCode(requestedCode);
        validateHorizon(horizonDays);
        if (modelVersion == null || modelVersion.trim().isEmpty()) {
            throw new IllegalArgumentException("模型版本不能为空");
        }
        settle(code + "." + market(code));
        return modelHealth(code + "." + market(code), horizonDays, modelVersion.trim());
    }

    public ForecastModelRace race(String requestedCode, int horizonDays) {
        String code = normalizeCode(requestedCode);
        validateHorizon(horizonDays);
        String instrumentCode = code + "." + market(code);
        settle(instrumentCode);
        return modelRace(instrumentCode, horizonDays);
    }

    private ForecastModelRace modelRace(String instrumentCode, int horizonDays) {
        return race == null ? null : race.evaluate(instrumentCode, horizonDays);
    }

    private ForecastModelHealth modelHealth(String instrumentCode, int horizonDays, String modelVersion) {
        return health == null ? null : health.evaluate(instrumentCode, horizonDays, modelVersion);
    }

    private void applyHealthGate(SingleStockForecast report, ForecastModelHealth modelHealth) {
        if (modelHealth == null || !modelHealth.isDirectionOutputPaused()) {
            return;
        }
        report.setModelDecision(report.getDecision());
        report.setDecision("ABSTAIN");
        report.setDecisionReason(modelHealth.getConclusion());
        report.getWarnings().add("真实到期验证门禁已暂停方向输出；概率与完整研究证据仅供复核");
    }

    private void settle(String instrumentCode) {
        if (settlement != null) {
            try {
                settlement.settlePending(instrumentCode);
            } catch (RuntimeException error) {
                log.warn("预测到期结算暂缓 instrumentCode={} error={}",
                        instrumentCode, error.getMessage());
            }
        }
    }

    private SingleStockForecastRun.HoldingSnapshot holdingSnapshot(
            SingleStockForecast report) {
        Optional<StockPosition> existing = transactions.account().getPositions().stream()
                .filter(position -> report.getInstrumentCode().equals(position.getInstrumentCode()))
                .filter(position -> position.getQuantity().signum() > 0)
                .findFirst();
        SingleStockForecastRun.HoldingSnapshot snapshot = new SingleStockForecastRun.HoldingSnapshot();
        snapshot.setHeld(existing.isPresent());
        snapshot.setInstrumentCode(report.getInstrumentCode());
        snapshot.setLastClose(report.getLastClose());
        if (!existing.isPresent()) {
            snapshot.setInterpretation("生成报告时，真实持仓账本中没有这只股票。");
            return snapshot;
        }
        StockPosition position = existing.get();
        snapshot.setInstrumentName(position.getInstrumentName());
        snapshot.setQuantity(position.getQuantity().doubleValue());
        snapshot.setAverageCost(position.getAverageCost().doubleValue());
        if (report.getLastClose() != null) {
            snapshot.setEstimatedMarketValue(snapshot.getQuantity() * report.getLastClose());
            if (snapshot.getAverageCost() > 0) {
                snapshot.setUnrealizedReturn(report.getLastClose() / snapshot.getAverageCost() - 1d);
            }
        }
        snapshot.setInterpretation(interpretation(report.getStatus()));
        return snapshot;
    }

    private String interpretation(String status) {
        if ("ROBUST".equals(status)) {
            return "历史证据相对稳健，但真实持仓仍应重点观察回撤边界和因子是否持续。";
        }
        if ("CONDITIONAL".equals(status)) {
            return "优势具有条件性；请核对当前所处趋势阶段及相邻参数是否仍保持同向。";
        }
        if ("INSUFFICIENT_DATA".equals(status)) {
            return "数据不足，当前持仓事实不能用这次模型结果加以支持或否定。";
        }
        return "没有发现稳定优势；当前持仓应依据原有投资逻辑管理，不能由本次概率单独强化。";
    }

    private String normalizeCode(String requestedCode) {
        String normalized = requestedCode == null ? "" : requestedCode.trim().toUpperCase();
        if (normalized.matches("\\d{6}\\.(SH|SZ|BJ)")) normalized = normalized.substring(0, 6);
        if (!normalized.matches("\\d{6}")) throw new IllegalArgumentException("股票代码必须是六位 A 股代码");
        return normalized;
    }

    private void validateHorizon(int horizonDays) {
        if (horizonDays != 1 && horizonDays != 5 && horizonDays != 20) {
            throw new IllegalArgumentException("预测周期只支持 1、5、20 个交易日");
        }
    }

    private String market(String code) {
        if (code.startsWith("6") || code.startsWith("5") || code.startsWith("9")) return "SH";
        if (code.startsWith("4") || code.startsWith("8")) return "BJ";
        return "SZ";
    }
}
