package com.finscope.domain.radar;

import lombok.Data;
import java.util.List;

@Data
public class RadarChangePage {
    private String asOf;
    private List<RadarRecentChange> items;
    private boolean hasMore;
}
