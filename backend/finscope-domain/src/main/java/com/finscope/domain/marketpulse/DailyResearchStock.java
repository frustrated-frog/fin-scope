package com.finscope.domain.marketpulse;

import lombok.Data;
import java.util.List;
import com.finscope.common.enums.marketpulse.MarketResearchGroup;

/** 本地日频研究数据，收益统一为百分点；缺失指标保持 null。 */
@Data
public class DailyResearchStock {
    private String instrumentCode;
    private Double return1d;
    private Double return5d;
    private Double return20d;
    private Double amount;
    private List<MarketResearchGroup> groupCodes;
}
