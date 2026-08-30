package com.finscope.service.strategy.holding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.dao.quant.SingleStockForecastRunRepository;
import com.finscope.dao.strategy.HoldingStrategyDecisionRepository;
import com.finscope.domain.quant.forecast.ForecastModelHealth;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.domain.quant.forecast.SingleStockForecastRun;
import com.finscope.domain.strategy.holding.HoldingStrategyAdvice;
import com.finscope.domain.strategy.holding.HoldingStrategyDecision;
import com.finscope.domain.strategy.holding.HoldingStrategyEvaluationRequest;
import com.finscope.domain.strategy.holding.HoldingStrategySettlementRequest;
import com.finscope.domain.strategy.holding.HoldingStrategySettlementResult;
import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockPosition;
import com.finscope.rpc.quant.PythonHoldingStrategyClient;
import com.finscope.service.quant.forecast.SingleStockForecastService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Freezes position advice from already-persisted forecasts without retraining or contaminating the model. */
@Service
@Slf4j
public class HoldingStrategyDecisionService {
    private static final String POLICY_VERSION = "holding-policy-v1";
    private static final String BENCHMARK = "同一只股票保持当时持仓不动";

    @Resource
    private HoldingStrategyDecisionRepository decisions;
    @Resource
    private SingleStockForecastRunRepository forecastRuns;
    @Resource
    private SingleStockForecastService forecasts;
    @Resource
    private StockAccountService accounts;
    @Resource
    private PythonHoldingStrategyClient client;

    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public List<HoldingStrategyDecision> list(int limit) {
        settleDue();
        return decisions.findAll(limit);
    }

    public List<HoldingStrategyDecision> refresh() {
        settleDue();
        StockAccountSnapshot account = accounts.snapshot();
        List<HoldingStrategyDecision> result = new ArrayList<HoldingStrategyDecision>();
        for (StockPosition position : account.getPositions()) {
            result.add(evaluateOne(position, account));
        }
        return result;
    }

    private void settleDue() {
        List<HoldingStrategyDecision> pending = decisions.findPendingDue(LocalDate.now(), 100);
        for (HoldingStrategyDecision decision : pending) {
            try {
                settleOne(decision);
            } catch (RuntimeException error) {
                log.warn("持仓影子策略到期结算失败 decisionId={} code={} error={}",
                        decision.getId(), decision.getInstrumentCode(), error.getMessage());
            }
        }
    }

    private void settleOne(HoldingStrategyDecision decision) {
        if (decision.getForecastRunId() == null) {
            decisions.markUnavailable(decision.getId());
            return;
        }
        SingleStockForecastRun run = forecasts.detail(decision.getForecastRunId());
        if (run.getMaturityStatus() == SingleStockForecastRun.MaturityStatus.UNAVAILABLE) {
            decisions.markUnavailable(decision.getId());
            return;
        }
        if (run.getMaturityStatus() != SingleStockForecastRun.MaturityStatus.MATURED
                || run.getOutcome() == null || run.getOutcome().getActualNetReturn() == null) {
            return;
        }
        HoldingStrategyEvaluationRequest frozenInput = readFrozenInput(decision.getInputJson());
        double entryPrice = run.getOutcome().getEntryOpen() == null
                ? frozenInput.getMarketPrice() : run.getOutcome().getEntryOpen();
        HoldingStrategySettlementRequest request = new HoldingStrategySettlementRequest();
        request.setAction(decision.getAction());
        request.setSuggestedQuantity(decision.getSuggestedQuantity());
        request.setHeldQuantity(frozenInput.getQuantity());
        request.setCurrentMarketValue(decision.getCurrentMarketValue());
        request.setEntryPrice(entryPrice);
        request.setActualNetReturn(run.getOutcome().getActualNetReturn());
        HoldingStrategySettlementResult result = client.settle(request);
        decisions.settle(decision.getId(), result.getStrategyReturn(),
                result.getHoldReturn(), result.getIncrementalReturn());
    }

    private HoldingStrategyDecision evaluateOne(StockPosition position, StockAccountSnapshot account) {
        LocalDate decisionDate = LocalDate.now();
        Optional<HoldingStrategyDecision> existing = decisions.findUnique(
                position.getInstrumentCode(), decisionDate, POLICY_VERSION);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!hasValuation(position) || account.getTotalEquity().signum() <= 0) {
            return unavailable(position, decisionDate, "真实行情或账户净值暂不可用");
        }
        Optional<SingleStockForecastRun> latest = forecastRuns.findLatest(position.getInstrumentCode());
        if (!latest.isPresent()) {
            return unavailable(position, decisionDate, "尚无可复用的单股预测记录");
        }
        try {
            SingleStockForecastRun run = forecasts.detail(latest.get().getId());
            HoldingStrategyEvaluationRequest request = request(position, account, run);
            HoldingStrategyAdvice advice = client.evaluate(request);
            return decisions.save(freeze(position, run, request, advice, decisionDate));
        } catch (RuntimeException error) {
            log.warn("持仓影子策略单股评估失败 code={} error={}",
                    position.getInstrumentCode(), error.getMessage());
            return unavailable(position, decisionDate, "量化证据暂不可用：" + safeMessage(error));
        }
    }

    private HoldingStrategyEvaluationRequest request(StockPosition position,
                                                     StockAccountSnapshot account,
                                                     SingleStockForecastRun run) {
        SingleStockForecast report = run.getReport();
        if (report == null || report.getReturnDistribution() == null
                || report.getUpProbability() == null
                || report.getReturnDistribution().getP10() == null
                || report.getReturnDistribution().getP50() == null
                || report.getReturnDistribution().getP90() == null) {
            throw new IllegalStateException("预测记录缺少概率或收益分布");
        }
        HoldingStrategyEvaluationRequest request = new HoldingStrategyEvaluationRequest();
        request.setInstrumentCode(position.getInstrumentCode());
        request.setAsOfDate(report.getAsOfDate());
        request.setHorizonDays(run.getHorizonDays());
        request.setMarketPrice(position.getLastPrice().doubleValue());
        request.setQuantity(position.getQuantity().doubleValue());
        request.setCash(account.getCash().doubleValue());
        request.setTotalEquity(account.getTotalEquity().doubleValue());
        request.setCurrentWeight(divide(position.getMarketValue(), account.getTotalEquity()));
        request.setUpProbability(report.getUpProbability());
        request.setP10Return(report.getReturnDistribution().getP10());
        request.setP50Return(report.getReturnDistribution().getP50());
        request.setP90Return(report.getReturnDistribution().getP90());
        request.setForecastStatus(report.getStatus());
        request.setModelHealthStatus(healthStatus(run.getModelHealth()));
        request.setQuoteAgeDays(tradingDayAge(position.getQuoteDate(), LocalDate.now()));
        if (report.getStrategyPolicy() != null) {
            request.setRoundTripCostRate(report.getStrategyPolicy().getRoundTripCostRate());
        }
        request.setForecastRunId(run.getId());
        request.setModelVersion(run.getModelVersion());
        request.setDataFingerprint(run.getDataFingerprint());
        request.setCostBasis(position.getAverageCost().doubleValue());
        if (position.getAverageCost().signum() > 0) {
            request.setUnrealizedReturn(position.getLastPrice().subtract(position.getAverageCost())
                    .divide(position.getAverageCost(), 8, RoundingMode.HALF_UP).doubleValue());
        }
        return request;
    }

    private HoldingStrategyDecision freeze(StockPosition position,
                                           SingleStockForecastRun run,
                                           HoldingStrategyEvaluationRequest request,
                                           HoldingStrategyAdvice advice,
                                           LocalDate decisionDate) {
        HoldingStrategyDecision value = new HoldingStrategyDecision();
        value.setInstrumentCode(position.getInstrumentCode());
        value.setInstrumentName(position.getInstrumentName());
        value.setDecisionDate(decisionDate);
        value.setForecastRunId(run.getId());
        value.setHorizonDays(run.getHorizonDays());
        value.setModelVersion(run.getModelVersion());
        value.setDataFingerprint(run.getDataFingerprint());
        value.setAction(advice.getAction());
        value.setSuggestedQuantity(advice.getSuggestedQuantity());
        value.setExpectedEdgeAfterCost(advice.getExpectedEdgeAfterCost());
        value.setP10RiskAmount(advice.getP10RiskAmount());
        value.setP90UpsideAmount(advice.getP90UpsideAmount());
        value.setCurrentMarketValue(advice.getCurrentMarketValue());
        value.setProjectedWeight(advice.getProjectedWeight());
        value.setEvidence(advice.getEvidence());
        value.setBlockers(advice.getBlockers());
        value.setExplanation(advice.getExplanation());
        value.setBenchmark(advice.getBenchmark());
        value.setPolicyVersion(advice.getPolicyVersion());
        value.setValidationStatus("PENDING");
        value.setMaturityDate(addTradingDays(request.getAsOfDate(), run.getHorizonDays()));
        value.setInputJson(write(request));
        value.setOutputJson(write(advice));
        return value;
    }

    private HoldingStrategyDecision unavailable(StockPosition position,
                                                LocalDate decisionDate,
                                                String reason) {
        HoldingStrategyDecision value = new HoldingStrategyDecision();
        value.setInstrumentCode(position.getInstrumentCode());
        value.setInstrumentName(position.getInstrumentName());
        value.setDecisionDate(decisionDate);
        value.setAction("ABSTAIN");
        value.setModelVersion("UNAVAILABLE");
        value.setDataFingerprint("UNAVAILABLE");
        value.setBenchmark(BENCHMARK);
        value.setPolicyVersion(POLICY_VERSION + "-unavailable");
        value.setValidationStatus("UNAVAILABLE");
        value.getBlockers().add(reason);
        value.setExplanation("证据链不完整，本次不改变真实持仓，也不生成可回测的冻结建议。");
        if (position.getMarketValue() != null) {
            value.setCurrentMarketValue(position.getMarketValue().doubleValue());
        }
        return value;
    }

    private boolean hasValuation(StockPosition position) {
        return position.getLastPrice() != null && position.getLastPrice().signum() > 0
                && position.getMarketValue() != null && position.getQuoteDate() != null;
    }

    private String healthStatus(ForecastModelHealth health) {
        if (health == null) {
            return "UNVERIFIED";
        }
        if (health.isDirectionOutputPaused()) {
            return "PAUSED";
        }
        return health.getStatus() == null ? "UNVERIFIED" : health.getStatus();
    }

    private int tradingDayAge(LocalDate quoteDate, LocalDate now) {
        if (quoteDate == null || !quoteDate.isBefore(now)) {
            return 0;
        }
        int days = 0;
        LocalDate cursor = quoteDate.plusDays(1);
        while (!cursor.isAfter(now)) {
            if (isTradingDay(cursor)) {
                days++;
            }
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    private LocalDate addTradingDays(LocalDate start, int tradingDays) {
        LocalDate cursor = start;
        int remaining = tradingDays;
        while (remaining > 0) {
            cursor = cursor.plusDays(1);
            if (isTradingDay(cursor)) {
                remaining--;
            }
        }
        return cursor;
    }

    private boolean isTradingDay(LocalDate value) {
        return value.getDayOfWeek() != DayOfWeek.SATURDAY
                && value.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private double divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) {
            return 0d;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP).doubleValue();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("无法冻结持仓策略证据", error);
        }
    }

    private HoldingStrategyEvaluationRequest readFrozenInput(String value) {
        try {
            return json.readValue(value, HoldingStrategyEvaluationRequest.class);
        } catch (Exception error) {
            throw new IllegalStateException("无法读取冻结持仓策略输入", error);
        }
    }

    private String safeMessage(RuntimeException error) {
        if (error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return error.getMessage();
    }
}
