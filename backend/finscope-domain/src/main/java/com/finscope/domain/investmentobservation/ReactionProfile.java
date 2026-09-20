package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReactionProfile {
    private String version = "REACTION_PROFILE_V1";
    private int observedSessions;
    private boolean windowEnded;
    private boolean dataComplete;
    private boolean hasGaps;
    private BigDecimal beforeStockPct;
    private BigDecimal beforeBenchmarkPct;
    private BigDecimal beforeRelativePp;
    private BigDecimal firstRelativePp;
    private BigDecimal currentRelativePp;
    private BigDecimal peakRelativePp;
    private Integer peakSession;
    private BigDecimal givebackPp;
    private BigDecimal maxDrawdownPct;
    private BigDecimal firstVolumeRatio;
    private BigDecimal currentVolumeRatio;
    private String summary;
}
