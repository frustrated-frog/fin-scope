package com.finscope.service.fundamentals;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.Set;

/** 只解析已登记标的，读取底稿不隐式建档或查询行情。 */
@Service
public class FundamentalStockResolver {
    @Resource
    private InstrumentRepository instruments;

    public Instrument require(String code) {
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        Instrument instrument = instruments.findByCodeAndType(code, "STOCK")
                .orElseThrow(() -> new ResourceNotFoundException("股票标的不存在"));
        if (instrument.getMarket() == null || !Set.of("SH", "SZ", "BJ").contains(instrument.getMarket())) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "基本面研究当前支持 A 股股票");
        }
        return instrument;
    }
}
