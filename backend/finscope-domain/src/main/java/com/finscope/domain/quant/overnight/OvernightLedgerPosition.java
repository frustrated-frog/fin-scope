package com.finscope.domain.quant.overnight;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 自动研判使用的账本快照，不包含实时估值或外部行情。 */
@Data
public class OvernightLedgerPosition {
    private String instrumentCode;
    private String instrumentName;
    private BigDecimal quantity;
    private BigDecimal averageCost;
    private LocalDate openedOn;
}
