package com.finscope.service.radar;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RadarEventIdentityResolution {
    private int nativeIdentityCount;
    private int legacyReusedCount;
    private int legacyConflictCount;
    private int fallbackKeptCount;

    public String summary() {
        return "nativeKeys=" + nativeIdentityCount
                + ",legacyReused=" + legacyReusedCount
                + ",legacyConflicts=" + legacyConflictCount
                + ",fallbackKept=" + fallbackKeptCount;
    }
}
