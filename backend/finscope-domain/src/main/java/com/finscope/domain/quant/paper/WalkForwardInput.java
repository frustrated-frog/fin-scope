package com.finscope.domain.quant.paper;

import java.math.BigDecimal;
import java.util.List;

/** 冻结回测的单参数滚动验证方案。 */
public record WalkForwardInput(String parameter, List<BigDecimal> values, Integer trainDays, Integer testDays) {
}
