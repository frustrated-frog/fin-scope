package com.finscope.web.response;

import com.finscope.domain.radar.NewsRiverPage;
import com.finscope.domain.radar.RadarReplayFrame;
import com.finscope.domain.radar.RadarReplayItem;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class NewsRiverPageResponse {
    private RadarReplayFrame frame;
    private LocalDateTime bucket;
    private List<RadarReplayItem> items;
    private int total;
    private int page;

    public static NewsRiverPageResponse from(NewsRiverPage value) {
        NewsRiverPageResponse result = new NewsRiverPageResponse();
        result.setFrame(value.getFrame());
        result.setBucket(value.getBucket());
        result.setItems(value.getItems());
        result.setTotal(value.getTotal());
        result.setPage(value.getPage());
        return result;
    }
}
