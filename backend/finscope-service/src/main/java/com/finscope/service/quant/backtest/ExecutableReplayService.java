package com.finscope.service.quant.backtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.quant.OpenExecutionState;
import com.finscope.domain.quant.backtest.BacktestRequest;
import com.finscope.domain.quant.backtest.BacktestResult;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.execution.*;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stateless, deterministic replay using the existing portfolio engine and ledger. */
@Service
public class ExecutableReplayService {
    private static final String ENGINE_VERSION = "executable-replay-v1";
    @Autowired
    private ExecutableReplayValidator validator;
    @Autowired
    private QuantBacktestEngine engine;
    @Autowired
    private ObjectMapper mapper;

    public ExecutableReplayReport replay(ExecutableReplayInput input) {
        validator.validate(input);
        TradingProtocol protocol = input.getProtocol();
        Map<String, String> industries = new LinkedHashMap<String, String>();
        Map<LocalDate, Map<String, Double>> targets = targets(input, industries);
        BacktestRequest request = new BacktestRequest();
        request.setInitialCapital(protocol.getInitialCapital());
        request.setSpec(spec(protocol));
        request.setBars(bars(input));
        PortfolioLedger ledger = new PortfolioLedger(protocol, industries);
        BacktestResult result = engine.runFrozen(request, targets, input.getTradingDates(), ledger);
        result.getWarnings().add("开盘价与开盘状态为输入中声明的模拟成交假设，不保证实际可成交");
        result.getWarnings().add("仅支持无公司行为区间；费用固定于本次协议，不代表全部历史费率");
        result.getWarnings().add("基准为输入行情的等权日收益，仅供研究，不是同资金可交易基准");
        result.getWarnings().add("仓位上限限制新增买入；价格变动或卖出受阻仍可能使实际权重超过上限");
        ExecutableReplayReport report = new ExecutableReplayReport();
        report.setInputFingerprint(fingerprint(input));
        report.setEngineVersion(ENGINE_VERSION);
        report.setProtocol(protocol);
        report.setSignals(input.getSignals());
        report.setFrozenInput(input);
        report.setTargetWeights(targets);
        report.setAccount(result);
        report.setOrders(new ArrayList<OrderAudit>(ledger.getOrders()));
        return report;
    }

    private Map<LocalDate, Map<String, Double>> targets(ExecutableReplayInput input, Map<String, String> industries) {
        Map<LocalDate, Map<String, Double>> result = new LinkedHashMap<LocalDate, Map<String, Double>>();
        TradingProtocol p = input.getProtocol();
        double slotWeight = Math.min(p.getMaxExposure() / p.getSlots(), p.getMaxSingleWeight());
        for (FrozenSignalBatch batch : input.getSignals()) {
            List<FrozenCandidate> candidates = new ArrayList<FrozenCandidate>();
            for (FrozenCandidate candidate : batch.getCandidates()) {
                industries.put(candidate.getInstrumentCode(), candidate.getIndustry());
                if (candidate.isEligible()) {
                    candidates.add(candidate);
                }
            }
            candidates.sort(Comparator.comparingDouble(FrozenCandidate::getRankingScore).reversed()
                    .thenComparing(FrozenCandidate::getInstrumentCode));
            Map<String, Double> selected = new LinkedHashMap<String, Double>();
            Map<String, Double> sectorWeights = new LinkedHashMap<String, Double>();
            for (FrozenCandidate candidate : candidates) {
                if (selected.size() == p.getSlots()) {
                    break;
                }
                double used = sectorWeights.getOrDefault(candidate.getIndustry(), 0d);
                if (used + slotWeight > p.getMaxIndustryWeight() + 1e-12) {
                    continue;
                }
                selected.put(candidate.getInstrumentCode(), slotWeight);
                sectorWeights.put(candidate.getIndustry(), used + slotWeight);
            }
            result.put(batch.getSignalDate(), selected);
        }
        return result;
    }

    private List<QuantDailyBar> bars(ExecutableReplayInput input) {
        List<QuantDailyBar> result = new ArrayList<QuantDailyBar>();
        for (ExecutionBar row : input.getBars()) {
            QuantDailyBar bar = new QuantDailyBar();
            bar.setInstrumentCode(row.getInstrumentCode());
            bar.setTradeDate(row.getTradeDate());
            bar.setOpen(row.getOpen());
            bar.setClose(row.getClose());
            bar.setAdjustedClose(row.getClose());
            bar.setHigh(row.getOpen().max(row.getClose()));
            bar.setLow(row.getOpen().min(row.getClose()));
            bar.setAmount(BigDecimal.ZERO);
            bar.setVolume(BigDecimal.ZERO);
            bar.setTradeStatus(row.getOpenState() == OpenExecutionState.SUSPENDED ? "SUSPENDED" : "TRADING");
            bar.setLimitUp(row.getOpenState() == OpenExecutionState.BUY_BLOCKED);
            bar.setLimitDown(row.getOpenState() == OpenExecutionState.SELL_BLOCKED);
            result.add(bar);
        }
        return result;
    }

    private QuantStrategySpec spec(TradingProtocol p) {
        QuantStrategySpec spec = new QuantStrategySpec();
        spec.setName(p.getVersion());
        QuantStrategySpec.Portfolio portfolio = new QuantStrategySpec.Portfolio();
        portfolio.setTopN(p.getSlots());
        portfolio.setRebalanceEvery(p.getRebalanceTradingDays());
        portfolio.setWeighting("FIXED_SLOTS");
        spec.setPortfolio(portfolio);
        spec.setFilters(new QuantStrategySpec.Filters());
        QuantStrategySpec.Execution execution = new QuantStrategySpec.Execution();
        execution.setSignalPrice("CLOSE");
        execution.setFillPrice("NEXT_OPEN");
        execution.setSlippageBps(p.getSlippageBps());
        spec.setExecution(execution);
        QuantStrategySpec.Cost cost = new QuantStrategySpec.Cost();
        cost.setBuyCommission(p.getBuyCommission());
        cost.setSellCommission(p.getSellCommission());
        cost.setMinimumCommission(p.getMinimumCommission());
        cost.setStampDuty(p.getStampDuty());
        spec.setCost(cost);
        return spec;
    }

    private String fingerprint(ExecutableReplayInput input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(input)));
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("无法计算回放输入指纹", error);
        }
    }
}
