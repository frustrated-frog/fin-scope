package com.finscope.web.response;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.domain.marketpulse.DailyMarketReview;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 每日市场复盘的 Web 响应，日期统一输出为 ISO 文本。 */
@Data
public class DailyMarketReviewResponse {
    private String businessDate;
    private String headline;
    private String indexOverview;
    private String breadthConclusion;
    private List<String> leadingSectors;
    private List<String> weakeningSectors;
    private List<String> confirmedEvents;
    private List<String> riskSignals;
    private List<String> nextSessionWatchlist;
    private List<String> evidence;
    private MarketPulseQualityStatus qualityStatus;
    private String sourceFingerprint;
    private LocalDateTime generatedAt;

    public static DailyMarketReviewResponse of(DailyMarketReview source) {
        if (source == null) {
            return null;
        }
        DailyMarketReviewResponse value = new DailyMarketReviewResponse();
        value.setBusinessDate(source.getBusinessDate() == null ? null : source.getBusinessDate().toString());
        value.setHeadline(source.getHeadline());
        value.setIndexOverview(source.getIndexOverview());
        value.setBreadthConclusion(source.getBreadthConclusion());
        value.setLeadingSectors(source.getLeadingSectors());
        value.setWeakeningSectors(source.getWeakeningSectors());
        value.setConfirmedEvents(source.getConfirmedEvents());
        value.setRiskSignals(source.getRiskSignals());
        value.setNextSessionWatchlist(source.getNextSessionWatchlist());
        value.setEvidence(source.getEvidence());
        value.setQualityStatus(source.getQualityStatus());
        value.setSourceFingerprint(source.getSourceFingerprint());
        value.setGeneratedAt(source.getGeneratedAt());
        return value;
    }
}
