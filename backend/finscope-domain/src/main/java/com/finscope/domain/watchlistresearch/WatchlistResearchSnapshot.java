package com.finscope.domain.watchlistresearch;

import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class WatchlistResearchSnapshot {
    public static final String METHOD_VERSION = "watchlist-research-v1";
    private String methodVersion = METHOD_VERSION;
    private String benchmarkName = "沪深300";
    private LocalDate marketDate;
    private Instant observedAt;
    private int totalStocks;
    private int marketCovered;
    private int financialCovered;
    private List<StockStrengthAnalysis> strengths = new ArrayList<StockStrengthAnalysis>();
    private List<StockRelationship> relationships = new ArrayList<StockRelationship>();
    private List<StockGrowthQuality> growthQuality = new ArrayList<StockGrowthQuality>();
    private List<ResearchFinding> findings = new ArrayList<ResearchFinding>();
    private List<String> gaps = new ArrayList<String>();
}
