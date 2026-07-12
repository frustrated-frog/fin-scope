package com.finscope.service.quant.strategy;

import com.finscope.common.exception.BusinessException;
import com.finscope.domain.quant.strategy.QuantStrategySpec;
import com.finscope.service.quant.factor.FactorRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantStrategySpecValidatorTest {
    @Test
    void acceptsARestrictedNextOpenTopNStrategy() {
        QuantStrategySpec spec = validSpec();
        new QuantStrategySpecValidator(new FactorRegistry()).validateOrThrow(spec);
        assertEquals(2, spec.getFactors().size());
    }

    @Test
    void rejectsUnknownFactorsAndSameDayExecution() {
        QuantStrategySpec spec = validSpec();
        spec.getFactors().get(0).setCode("HALLUCINATED_ALPHA");
        spec.getExecution().setFillPrice("SAME_CLOSE");

        BusinessException error = assertThrows(BusinessException.class,
                () -> new QuantStrategySpecValidator(new FactorRegistry()).validateOrThrow(spec));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("未知因子"));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("下一交易日"));
    }

    static QuantStrategySpec validSpec() {
        QuantStrategySpec spec = new QuantStrategySpec();
        spec.setName("质量价值动量"); spec.setDatasetId(1L); spec.setBenchmark("000300.SH");
        spec.setFactors(Arrays.asList(new QuantStrategySpec.FactorWeight("ROE", 0.5, "HIGH"),
                new QuantStrategySpec.FactorWeight("MOMENTUM_20D", 0.5, "HIGH")));
        QuantStrategySpec.Portfolio portfolio = new QuantStrategySpec.Portfolio();
        portfolio.setTopN(20); portfolio.setRebalanceEvery(20); portfolio.setWeighting("EQUAL");
        spec.setPortfolio(portfolio);
        QuantStrategySpec.Filters filters = new QuantStrategySpec.Filters();
        filters.setExcludeSt(true); filters.setMinTradingDays(60); filters.setMinAmount(5000000);
        spec.setFilters(filters);
        QuantStrategySpec.Execution execution = new QuantStrategySpec.Execution();
        execution.setSignalPrice("CLOSE"); execution.setFillPrice("NEXT_OPEN"); execution.setSlippageBps(10);
        spec.setExecution(execution);
        QuantStrategySpec.Cost cost = new QuantStrategySpec.Cost();
        cost.setBuyCommission(0.0003); cost.setSellCommission(0.0003);
        cost.setStampDuty(0.001); cost.setMinimumCommission(5);
        spec.setCost(cost); spec.setInvestmentHypothesis("质量与价值约束降低脆弱性，中期动量提供趋势确认");
        spec.setRiskBoundary("仅用于历史研究，不构成交易建议");
        return spec;
    }
}
