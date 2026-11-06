package com.finscope.web.response;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.StockFinancialQuarter;
import com.finscope.domain.instrument.StockFundamentals;
import com.finscope.domain.valuation.StockValuationView;
import com.finscope.domain.valuation.StockValuationPeer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** 与持久化报告隔离的股票基本面展示契约，估值沿用现有视图。 */
@Data
public class StockFundamentalsResponse {
    private InstrumentResponse instrument;
    private List<QuarterResponse> quarters;
    private StockValuationView valuation;
    private List<PeerResponse> peers;
    private String peerComparisonNote;
    private List<String> warnings;
    private Instant refreshedAt;

    public static StockFundamentalsResponse of(StockFundamentals source) {
        StockFundamentalsResponse result = new StockFundamentalsResponse();
        result.setInstrument(InstrumentResponse.of(source.getInstrument()));
        result.setQuarters(source.getQuarters().stream()
                .map(QuarterResponse::of).collect(Collectors.toList()));
        result.setValuation(source.getValuation());
        result.setPeers(source.getPeers().stream().map(PeerResponse::of).collect(Collectors.toList()));
        result.setPeerComparisonNote(source.getPeerComparisonNote());
        result.setWarnings(source.getWarnings());
        result.setRefreshedAt(source.getRefreshedAt());
        return result;
    }

    @Data
    public static class PeerResponse {
        private String code;
        private String name;
        private LocalDate observedDate;
        private String sourceCode;
        private String qualityStatus;
        private BigDecimal peTtm;
        private BigDecimal pbMrq;

        private static PeerResponse of(StockValuationPeer source) {
            PeerResponse result = new PeerResponse();
            result.setCode(source.getCode());
            result.setName(source.getName());
            result.setObservedDate(source.getObservedDate());
            result.setSourceCode(source.getSourceCode());
            result.setQualityStatus(source.getQualityStatus());
            result.setPeTtm(source.getPeTtm());
            result.setPbMrq(source.getPbMrq());
            return result;
        }
    }

    @Data
    public static class InstrumentResponse {
        private Long id;
        private String code;
        private String name;
        private String market;
        private String type;

        private static InstrumentResponse of(Instrument source) {
            InstrumentResponse result = new InstrumentResponse();
            result.setId(source.getId());
            result.setCode(source.getCode());
            result.setName(source.getName());
            result.setMarket(source.getMarket());
            result.setType(source.getType());
            return result;
        }
    }

    @Data
    public static class QuarterResponse {
        private LocalDate periodEnd;
        private FinancialReportType reportType;
        private String currency;
        private Long reportId;
        private String sourceCode;
        private FinancialQualityStatus qualityStatus;
        private LocalDateTime publishedAt;
        private BigDecimal revenue;
        private BigDecimal netProfitParent;
        private BigDecimal operatingCashFlow;
        private BigDecimal grossMarginPct;
        private List<String> dataGaps;

        private static QuarterResponse of(StockFinancialQuarter source) {
            QuarterResponse result = new QuarterResponse();
            result.setPeriodEnd(source.getPeriodEnd());
            result.setReportType(source.getReportType());
            result.setCurrency(source.getCurrency());
            result.setReportId(source.getReportId());
            result.setSourceCode(source.getSourceCode());
            result.setQualityStatus(source.getQualityStatus());
            result.setPublishedAt(source.getPublishedAt());
            result.setRevenue(source.getRevenue());
            result.setNetProfitParent(source.getNetProfitParent());
            result.setOperatingCashFlow(source.getOperatingCashFlow());
            result.setGrossMarginPct(source.getGrossMarginPct());
            result.setDataGaps(source.getDataGaps());
            return result;
        }
    }
}
