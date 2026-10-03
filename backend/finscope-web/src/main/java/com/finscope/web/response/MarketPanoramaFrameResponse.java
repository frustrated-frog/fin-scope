package com.finscope.web.response;

import com.finscope.domain.marketpulse.MarketIndexPerformance;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.SectorRotationItem;
import lombok.Data;
import java.util.List;

/** 全景时间轴的一帧，省去研究报告和重复的历史宽度数据。 */
@Data
public class MarketPanoramaFrameResponse {
    private String businessDate;
    private String headline;
    private Double advanceRatio;
    private Double ma20Ratio;
    private Double totalAmount;
    private List<MarketIndexPerformance> indices;
    private List<SectorRotationItem> sectors;

    public static MarketPanoramaFrameResponse of(MarketPulseWorkspace source) {
        MarketPanoramaFrameResponse value = new MarketPanoramaFrameResponse();
        value.setBusinessDate(source.getBusinessDate().toString());
        value.setHeadline(source.getDailyReview() == null ? null : source.getDailyReview().getHeadline());
        value.setSectors(source.getSectors());
        value.setIndices(List.of());
        if (source.getBreadth() != null) {
            var breadth = source.getBreadth();
            value.setIndices(breadth.getIndices());
            value.setAdvanceRatio(breadth.getAdvanceRatio());
            value.setTotalAmount(breadth.getTotalAmount());
            if (breadth.getTrendBreadth() != null) {
                value.setMa20Ratio(breadth.getTrendBreadth().getMa20Ratio());
            }
        }
        return value;
    }
}
