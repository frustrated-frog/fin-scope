package com.finscope.domain.marketpulse;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MarketPulseRefreshResult {
    private LocalDate businessDate;
    private String status;
    private MarketPulseQualityStatus qualityStatus;
    private int sectorCount;
    private int eventConfirmationCount;
    private int candidateCount;
}
