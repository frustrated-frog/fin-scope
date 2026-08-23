package com.finscope.domain.marketpulse;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class MarketPulseWorkspace {
    private LocalDate businessDate;
    private MarketRegimeSnapshot regime;
    private List<MarketRegimeSnapshot> recentRegimes = new ArrayList<>();
    private List<SectorRotationItem> sectors = new ArrayList<>();
    private List<MarketEventConfirmation> eventConfirmations = new ArrayList<>();
    private List<MarketPulseCandidate> candidates = new ArrayList<>();
    private MarketPulseQualityStatus qualityStatus;
    private List<String> warnings = new ArrayList<>();
    private LocalDateTime generatedAt;
}
