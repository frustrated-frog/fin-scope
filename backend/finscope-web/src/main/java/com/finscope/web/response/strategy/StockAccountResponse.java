package com.finscope.web.response.strategy;

import com.finscope.domain.strategy.holding.StockAccountSnapshot;
import com.finscope.domain.strategy.holding.StockPosition;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class StockAccountResponse {
    private BigDecimal cash;
    private BigDecimal marketValue;
    private BigDecimal totalEquity;
    private BigDecimal realizedProfit;
    private BigDecimal unrealizedProfit;
    private BigDecimal dividendIncome;
    private BigDecimal totalProfit;
    private BigDecimal concentration;
    private LocalDateTime calculatedAt;
    private List<PositionResponse> positions = new ArrayList<PositionResponse>();

    public static StockAccountResponse of(StockAccountSnapshot value) {
        StockAccountResponse response = new StockAccountResponse();
        response.cash = value.getCash();
        response.marketValue = value.getMarketValue();
        response.totalEquity = value.getTotalEquity();
        response.realizedProfit = value.getRealizedProfit();
        response.unrealizedProfit = value.getUnrealizedProfit();
        response.dividendIncome = value.getDividendIncome();
        response.totalProfit = value.getTotalProfit();
        response.concentration = value.getConcentration();
        response.calculatedAt = value.getCalculatedAt();
        for (StockPosition position : value.getPositions()) {
            response.positions.add(PositionResponse.of(position));
        }
        return response;
    }

    @Data
    public static class PositionResponse {
        private String instrumentCode;
        private String instrumentName;
        private BigDecimal quantity;
        private BigDecimal totalCost;
        private BigDecimal averageCost;
        private BigDecimal lastPrice;
        private LocalDate quoteDate;
        private String quoteQuality;
        private BigDecimal marketValue;
        private BigDecimal realizedProfit;
        private BigDecimal unrealizedProfit;
        private BigDecimal dividendIncome;
        private BigDecimal totalProfit;
        private BigDecimal weight;

        private static PositionResponse of(StockPosition value) {
            PositionResponse response = new PositionResponse();
            response.instrumentCode = value.getInstrumentCode();
            response.instrumentName = value.getInstrumentName();
            response.quantity = value.getQuantity();
            response.totalCost = value.getTotalCost();
            response.averageCost = value.getAverageCost();
            response.lastPrice = value.getLastPrice();
            response.quoteDate = value.getQuoteDate();
            response.quoteQuality = value.getQuoteQuality();
            response.marketValue = value.getMarketValue();
            response.realizedProfit = value.getRealizedProfit();
            response.unrealizedProfit = value.getUnrealizedProfit();
            response.dividendIncome = value.getDividendIncome();
            response.totalProfit = value.getTotalProfit();
            response.weight = value.getWeight();
            return response;
        }
    }
}
