package com.finscope.domain.quant.overnight;

import com.finscope.common.enums.overnight.OvernightMode;
import lombok.Data;
import java.time.LocalDate;

@Data
public class OvernightResearchInput {
    private String instrumentCode;
    private LocalDate signalDate;
    private OvernightMode mode;
    private String cutoff;
    private double costBps = 20;
    private Double costBasis;
    private Double quantity;
    private LocalDate positionOpenedOn;
}
