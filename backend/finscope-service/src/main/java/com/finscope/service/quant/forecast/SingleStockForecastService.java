package com.finscope.service.quant.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.dao.strategy.StrategyHoldingRepository;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import com.finscope.domain.strategy.StrategyHolding;
import com.finscope.rpc.quant.PythonSingleStockForecastClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Python owns analytics; this service freezes each report with personal context. */
@Service
public class SingleStockForecastService {
    private final PythonSingleStockForecastClient client;
    private final SingleStockForecastRunRepository runs;
    private final StrategyHoldingRepository holdings;
    private final ForecastOutcomeSettlementService settlement;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    public SingleStockForecastService(PythonSingleStockForecastClient client,
                                      SingleStockForecastRunRepository runs,
                                      StrategyHoldingRepository holdings,
                                      ForecastOutcomeSettlementService settlement) {
        this.client = client;
        this.runs = runs;
        this.holdings = holdings;
        this.settlement = settlement;
    }

    SingleStockForecastService(PythonSingleStockForecastClient client,
                               SingleStockForecastRunRepository runs,
                               StrategyHoldingRepository holdings) {
        this(client, runs, holdings, null);
    }

    public SingleStockForecastRun forecast(String requestedCode, int horizonDays) {
        String code = normalizeCode(requestedCode);
        validateHorizon(horizonDays);
        settle(code + "." + market(code));
        SingleStockForecast report = client.forecast(code, horizonDays);
        SingleStockForecastRun.HoldingSnapshot holding = holdingSnapshot(code, report);
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
        SingleStockForecastRun saved = runs.save(value);
        saved.setReport(report);
        saved.setHoldingSnapshot(holding);
        return saved;
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
        if (instrumentCode == null) {
            settleAll();
        } else {
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
            value.setHoldingSnapshot(json.readValue(
                    value.getHoldingSnapshotJson(), SingleStockForecastRun.HoldingSnapshot.class));
            return value;
        } catch (Exception error) {
            throw new IllegalStateException("无法读取单股预测研究记录", error);
        }
    }

    private void settle(String instrumentCode) {
        if (settlement != null) {
            settlement.settlePending(instrumentCode);
        }
    }

    private void settleAll() {
        if (settlement != null) {
            settlement.settleAllPending();
        }
    }

    private SingleStockForecastRun.HoldingSnapshot holdingSnapshot(
            String code, SingleStockForecast report) {
        Optional<StrategyHolding> existing = holdings.findStockByCode(code);
        SingleStockForecastRun.HoldingSnapshot snapshot = new SingleStockForecastRun.HoldingSnapshot();
        snapshot.setHeld(existing.isPresent());
        snapshot.setInstrumentCode(report.getInstrumentCode());
        snapshot.setLastClose(report.getLastClose());
        if (!existing.isPresent()) {
            snapshot.setInterpretation("当前策略组合中没有这只股票；该报告只评价模型和历史虚拟策略。");
            return snapshot;
        }
        StrategyHolding holding = existing.get();
        snapshot.setInstrumentName(holding.getName());
        snapshot.setRole(holding.getRole());
        snapshot.setTargetWeight(holding.getTargetWeight());
        snapshot.setCurrentWeight(holding.getCurrentWeight());
        snapshot.setQuantity(holding.getQuantity());
        snapshot.setAverageCost(holding.getAverageCost());
        snapshot.setNote(holding.getNote());
        if (holding.getQuantity() != null && report.getLastClose() != null) {
            snapshot.setEstimatedMarketValue(holding.getQuantity() * report.getLastClose());
        }
        if (holding.getAverageCost() != null && holding.getAverageCost() > 0 && report.getLastClose() != null) {
            snapshot.setUnrealizedReturn(report.getLastClose() / holding.getAverageCost() - 1d);
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
