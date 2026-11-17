package com.finscope.domain.radar;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RadarRiverBucket {
    private LocalDateTime start;
    private int finance;
    private int technology;
    private int politics;
    private int other;
}
