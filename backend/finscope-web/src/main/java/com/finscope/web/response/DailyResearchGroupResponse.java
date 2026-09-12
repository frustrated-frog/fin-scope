package com.finscope.web.response;

import com.finscope.domain.marketpulse.DailyResearchGroup;
import lombok.Data;
import java.util.List;
import com.finscope.common.enums.marketpulse.MarketResearchGroup;

/** 日频研究的 Web 契约，日期统一为 ISO 字符串。 */
@Data
public class DailyResearchGroupResponse {
    private MarketResearchGroup code;
    private String label;
    private String definition;
    private Integer eligibleCount;
    private Integer memberCount;
    private Integer validCount;
    private Double advanceRatio;
    private Double medianReturn;
    private List<String> members;

    public static DailyResearchGroupResponse of(DailyResearchGroup source) {
        DailyResearchGroupResponse value = new DailyResearchGroupResponse();
        value.setCode(source.getCode());
        value.setLabel(source.getLabel());
        value.setDefinition(source.getDefinition());
        value.setEligibleCount(source.getEligibleCount());
        value.setMemberCount(source.getMemberCount());
        value.setValidCount(source.getValidCount());
        value.setAdvanceRatio(source.getAdvanceRatio());
        value.setMedianReturn(source.getMedianReturn());
        value.setMembers(source.getMembers());
        return value;
    }
}
