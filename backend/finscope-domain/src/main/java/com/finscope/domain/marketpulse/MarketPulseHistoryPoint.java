package com.finscope.domain.marketpulse;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.common.enums.marketpulse.MarketStage;
import lombok.Data;

import java.time.LocalDate;

/** 历史演变视图使用的轻量每日事实点。 */
@Data
public class MarketPulseHistoryPoint {
    private LocalDate businessDate;
    private MarketStage marketStage;
    private int confidenceScore;
    private Double advanceRatio;
    private Double totalAmount;
    private Double medianChangePct;
    private String leadingSectorName;
    private Integer leadingSectorScore;
    private String headline;
    private MarketPulseQualityStatus qualityStatus;
}
