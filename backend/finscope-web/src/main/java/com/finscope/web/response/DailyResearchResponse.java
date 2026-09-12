package com.finscope.web.response;

import com.finscope.domain.marketpulse.DailyResearchSnapshot;
import lombok.Data;
import java.util.List;
import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;

/** 日频研究的 Web 契约，日期统一为 ISO 字符串。 */
@Data
public class DailyResearchResponse {
    private String businessDate;
    private String selectionDate;
    private String sourceCode;
    private MarketPulseQualityStatus qualityStatus;
    private Integer sampleCount;
    private List<DailyResearchStockResponse> stocks;
    private List<DailyResearchGroupResponse> groups;
    private List<String> warnings;

    public static DailyResearchResponse of(DailyResearchSnapshot source) {
        DailyResearchResponse value = new DailyResearchResponse();
        value.setBusinessDate(source.getBusinessDate() == null ? null : source.getBusinessDate().toString());
        value.setSelectionDate(source.getSelectionDate() == null ? null : source.getSelectionDate().toString());
        value.setSourceCode(source.getSourceCode());
        value.setQualityStatus(source.getQualityStatus());
        value.setSampleCount(source.getSampleCount());
        value.setStocks(source.getStocks().stream().map(DailyResearchStockResponse::of).toList());
        value.setGroups(source.getGroups().stream().map(DailyResearchGroupResponse::of).toList());
        value.setWarnings(source.getWarnings());
        return value;
    }
}
