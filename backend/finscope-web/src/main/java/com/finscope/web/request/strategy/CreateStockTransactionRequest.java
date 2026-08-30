package com.finscope.web.request.strategy;

import com.finscope.domain.strategy.holding.StockTransaction;
import com.finscope.domain.strategy.holding.StockTransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateStockTransactionRequest {
    private String clientRequestId;
    private String code;
    private StockTransactionType type;
    private LocalDate tradeDate;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal commission;
    private BigDecimal stampDuty;
    private BigDecimal transferFee;
    private BigDecimal otherFee;
    private BigDecimal cashAmount;
    private String note;

    public StockTransaction toTransaction() {
        StockTransaction value = new StockTransaction();
        value.setClientRequestId(clientRequestId);
        value.setType(type);
        value.setTradeDate(tradeDate);
        value.setQuantity(quantity);
        value.setPrice(price);
        value.setCommission(commission);
        value.setStampDuty(stampDuty);
        value.setTransferFee(transferFee);
        value.setOtherFee(otherFee);
        value.setCashAmount(cashAmount);
        value.setNote(note);
        return value;
    }
}
