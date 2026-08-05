package com.finscope.service.radar;

import java.util.Locale;

/**
 * Normalized source quality used by both hotspot scoring and research priority.
 * Provider configuration historically used both T1/T2 and TIER_1/TIER_2.
 */
public enum RadarSourceQuality {
    TIER_1(1.00D, 15),
    TIER_2(0.75D, 10),
    TIER_3(0.50D, 5);

    private final double hotnessWeight;
    private final int priorityPoints;

    RadarSourceQuality(double hotnessWeight, int priorityPoints) {
        this.hotnessWeight = hotnessWeight;
        this.priorityPoints = priorityPoints;
    }

    public double getHotnessWeight() {
        return hotnessWeight;
    }

    public int getPriorityPoints() {
        return priorityPoints;
    }

    public static RadarSourceQuality resolve(String rawTier) {
        String tier = rawTier == null ? "" : rawTier.trim().toUpperCase(Locale.ROOT);
        if ("T1".equals(tier) || "TIER_1".equals(tier)
                || "PRIMARY".equals(tier) || "OFFICIAL".equals(tier)) {
            return TIER_1;
        }
        if ("T2".equals(tier) || "TIER_2".equals(tier)
                || "MEDIA".equals(tier) || "PROFESSIONAL".equals(tier)) {
            return TIER_2;
        }
        return TIER_3;
    }
}
