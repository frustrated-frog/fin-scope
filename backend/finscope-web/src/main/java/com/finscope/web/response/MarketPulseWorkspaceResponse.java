package com.finscope.web.response;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.domain.marketpulse.MarketEventConfirmation;
import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketPulseCandidate;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.MarketRegimeSnapshot;
import com.finscope.domain.marketpulse.SectorRotationItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 面向 Web 的市场机会工作台响应，日期统一输出为 ISO 文本。 */
@Data
public class MarketPulseWorkspaceResponse {
    private String businessDate;
    private MarketRegimeSnapshot regime;
    private MarketBreadthSnapshot breadth;
    private List<MarketRegimeSnapshot> recentRegimes;
    private List<SectorRotationItem> sectors;
    private List<MarketEventConfirmation> eventConfirmations;
    private List<MarketPulseCandidate> candidates;
    private MarketPulseQualityStatus qualityStatus;
    private List<String> warnings;
    private LocalDateTime generatedAt;

    public static MarketPulseWorkspaceResponse of(MarketPulseWorkspace source) {
        MarketPulseWorkspaceResponse value = new MarketPulseWorkspaceResponse();
        value.setBusinessDate(source.getBusinessDate() == null ? null : source.getBusinessDate().toString());
        value.setRegime(source.getRegime());
        value.setBreadth(source.getBreadth());
        value.setRecentRegimes(source.getRecentRegimes());
        value.setSectors(source.getSectors());
        value.setEventConfirmations(source.getEventConfirmations());
        value.setCandidates(source.getCandidates());
        value.setQualityStatus(source.getQualityStatus());
        value.setWarnings(source.getWarnings());
        value.setGeneratedAt(source.getGeneratedAt());
        return value;
    }
}
