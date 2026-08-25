package com.finscope.rpc.quote;

import com.finscope.domain.instrument.FundHoldingDisclosure;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/** 基金持仓数据源路由：优先扶摇，失败或未配置时回退东方财富。 */
@Component
@Primary
public class FundHoldingProviderRouter implements FundHoldingProvider {

    @Resource
    private FuyaoFundHoldingProvider fuyao;

    @Resource
    private EastmoneyFundHoldingProvider eastmoney;

    @Override
    public FundHoldingDisclosure fetch(String fundCode) {
        if (!fuyao.isConfigured()) {
            return eastmoney.fetch(fundCode);
        }
        try {
            return fuyao.fetch(fundCode);
        } catch (RuntimeException primaryError) {
            try {
                return eastmoney.fetch(fundCode);
            } catch (RuntimeException fallbackError) {
                fallbackError.addSuppressed(primaryError);
                throw fallbackError;
            }
        }
    }
}
