package com.finscope.web.request.strategy;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReverseStockTransactionRequest {
    private String clientRequestId;
    private LocalDate tradeDate;
    private String note;
}
