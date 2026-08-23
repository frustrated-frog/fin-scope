package com.finscope.domain.marketpulse;

import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 由冻结市场事实确定性生成的每日收盘复盘。 */
@Data
public class DailyMarketReview {
    private LocalDate businessDate;
    private String headline;
    private String indexOverview;
    private String breadthConclusion;
    private List<String> leadingSectors = new ArrayList<>();
    private List<String> weakeningSectors = new ArrayList<>();
    private List<String> confirmedEvents = new ArrayList<>();
    private List<String> riskSignals = new ArrayList<>();
    private List<String> nextSessionWatchlist = new ArrayList<>();
    private List<String> evidence = new ArrayList<>();
    private MarketPulseQualityStatus qualityStatus;
    private String sourceFingerprint;
    private LocalDateTime generatedAt;
}
