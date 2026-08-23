package com.finscope.domain.marketpulse;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 行业轮动计算结果，同时保留历史数据质量与降级原因。 */
@Data
public class MarketPulseSectorResult {
    private List<SectorRotationItem> sectors = new ArrayList<>();
    private MarketPulseQualityStatus qualityStatus = MarketPulseQualityStatus.PARTIAL;
    private List<String> warnings = new ArrayList<>();
}
