package com.finscope.rpc.quote;

import com.finscope.domain.instrument.FundHoldingDisclosure;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/** 基金持仓统一入口，仅使用扶摇同花顺结构化接口。 */
@Component
@Primary
public class FundHoldingProviderRouter implements FundHoldingProvider {

    @Resource
    private FuyaoFundHoldingProvider fuyao;

    @Override
    public FundHoldingDisclosure fetch(String fundCode) {
        return fuyao.fetch(fundCode);
    }
}
