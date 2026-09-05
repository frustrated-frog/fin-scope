package com.finscope.domain.quant.forecast;

import com.finscope.common.enums.quant.NextSessionOutcomeStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NextSessionPredictionRecord {
    private Long id;
    private String instrumentCode;
    private NextSessionPrediction prediction;
    private NextSessionOutcomeStatus status;
    private Double actualReturn;
    private Boolean correct;
    private Boolean intervalCovered;
    private LocalDateTime settledAt;
    private String outcomeNote;
}
