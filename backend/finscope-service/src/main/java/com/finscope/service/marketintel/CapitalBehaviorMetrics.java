package com.finscope.service.marketintel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
public class CapitalBehaviorMetrics {
    private Latest latest;
    private Streak intradayStreak;
    private Streak dailyStreak;
    private List<ObjectiveTag> objectiveTags = Collections.emptyList();

    @Data
    public static class Latest {
        private BigDecimal tradeAmount;
        private BigDecimal tradeVolume;
        private BigDecimal turnoverRate;
        private BigDecimal volumeRatio;
        private BigDecimal mainNetInflow;
        private BigDecimal mainNetInflowSharePct;
        private LocalDateTime observedAt;
    }

    @Data
    public static class Streak {
        private String direction;
        private int periods;
        private String granularity;
        private LocalDateTime since;
        private LocalDateTime through;
    }

    @Data
    public static class ObjectiveTag {
        private String code;
        private String label;
        private String explanation;
        private String window;
        private String version;
        private List<String> metricRefs = Collections.emptyList();
        private Map<String, BigDecimal> actualValues = Collections.emptyMap();
        private Map<String, BigDecimal> thresholds = Collections.emptyMap();
    }
}
