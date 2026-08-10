package com.finscope.rpc.quote;

import com.finscope.domain.instrument.FundHoldingDisclosure;

/** 基金公开披露持仓的外部数据边界。 */
public interface FundHoldingProvider {
    FundHoldingDisclosure fetch(String fundCode);
}
