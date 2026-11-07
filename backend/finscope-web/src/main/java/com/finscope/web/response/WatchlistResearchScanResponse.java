package com.finscope.web.response;

import com.finscope.common.enums.watchlistresearch.ResearchScanStatus;
import com.finscope.common.enums.watchlistresearch.ResearchScanStage;
import com.finscope.common.enums.watchlistresearch.ResearchSignalKind;
import com.finscope.domain.watchlistresearch.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/** 扫描HTTP契约，不暴露持久化键和内部排序权重。 */
@Data
public class WatchlistResearchScanResponse {
    private RunResponse active;
    private RunResponse latest;
    private RunResponse lastAttempt;
    private List<RunResponse> history;

    public static WatchlistResearchScanResponse of(ResearchScanView source) {
        WatchlistResearchScanResponse result = new WatchlistResearchScanResponse();
        result.setActive(RunResponse.of(source.getActive()));
        result.setLatest(RunResponse.of(source.getLatest()));
        result.setLastAttempt(RunResponse.of(source.getLastAttempt()));
        result.setHistory(source.getHistory().stream().map(RunResponse::of).collect(Collectors.toList()));
        return result;
    }

    @Data
    public static class RunResponse {
        private String id;
        private LocalDate scanDate;
        private ResearchScanStatus status;
        private ResearchScanStage stage;
        private int processedStocks;
        private int totalStocks;
        private String message;
        private Instant startedAt;
        private Instant finishedAt;
        private SnapshotResponse snapshot;

        public static RunResponse of(ResearchScanRun source) {
            if (source == null) {
                return null;
            }
            RunResponse result = new RunResponse();
            result.setId(source.getId());
            result.setScanDate(source.getScanDate());
            result.setStatus(source.getStatus());
            result.setStage(source.getStage());
            result.setProcessedStocks(source.getProcessedStocks());
            result.setTotalStocks(source.getTotalStocks());
            result.setMessage(source.getMessage());
            result.setStartedAt(source.getStartedAt());
            result.setFinishedAt(source.getFinishedAt());
            result.setSnapshot(SnapshotResponse.of(source.getSnapshot()));
            return result;
        }
    }

    @Data
    public static class SnapshotResponse {
        private String methodVersion;
        private String benchmarkName;
        private LocalDate marketDate;
        private Instant observedAt;
        private int totalStocks;
        private int marketCovered;
        private int financialCovered;
        private List<StrengthResponse> strengths;
        private List<RelationshipResponse> relationships;
        private List<GrowthResponse> growthQuality;
        private List<FindingResponse> findings;
        private List<String> gaps;

        public static SnapshotResponse of(WatchlistResearchSnapshot source) {
            if (source == null) {
                return null;
            }
            SnapshotResponse result = new SnapshotResponse();
            result.setMethodVersion(source.getMethodVersion());
            result.setBenchmarkName(source.getBenchmarkName());
            result.setMarketDate(source.getMarketDate());
            result.setObservedAt(source.getObservedAt());
            result.setTotalStocks(source.getTotalStocks());
            result.setMarketCovered(source.getMarketCovered());
            result.setFinancialCovered(source.getFinancialCovered());
            result.setStrengths(source.getStrengths().stream().map(StrengthResponse::of).collect(Collectors.toList()));
            result.setRelationships(source.getRelationships().stream().map(RelationshipResponse::of).collect(Collectors.toList()));
            result.setGrowthQuality(source.getGrowthQuality().stream().map(GrowthResponse::of).collect(Collectors.toList()));
            result.setFindings(source.getFindings().stream().map(FindingResponse::of).collect(Collectors.toList()));
            result.setGaps(source.getGaps());
            return result;
        }
    }

    @Data
    public static class StrengthResponse {
        private String code;
        private String name;
        private String sourceCode;
        private LocalDate trainingFrom;
        private LocalDate trainingThrough;
        private LocalDate evaluatedFrom;
        private LocalDate evaluatedThrough;
        private int trainingSamples;
        private int evaluationSamples;
        private Double beta;
        private Double fitQuality;
        private Double actualReturnPct;
        private Double baselineReturnPct;
        private Double excessReturnPp;
        private int positiveResidualDays;
        private List<StrengthPointResponse> returnPath;
        private List<String> gaps;

        public static StrengthResponse of(StockStrengthAnalysis source) {
            if (source == null) {
                return null;
            }
            StrengthResponse result = new StrengthResponse();
            result.setCode(source.getCode());
            result.setName(source.getName());
            result.setSourceCode(source.getSourceCode());
            result.setTrainingFrom(source.getTrainingFrom());
            result.setTrainingThrough(source.getTrainingThrough());
            result.setEvaluatedFrom(source.getEvaluatedFrom());
            result.setEvaluatedThrough(source.getEvaluatedThrough());
            result.setTrainingSamples(source.getTrainingSamples());
            result.setEvaluationSamples(source.getEvaluationSamples());
            result.setBeta(source.getBeta());
            result.setFitQuality(source.getFitQuality());
            result.setActualReturnPct(source.getActualReturnPct());
            result.setBaselineReturnPct(source.getBaselineReturnPct());
            result.setExcessReturnPp(source.getExcessReturnPp());
            result.setPositiveResidualDays(source.getPositiveResidualDays());
            result.setReturnPath(source.getReturnPath().stream().map(StrengthPointResponse::of).toList());
            result.setGaps(source.getGaps());
            return result;
        }
    }

    public record StrengthPointResponse(LocalDate date, double actualReturnPct, double baselineReturnPct) {
        private static StrengthPointResponse of(StockStrengthPoint source) {
            return new StrengthPointResponse(source.date(), source.actualReturnPct(), source.baselineReturnPct());
        }
    }

    @Data
    public static class RelationshipResponse {
        private String leftCode;
        private String leftName;
        private String rightCode;
        private String rightName;
        private LocalDate from;
        private LocalDate through;
        private int samples;
        private Double correlation;
        private Double residualCorrelation;
        private Double downsideCorrelation;
        private int downsideSamples;
        private boolean strong;

        public static RelationshipResponse of(StockRelationship source) {
            if (source == null) {
                return null;
            }
            RelationshipResponse result = new RelationshipResponse();
            result.setLeftCode(source.getLeftCode());
            result.setLeftName(source.getLeftName());
            result.setRightCode(source.getRightCode());
            result.setRightName(source.getRightName());
            result.setFrom(source.getFrom());
            result.setThrough(source.getThrough());
            result.setSamples(source.getSamples());
            result.setCorrelation(source.getCorrelation());
            result.setResidualCorrelation(source.getResidualCorrelation());
            result.setDownsideCorrelation(source.getDownsideCorrelation());
            result.setDownsideSamples(source.getDownsideSamples());
            result.setStrong(source.isStrong());
            return result;
        }
    }

    @Data
    public static class GrowthResponse {
        private String code;
        private String name;
        private String currency;
        private String sourceCode;
        private LocalDate periodFrom;
        private LocalDate periodThrough;
        private BigDecimal revenueTtm;
        private BigDecimal profitTtm;
        private BigDecimal operatingCashFlowTtm;
        private Double revenueGrowthPct;
        private Double profitGrowthPct;
        private Double cashConversion;
        private Double grossMarginPct;
        private Double grossMarginChangePp;
        private List<String> gaps;

        public static GrowthResponse of(StockGrowthQuality source) {
            if (source == null) {
                return null;
            }
            GrowthResponse result = new GrowthResponse();
            result.setCode(source.getCode());
            result.setName(source.getName());
            result.setCurrency(source.getCurrency());
            result.setSourceCode(source.getSourceCode());
            result.setPeriodFrom(source.getPeriodFrom());
            result.setPeriodThrough(source.getPeriodThrough());
            result.setRevenueTtm(source.getRevenueTtm());
            result.setProfitTtm(source.getProfitTtm());
            result.setOperatingCashFlowTtm(source.getOperatingCashFlowTtm());
            result.setRevenueGrowthPct(source.getRevenueGrowthPct());
            result.setProfitGrowthPct(source.getProfitGrowthPct());
            result.setCashConversion(source.getCashConversion());
            result.setGrossMarginPct(source.getGrossMarginPct());
            result.setGrossMarginChangePp(source.getGrossMarginChangePp());
            result.setGaps(source.getGaps());
            return result;
        }
    }

    @Data
    public static class FindingResponse {
        private String key;
        private ResearchSignalKind kind;
        private List<String> codes;
        private String title;
        private List<String> evidence;
        private boolean newlyObserved;

        public static FindingResponse of(ResearchFinding source) {
            if (source == null) {
                return null;
            }
            FindingResponse result = new FindingResponse();
            result.setKey(source.getKey());
            result.setKind(source.getKind());
            result.setCodes(source.getCodes());
            result.setTitle(source.getTitle());
            result.setEvidence(source.getEvidence());
            result.setNewlyObserved(source.isNewlyObserved());
            return result;
        }
    }
}
