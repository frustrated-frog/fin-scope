package com.finscope.domain.radar;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class NewsRiverOverview {
    private LocalDate date;
    private LocalDate earliestDate;
    private List<RadarReplayFrame> frames;
    private List<RadarRiverBucket> buckets;
}
