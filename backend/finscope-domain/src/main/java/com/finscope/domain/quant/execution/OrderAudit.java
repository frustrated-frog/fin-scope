package com.finscope.domain.quant.execution;

import lombok.Data;

/** Frozen replay contract; times use Asia/Shanghai. */
@Data
public class OrderAudit {
    private java.time.LocalDate signalDate;
    private java.time.LocalDate tradeDate;
    private String instrumentCode;
    private com.finscope.common.enums.quant.ReplayOrderSide side;
    private long requestedQuantity;
    private long filledQuantity;
    private double fee;
    private com.finscope.common.enums.quant.ReplayOrderReason reason;
}
