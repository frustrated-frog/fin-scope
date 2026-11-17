package com.finscope.domain.radar;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RadarReplayFrame {
    private String id;
    private LocalDateTime publishedAt;
    private int eventCount;
}
