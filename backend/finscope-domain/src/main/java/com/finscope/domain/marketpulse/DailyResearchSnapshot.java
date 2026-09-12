package com.finscope.domain.marketpulse;

import lombok.Data;
import java.util.List;
import java.time.LocalDate;
import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;

/** 本地日频研究数据，收益统一为百分点；缺失指标保持 null。 */
@Data
public class DailyResearchSnapshot {
    private Boolean cacheHit = false;
    private String calculatedAt;
    private LocalDate businessDate;
    private LocalDate selectionDate;
    private String sourceCode;
    private MarketPulseQualityStatus qualityStatus;
    private Integer sampleCount;
    private List<DailyResearchStock> stocks;
    private List<DailyResearchGroup> groups;
    private List<String> warnings;
}
