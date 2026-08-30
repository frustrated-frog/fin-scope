package com.finscope.web.response.strategy;

import com.finscope.domain.strategy.holding.StockTransaction;
import com.finscope.domain.strategy.holding.StockTransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StockTransactionResponse {
    private Long id;
    private String clientRequestId;
    private String instrumentCode;
    private String instrumentName;
    private StockTransactionType type;
    private LocalDate tradeDate;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalFees;
    private BigDecimal cashAmount;
    private Long reversalOfId;
    private String note;
    private LocalDateTime createdAt;

    public static StockTransactionResponse of(StockTransaction value) {
        StockTransactionResponse response = new StockTransactionResponse();
        response.id = value.getId();
        response.clientRequestId = value.getClientRequestId();
        response.instrumentCode = value.getInstrumentCode();
        response.instrumentName = value.getInstrumentName();
        response.type = value.getType();
        response.tradeDate = value.getTradeDate();
        response.quantity = value.getQuantity();
        response.price = value.getPrice();
        response.totalFees = value.totalFees();
        response.cashAmount = value.getCashAmount();
        response.reversalOfId = value.getReversalOfId();
        response.note = value.getNote();
        response.createdAt = value.getCreatedAt();
        return response;
    }
}
