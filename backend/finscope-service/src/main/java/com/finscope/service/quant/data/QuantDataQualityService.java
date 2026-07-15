package com.finscope.service.quant.data;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuantDataQualityService {
    public void assertValidBars(List<QuantDailyBar> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日行情不能为空");
        }
        if (values.size() > 100_000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次最多导入 100000 条日行情");
        }
        Set<String> keys = new HashSet<String>();
        for (QuantDailyBar value : values) {
            require(value.getTradeDate() != null && text(value.getInstrumentCode()), "日行情缺少日期或标的代码");
            require(positive(value.getOpen()) && positive(value.getHigh()) && positive(value.getLow())
                    && positive(value.getClose()) && positive(value.getAdjustedClose()), "日行情价格必须大于零");
            BigDecimal top = value.getOpen().max(value.getClose()).max(value.getLow());
            BigDecimal bottom = value.getOpen().min(value.getClose()).min(value.getHigh());
            require(value.getHigh().compareTo(top) >= 0 && value.getLow().compareTo(bottom) <= 0,
                    "日行情存在非法 OHLC");
            require(nonNegative(value.getVolume()) && nonNegative(value.getAmount()), "成交量和成交额不能为负数");
            require(keys.add(value.getTradeDate() + "|" + value.getInstrumentCode()), "导入批次存在重复日行情");
        }
    }

    public void assertValidFundamentals(List<QuantFundamentalSnapshot> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "基本面快照不能为空");
        }
        for (QuantFundamentalSnapshot value : values) {
            require(value.getReportPeriod() != null && value.getDisclosedAt() != null,
                    "基本面快照缺少报告期或披露日期");
            require(!value.getDisclosedAt().isBefore(value.getReportPeriod()), "基本面披露日期不能早于报告期");
        }
    }

    private void require(boolean valid, String message) {
        if (!valid) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private boolean nonNegative(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    private boolean text(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
