package com.finscope.domain.watchlistresearch;

import com.finscope.common.enums.watchlistresearch.ResearchScanStatus;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;

@Data
public class ResearchScanRun {
    private String id;
    private LocalDate scanDate;
    private String membershipKey;
    private ResearchScanStatus status;
    private int processedStocks;
    private int totalStocks;
    private String message;
    private Instant startedAt;
    private Instant finishedAt;
    private WatchlistResearchSnapshot snapshot;
}
