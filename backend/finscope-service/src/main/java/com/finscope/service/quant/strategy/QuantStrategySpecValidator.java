package com.finscope.service.quant.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.service.quant.factor.FactorRegistry;
import com.finscope.service.factorresearch.FactorProviderRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuantStrategySpecValidator {
    private final FactorRegistry factors;
    private final FactorProviderRegistry providers;
    public QuantStrategySpecValidator(FactorRegistry factors) { this(factors, null); }
    public QuantStrategySpecValidator(FactorRegistry factors, FactorProviderRegistry providers) {
        this.factors = factors; this.providers = providers;
    }

    public void validateOrThrow(QuantStrategySpec spec) {
        List<String> issues = validate(spec);
        if (!issues.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, String.join("；", issues));
    }

    public List<String> validate(QuantStrategySpec spec) {
        List<String> issues = new ArrayList<String>();
        if (spec == null) { issues.add("策略草案不能为空"); return issues; }
        if (!text(spec.getName())) issues.add("策略名称不能为空");
        if (spec.getDatasetId() == null) issues.add("必须选择数据集");
        if (!text(spec.getInvestmentHypothesis())) issues.add("投资假设不能为空");
        if (!text(spec.getRiskBoundary())) issues.add("风险边界不能为空");
        if ((spec.getStartDate() == null) != (spec.getEndDate() == null)) issues.add("回测起止日期必须同时提供");
        if (spec.getStartDate() != null && spec.getStartDate().isAfter(spec.getEndDate())) issues.add("回测开始日期不能晚于结束日期");
        if (!"EQUAL_WEIGHT".equals(spec.getBenchmark())) issues.add("第二期基准仅支持时点股票池等权基准 EQUAL_WEIGHT");
        if (spec.getFactors() == null || spec.getFactors().isEmpty()) issues.add("至少选择一个因子");
        else {
            Set<String> seen = new HashSet<String>(); double weight = 0;
            for (QuantStrategySpec.FactorWeight item : spec.getFactors()) {
                if (item == null) { issues.add("因子配置不能为空"); continue; }
                if (!known(item.getCode())) issues.add("未知因子：" + item.getCode());
                else if (!expectedDirection(item.getCode()).equals(item.getDirection()))
                    issues.add("因子方向必须与登记目录一致：" + item.getCode());
                if (!seen.add(item.getCode())) issues.add("因子不能重复：" + item.getCode());
                if (!Double.isFinite(item.getWeight()) || item.getWeight() <= 0 || item.getWeight() > 1) issues.add("因子权重必须在 0 到 1 之间");
                if (!"HIGH".equals(item.getDirection()) && !"LOW".equals(item.getDirection())) issues.add("因子方向只能是 HIGH 或 LOW");
                weight += item.getWeight();
            }
            if (Math.abs(weight - 1d) > 0.000001) issues.add("因子权重合计必须为 1");
        }
        if (spec.getPortfolio() == null) issues.add("缺少组合参数");
        else {
            if (spec.getPortfolio().getTopN() < 1 || spec.getPortfolio().getTopN() > 100) issues.add("持股数量必须在 1 到 100 之间");
            if (spec.getPortfolio().getRebalanceEvery() < 1 || spec.getPortfolio().getRebalanceEvery() > 120) issues.add("调仓周期必须在 1 到 120 个交易日之间");
            if (!"EQUAL".equals(spec.getPortfolio().getWeighting())) issues.add("第二期只支持等权组合");
        }
        if (spec.getFilters() == null) issues.add("缺少股票池过滤参数");
        else {
            if (spec.getFilters().getMinTradingDays() < 0 || spec.getFilters().getMinTradingDays() > 5000) issues.add("最少交易日必须在 0 到 5000 之间");
            if (!Double.isFinite(spec.getFilters().getMinAmount()) || spec.getFilters().getMinAmount() < 0) issues.add("最低成交额必须是非负有限数");
        }
        if (spec.getExecution() == null) issues.add("缺少执行参数");
        else {
            if (!"CLOSE".equals(spec.getExecution().getSignalPrice())) issues.add("信号只能在收盘后生成");
            if (!"NEXT_OPEN".equals(spec.getExecution().getFillPrice())) issues.add("订单必须在下一交易日执行");
            if (!Double.isFinite(spec.getExecution().getSlippageBps()) || spec.getExecution().getSlippageBps() < 0 || spec.getExecution().getSlippageBps() > 500) issues.add("滑点必须在 0 到 500 bps 之间");
        }
        if (spec.getCost() == null) issues.add("缺少交易成本参数");
        else if (!rate(spec.getCost().getBuyCommission()) || !rate(spec.getCost().getSellCommission())
                || !rate(spec.getCost().getStampDuty()) || !Double.isFinite(spec.getCost().getMinimumCommission())
                || spec.getCost().getMinimumCommission() < 0 || spec.getCost().getMinimumCommission() > 10000) {
            issues.add("交易成本参数超出允许范围");
        }
        return issues;
    }
    private boolean known(String code) {
        return factors.contains(code) || providers != null && providers.contains(code);
    }
    private String expectedDirection(String code) {
        return factors.contains(code) ? factors.get(code).getDirection() : "HIGH";
    }
    private boolean text(String value) { return value != null && !value.trim().isEmpty(); }
    private boolean rate(double value) { return value >= 0 && value <= 0.05 && Double.isFinite(value); }
}
