package com.finscope.web.response;

import com.finscope.domain.marketpulse.DailyResearchStock;
import lombok.Data;
import java.util.List;
import com.finscope.common.enums.marketpulse.MarketResearchGroup;

/** 日频研究的 Web 契约，日期统一为 ISO 字符串。 */
@Data
public class DailyResearchStockResponse {
    private String instrumentCode;
    private Double return1d;
    private Double return5d;
    private Double return20d;
    private Double amount;
    private List<MarketResearchGroup> groupCodes;

    public static DailyResearchStockResponse of(DailyResearchStock source) {
        DailyResearchStockResponse value = new DailyResearchStockResponse();
        value.setInstrumentCode(source.getInstrumentCode());
        value.setReturn1d(source.getReturn1d());
        value.setReturn5d(source.getReturn5d());
        value.setReturn20d(source.getReturn20d());
        value.setAmount(source.getAmount());
        value.setGroupCodes(source.getGroupCodes());
        return value;
    }
}
