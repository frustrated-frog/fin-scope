package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.time.LocalDate;
import java.math.BigDecimal;
import com.finscope.common.enums.investmentobservation.ReactionWindowStatus;

@Data
public class ReactionWindow {
    private int sessions;
    private LocalDate endDate;
    private ReactionWindowStatus status;
    private BigDecimal stockReturnPct;
    private BigDecimal benchmarkReturnPct;
    private BigDecimal relativeReturnPp;
}
