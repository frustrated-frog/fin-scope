package com.finscope.web.response;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.domain.marketpulse.MarketPulseHistoryPoint;
import lombok.Data;

/** 历史演变事实点的 Web 响应，日期统一输出为 ISO 文本。 */
@Data
public class MarketPulseHistoryPointResponse {
    private String businessDate;
    private MarketStage marketStage;
    private int confidenceScore;
    private Double advanceRatio;
    private Double totalAmount;
    private Double medianChangePct;
    private String leadingSectorName;
    private Integer leadingSectorScore;
    private String headline;
    private MarketPulseQualityStatus qualityStatus;

    public static MarketPulseHistoryPointResponse of(MarketPulseHistoryPoint source) {
        MarketPulseHistoryPointResponse value = new MarketPulseHistoryPointResponse();
        value.setBusinessDate(source.getBusinessDate() == null ? null : source.getBusinessDate().toString());
        value.setMarketStage(source.getMarketStage());
        value.setConfidenceScore(source.getConfidenceScore());
        value.setAdvanceRatio(source.getAdvanceRatio());
        value.setTotalAmount(source.getTotalAmount());
        value.setMedianChangePct(source.getMedianChangePct());
        value.setLeadingSectorName(source.getLeadingSectorName());
        value.setLeadingSectorScore(source.getLeadingSectorScore());
        value.setHeadline(source.getHeadline());
        value.setQualityStatus(source.getQualityStatus());
        return value;
    }
}
