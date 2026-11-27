package com.finscope.domain.quant.paper;

import java.math.BigDecimal;

/** 经典策略回测请求。具体组件组合由执行引擎统一校验。 */
public record ClassicPaperResearchInput(BigDecimal capital, boolean autoStart, Long reference, Components recipe,
                                       String startDate, String endDate, String question) {
    public record Components(String family, String base, String period, Integer window, Integer holdings,
                             Integer poolSize, BigDecimal minTurnover, String allocation, BigDecimal exposure,
                             BigDecimal maxWeight, String rebalance, BigDecimal maxFeeBps, Integer riskWindow,
                             BigDecimal targetVol, BigDecimal stopLoss, BigDecimal drawdownStop) {
    }
}
