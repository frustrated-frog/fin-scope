package com.finscope.service.quant.factor;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.quant.factor.FactorDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FactorRegistry {
    private final Map<String, FactorDefinition> values = new LinkedHashMap<String, FactorDefinition>();

    public FactorRegistry() {
        add("MOMENTUM_20D", "20日动量", "动量", "HIGH", "过去20个交易日复权收益", 20, false);
        add("MOMENTUM_60D", "60日动量", "动量", "HIGH", "过去60个交易日复权收益", 60, false);
        add("REVERSAL_5D", "5日反转", "反转", "HIGH", "过去5日收益的相反数", 5, false);
        add("VOLATILITY_20D", "20日低波", "波动", "HIGH", "过去20日日收益波动率的相反数", 20, false);
        add("AVG_AMOUNT_20D", "20日成交额", "流动性", "HIGH", "过去20日平均成交额的对数", 20, false);
        add("TURNOVER_PROXY_20D", "20日换手代理", "流动性", "LOW", "成交量相对均值的稳定性代理", 20, false);
        add("LOG_MARKET_CAP", "对数总市值", "规模", "LOW", "披露时点总市值的自然对数", 0, true);
        add("EP", "盈利收益率", "价值", "HIGH", "市盈率倒数", 0, true);
        add("BP", "账面市值比", "价值", "HIGH", "市净率倒数", 0, true);
        add("ROE", "净资产收益率", "质量", "HIGH", "披露时点 ROE", 0, true);
        add("LOW_DEBT", "低负债", "质量", "HIGH", "资产负债率的相反数", 0, true);
        add("REVENUE_GROWTH", "营收增长", "成长", "HIGH", "披露时点营收同比", 0, true);
        add("PROFIT_GROWTH", "利润增长", "成长", "HIGH", "披露时点净利润同比", 0, true);
    }

    public List<FactorDefinition> list() { return new ArrayList<FactorDefinition>(values.values()); }
    public FactorDefinition get(String code) {
        FactorDefinition value = values.get(code);
        if (value == null) throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "未知因子：" + code);
        return value;
    }
    public boolean contains(String code) { return values.containsKey(code); }
    private void add(String code, String name, String category, String direction,
                     String description, int lookback, boolean pointInTime) {
        values.put(code, new FactorDefinition(code, name, category, direction, description, lookback, pointInTime));
    }
}
