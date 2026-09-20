package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.time.LocalDate;
import java.math.BigDecimal;
import com.finscope.common.enums.investmentobservation.ReactionWindowStatus;

@Data
public class ReactionPoint {
    private BigDecimal close;
    private BigDecimal adjustedClose;
    private BigDecimal volume;
    private BigDecimal amount;
    private int session;
    private LocalDate tradeDate;
    private BigDecimal stockReturnPct;
    private BigDecimal benchmarkReturnPct;
    private BigDecimal relativeReturnPp;
    private ReactionWindowStatus status;
}
