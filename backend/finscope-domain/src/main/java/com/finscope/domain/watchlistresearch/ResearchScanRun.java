package com.finscope.domain.watchlistresearch;

import com.finscope.common.enums.watchlistresearch.ResearchScanStatus;
import com.finscope.common.enums.watchlistresearch.ResearchScanStage;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;

@Data
public class ResearchScanRun {
    private String id;
    private LocalDate scanDate;
    private String membershipKey;
    private ResearchScanStatus status;
    private ResearchScanStage stage;
    /** 当前阶段的已处理股票数；跨阶段重新计数。 */
    private int processedStocks;
    private int totalStocks;
    private String message;
    private Instant startedAt;
    private Instant finishedAt;
    private WatchlistResearchSnapshot snapshot;
}
