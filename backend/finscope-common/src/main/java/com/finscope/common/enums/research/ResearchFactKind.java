package com.finscope.common.enums.research;

import lombok.Getter;

@Getter
public enum ResearchFactKind {
    EVENT("事件事实"), VOLUME("成交数量"), AMOUNT("成交金额"), PRICE("成交价格"), CHANGE_RATE("价格变动幅度"),
    MARKET_SPACE("市场空间"), PROFIT_MODEL("盈利模式"), COMPETITION("竞争格局"), GOVERNANCE("公司治理"),
    VALUATION("估值"), COUNTERCHECK("反方与风险");

    private final String label;

    ResearchFactKind(String label) {
        this.label = label;
    }
}
