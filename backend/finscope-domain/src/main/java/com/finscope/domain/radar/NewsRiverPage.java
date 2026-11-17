package com.finscope.domain.radar;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class NewsRiverPage {
    private RadarReplayFrame frame;
    private LocalDateTime bucket;
    private List<RadarReplayItem> items;
    private int total;
    private int page;
}
