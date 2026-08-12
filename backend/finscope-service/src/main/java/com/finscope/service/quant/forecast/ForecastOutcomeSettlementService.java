package com.finscope.service.quant.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Settles immutable forecasts using the same T+1 open-to-open label used by Python. */
@Service
public class ForecastOutcomeSettlementService {
    private static final double DEFAULT_ROUND_TRIP_COST = 0.0015d;
    private final SingleStockForecastRunRepository runs;
    private final ForecastRunPersistenceService persistence;
    private final QuantDailyBarSource bars;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    public ForecastOutcomeSettlementService(SingleStockForecastRunRepository runs,
                                            ForecastRunPersistenceService persistence,
                                            QuantDailyBarSource bars) {
        this.runs = runs;
        this.persistence = persistence;
        this.bars = bars;
    }

    ForecastOutcomeSettlementService(SingleStockForecastRunRepository runs,
                                     QuantDailyBarSource bars) {
        this(runs, null, bars);
    }

    public SettlementSummary settlePending(String instrumentCode) {
        List<SingleStockForecastRun> pending = runs.findPending(instrumentCode, 200);
        if (pending.isEmpty()) {
            return new SettlementSummary(0, 0, 0);
        }
        QuantDailyBarBatch batch = bars.fetch(instrumentCode, 5000);
        List<QuantDailyBar> ordered = new ArrayList<QuantDailyBar>(batch.getBars());
        ordered.sort(Comparator.comparing(QuantDailyBar::getTradeDate));
        int matured = 0;
        int waiting = 0;
        int unavailable = 0;
        for (SingleStockForecastRun run : pending) {
            int signal = indexOf(ordered, run.getAsOfDate());
            if (signal < 0) {
                if (markUnavailable(run.getId(), "历史信号日无法在当前 QFQ 日线中定位")) {
                    unavailable++;
                }
                continue;
            }
            int entryIndex = signal + 1;
            int exitIndex = signal + run.getHorizonDays() + 1;
            if (exitIndex >= ordered.size()) {
                waiting++;
                continue;
            }
            QuantDailyBar entry = ordered.get(entryIndex);
            QuantDailyBar exit = ordered.get(exitIndex);
            SingleStockForecast report = report(run);
            double cost = roundTripCost(report);
            double netReturn = exit.getOpen().doubleValue() / entry.getOpen().doubleValue() - 1d - cost;
            String actualDirection = netReturn > 0d ? "UP" : "DOWN";
            String decision = report == null ? null : report.getModelDecision() == null
                    ? report.getDecision() : report.getModelDecision();
            SingleStockForecastRun.ForecastOutcome outcome = new SingleStockForecastRun.ForecastOutcome();
            outcome.setEntryDate(entry.getTradeDate());
            outcome.setExitDate(exit.getTradeDate());
            outcome.setEntryOpen(entry.getOpen().doubleValue());
            outcome.setExitOpen(exit.getOpen().doubleValue());
            outcome.setActualNetReturn(netReturn);
            outcome.setActualDirection(actualDirection);
            outcome.setCorrect("UP".equals(decision) || "DOWN".equals(decision)
                    ? decision.equals(actualDirection) : null);
            outcome.setSettledAt(LocalDateTime.now());
            outcome.setSourceCode(batch.getSourceCode());
            outcome.setNote("按冻结 T+1 开盘入场、T+N+1 开盘退出与双边成本口径结算");
            if (settle(run.getId(), outcome)) {
                matured++;
            }
        }
        return new SettlementSummary(matured, waiting, unavailable);
    }

    private boolean settle(long forecastRunId, SingleStockForecastRun.ForecastOutcome outcome) {
        return persistence == null ? runs.settle(forecastRunId, outcome)
                : persistence.settle(forecastRunId, outcome);
    }

    private boolean markUnavailable(long forecastRunId, String note) {
        return persistence == null ? runs.markUnavailable(forecastRunId, note)
                : persistence.markUnavailable(forecastRunId, note);
    }

    private int indexOf(List<QuantDailyBar> values, java.time.LocalDate date) {
        for (int index = 0; index < values.size(); index++) {
            if (date.equals(values.get(index).getTradeDate())) {
                return index;
            }
        }
        return -1;
    }

    private SingleStockForecast report(SingleStockForecastRun run) {
        if (run.getReport() != null) {
            return run.getReport();
        }
        try {
            return json.readValue(run.getReportJson(), SingleStockForecast.class);
        } catch (Exception error) {
            throw new IllegalStateException("无法读取待结算预测报告", error);
        }
    }

    private double roundTripCost(SingleStockForecast report) {
        if (report == null || report.getStrategyPolicy() == null
                || report.getStrategyPolicy().getRoundTripCostRate() < 0d) {
            return DEFAULT_ROUND_TRIP_COST;
        }
        return report.getStrategyPolicy().getRoundTripCostRate();
    }

    @Value
    public static class SettlementSummary {
        int matured;
        int pending;
        int unavailable;
    }
}
