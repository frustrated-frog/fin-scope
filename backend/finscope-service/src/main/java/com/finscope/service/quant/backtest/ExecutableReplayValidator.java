package com.finscope.service.quant.backtest;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.quant.execution.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fails closed before any account calculation; evidence is supplied by the data producer. */
@Component
public class ExecutableReplayValidator {
    public void validate(ExecutableReplayInput input) {
        require(input != null && input.getProtocol() != null, "缺少交易协议");
        validateProtocol(input.getProtocol());
        require("RAW".equals(input.getPriceBasis()), "成交价格必须为 RAW 原始价格");
        require(text(input.getCalendarEvidence()), "缺少交易日历证据");
        require(Boolean.FALSE.equals(input.getHasCorporateActions()) && text(input.getCorporateActionEvidence()),
                "首版仅支持有证据确认区间内无公司行为的数据");
        List<LocalDate> dates = input.getTradingDates();
        require(dates != null && dates.size() >= 7 && dates.size() <= 1500, "交易日历长度须为 7 至 1500");
        LocalDate previous = null;
        for (LocalDate date : dates) {
            require(date != null && (previous == null || date.isAfter(previous)), "交易日历必须严格递增且唯一");
            previous = date;
        }
        Set<String> codes = validateSignals(input);
        validateBars(input, codes);
    }

    private void validateProtocol(TradingProtocol p) {
        require(p.getHoldingTradingDays() != null && p.getRebalanceTradingDays() != null && p.getSlots() != null
                && p.getInitialCapital() != null && p.getMaxExposure() != null && p.getMaxSingleWeight() != null
                && p.getMaxIndustryWeight() != null && p.getBuyCommission() != null && p.getSellCommission() != null
                && p.getMinimumCommission() != null && p.getStampDuty() != null && p.getSlippageBps() != null,
                "协议必须显式提供全部资金、成本与仓位参数");
        require("OPEN_5D_V1".equals(p.getVersion()) && p.getHoldingTradingDays() == 5
                && p.getRebalanceTradingDays() == 5 && p.getSlots() == 5, "不支持的交易协议版本或周期");
        require(LocalTime.of(15, 30).equals(p.getSignalTime())
                && LocalTime.of(9, 30).equals(p.getExecutionTime()), "协议时间必须为 15:30 信号及 09:30 开盘");
        require(Double.isFinite(p.getInitialCapital()) && p.getInitialCapital() >= 100
                && p.getInitialCapital() <= 1_000_000_000d, "模拟初始资金超出范围");
        require(weight(p.getMaxExposure()) && weight(p.getMaxSingleWeight()) && weight(p.getMaxIndustryWeight())
                && p.getMaxSingleWeight() <= p.getMaxExposure() / p.getSlots()
                && p.getMaxIndustryWeight() >= p.getMaxSingleWeight()
                && p.getMaxIndustryWeight() <= p.getMaxExposure(), "仓位或行业上限不合法");
        require(rate(p.getBuyCommission()) && rate(p.getSellCommission()) && rate(p.getStampDuty())
                && Double.isFinite(p.getMinimumCommission()) && p.getMinimumCommission() >= 0
                && p.getMinimumCommission() <= 1000 && Double.isFinite(p.getSlippageBps())
                && p.getSlippageBps() >= 0 && p.getSlippageBps() <= 1000, "成本参数不合法");
    }

    private Set<String> validateSignals(ExecutableReplayInput input) {
        List<LocalDate> dates = input.getTradingDates();
        List<FrozenSignalBatch> signals = input.getSignals();
        require(signals != null && signals.size() == (dates.size() - 2) / 5 + 1, "调仓信号批次不完整");
        Set<String> allCodes = new HashSet<String>();
        Map<String, String> industries = new LinkedHashMap<String, String>();
        for (int index = 0; index < signals.size(); index++) {
            FrozenSignalBatch batch = signals.get(index);
            LocalDate expected = dates.get(index * 5);
            require(batch != null && expected.equals(batch.getSignalDate()), "信号必须按日历每五日冻结，空批次也必须显式提供");
            require("OPEN_5D_V1".equals(batch.getProtocolVersion()), "信号的交易目标与协议不一致");
            require(expected.atTime(input.getProtocol().getSignalTime()).equals(batch.getInformationCutoff()), "信号信息截止时间不一致");
            require(batch.getTrainingLabelsMaturedBefore() != null
                    && batch.getTrainingLabelsMaturedBefore().isBefore(batch.getInformationCutoff()), "训练标签尚未成熟");
            require(text(batch.getModelVersion()) && text(batch.getDataFingerprint()) && text(batch.getUniverseEvidence()), "缺少模型版本、数据指纹或历史股票池证据");
            require(batch.getCandidates() != null && batch.getCandidates().size() <= 6000, "候选截面缺失或超限");
            Set<String> seen = new HashSet<String>();
            for (FrozenCandidate candidate : batch.getCandidates()) {
                require(candidate != null && candidate.getInstrumentCode() != null, "候选缺少证券代码");
                String code = candidate.getInstrumentCode();
                require(code.matches("(?:(?:600|601|603|605)\\d{3}\\.SH|(?:000|001|002|003)\\d{3}\\.SZ)"), "仅支持沪深主板代码");
                require(seen.add(code) && text(candidate.getIndustry()), "重复候选或缺少行业分类");
                require(candidate.getRankingScore() != null && Double.isFinite(candidate.getRankingScore()) && (candidate.getPredictedPriceReturn() == null
                        || Double.isFinite(candidate.getPredictedPriceReturn())), "候选分数不是有限值");
                require(candidate.isEligible() || text(candidate.getRejectionReason()), "未准入候选必须保留拒绝原因");
                String before = industries.putIfAbsent(code, candidate.getIndustry());
                require(before == null || before.equals(candidate.getIndustry()), "首版不支持回放区间内行业分类变更");
                allCodes.add(code);
            }
        }
        return allCodes;
    }

    private void validateBars(ExecutableReplayInput input, Set<String> codes) {
        List<ExecutionBar> bars = input.getBars();
        require(bars != null && bars.size() <= 500000, "执行行情缺失或超限");
        Set<LocalDate> calendar = new HashSet<LocalDate>(input.getTradingDates());
        Set<String> seen = new HashSet<String>();
        for (ExecutionBar bar : bars) {
            require(bar != null && calendar.contains(bar.getTradeDate()) && codes.contains(bar.getInstrumentCode()), "执行行情不在冻结日历或候选范围内");
            require(bar.getOpenState() != null && text(bar.getSourceEvidence()), "缺少开盘状态或来源证据");
            require(bar.getOpen() != null && bar.getClose() != null
                    && validPrice(bar.getOpen().doubleValue()) && validPrice(bar.getClose().doubleValue()), "执行价格不合法");
            require(seen.add(bar.getTradeDate() + "|" + bar.getInstrumentCode()), "执行行情股票日重复");
        }
        // Complete grid includes suspended symbols with an explicit carried valuation.
        // Do not use future availability to silently drop symbols from a historical universe.
        require((long) bars.size() == (long) codes.size() * calendar.size(), "执行行情必须完整覆盖日历和候选；停牌需显式状态与估值");
    }

    private boolean validPrice(double value) {
        return Double.isFinite(value) && value >= 0.01 && value <= 1_000_000;
    }

    private boolean rate(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 0.1;
    }

    private boolean weight(double value) {
        return Double.isFinite(value) && value > 0 && value <= 1;
    }

    private boolean text(String value) {
        return value != null && !value.trim().isEmpty() && value.length() <= 2000;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, message);
        }
    }
}
