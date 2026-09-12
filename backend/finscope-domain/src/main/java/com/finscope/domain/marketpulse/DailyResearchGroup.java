package com.finscope.domain.marketpulse;

import lombok.Data;
import java.util.List;
import com.finscope.common.enums.marketpulse.MarketResearchGroup;

/** 本地日频研究数据，收益统一为百分点；缺失指标保持 null。 */
@Data
public class DailyResearchGroup {
    private MarketResearchGroup code;
    private String label;
    private String definition;
    private Integer eligibleCount;
    private Integer memberCount;
    private Integer validCount;
    private Double advanceRatio;
    private Double medianReturn;
    private List<String> members;
}
