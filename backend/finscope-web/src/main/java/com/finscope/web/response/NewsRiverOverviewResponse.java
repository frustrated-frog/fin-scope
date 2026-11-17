package com.finscope.web.response;

import com.finscope.domain.radar.NewsRiverOverview;
import com.finscope.domain.radar.RadarReplayFrame;
import com.finscope.domain.radar.RadarRiverBucket;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class NewsRiverOverviewResponse {
    private LocalDate date;
    private LocalDate earliestDate;
    private List<RadarReplayFrame> frames;
    private List<RadarRiverBucket> buckets;

    public static NewsRiverOverviewResponse from(NewsRiverOverview value) {
        NewsRiverOverviewResponse result = new NewsRiverOverviewResponse();
        result.setDate(value.getDate());
        result.setEarliestDate(value.getEarliestDate());
        result.setFrames(value.getFrames());
        result.setBuckets(value.getBuckets());
        return result;
    }
}
