package com.finscope.web.response;

import com.finscope.domain.instrument.DailyBarHistory;
import com.finscope.domain.instrument.DailyBarPoint;
import com.finscope.domain.instrument.StockMarketHistory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/** 股票展示专用响应，与行情领域对象隔离。 */
@Data
public class StockMarketHistoryResponse {
    private HistoryResponse stock;
    private HistoryResponse benchmark;
    private String benchmarkCode;
    private String benchmarkName;
    private List<String> warnings;

    public static StockMarketHistoryResponse of(StockMarketHistory history) {
        StockMarketHistoryResponse response = new StockMarketHistoryResponse();
        response.setStock(HistoryResponse.of(history.getStock()));
        response.setBenchmark(HistoryResponse.of(history.getBenchmark()));
        response.setBenchmarkCode(history.getBenchmarkCode());
        response.setBenchmarkName(history.getBenchmarkName());
        response.setWarnings(history.getWarnings());
        return response;
    }

    @Data
    public static class HistoryResponse {
        private String code;
        private String market;
        private String sourceCode;
        private String qualityStatus;
        private String asOf;
        private String retrievedAt;
        private List<BarResponse> bars;
        private List<String> warnings;

        private static HistoryResponse of(DailyBarHistory history) {
            if (history == null) {
                return null;
            }
            HistoryResponse response = new HistoryResponse();
            response.setCode(history.getCode());
            response.setMarket(history.getMarket());
            response.setSourceCode(history.getSourceCode());
            response.setQualityStatus(history.getQualityStatus());
            response.setAsOf(history.getAsOf());
            response.setRetrievedAt(history.getRetrievedAt());
            response.setWarnings(history.getWarnings());
            response.setBars(history.getBars().stream().map(BarResponse::of).collect(Collectors.toList()));
            return response;
        }
    }

    @Data
    public static class BarResponse {
        private String code;
        private String market;
        private LocalDate tradeDate;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
        private BigDecimal amount;
        private BigDecimal amplitude;
        private BigDecimal changePct;
        private BigDecimal turnoverRate;
        private String adjustment;
        private Boolean isComplete;

        private static BarResponse of(DailyBarPoint bar) {
            BarResponse response = new BarResponse();
            response.setCode(bar.getCode());
            response.setMarket(bar.getMarket());
            response.setTradeDate(bar.getTradeDate());
            response.setOpen(bar.getOpen());
            response.setHigh(bar.getHigh());
            response.setLow(bar.getLow());
            response.setClose(bar.getClose());
            response.setVolume(bar.getVolume());
            response.setAmount(bar.getAmount());
            response.setAmplitude(bar.getAmplitude());
            response.setChangePct(bar.getChangePct());
            response.setTurnoverRate(bar.getTurnoverRate());
            response.setAdjustment(bar.getAdjustment());
            response.setIsComplete(bar.getIsComplete());
            return response;
        }
    }
}
