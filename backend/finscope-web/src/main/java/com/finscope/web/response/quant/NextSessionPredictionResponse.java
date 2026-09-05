package com.finscope.web.response.quant;

import com.finscope.common.enums.quant.NextSessionOutcomeStatus;
import com.finscope.domain.quant.forecast.NextSessionPrediction;
import com.finscope.domain.quant.forecast.NextSessionPredictionRecord;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NextSessionPredictionResponse {
    private Long id;
    private String instrumentCode;
    private NextSessionPrediction prediction;
    private NextSessionOutcomeStatus status;
    private Double actualReturn;
    private Boolean correct;
    private Boolean intervalCovered;
    private LocalDateTime settledAt;
    private String outcomeNote;

    public static NextSessionPredictionResponse of(NextSessionPredictionRecord record) {
        NextSessionPredictionResponse value = new NextSessionPredictionResponse();
        value.id = record.getId();
        value.instrumentCode = record.getInstrumentCode();
        value.prediction = record.getPrediction();
        value.status = record.getStatus();
        value.actualReturn = record.getActualReturn();
        value.correct = record.getCorrect();
        value.intervalCovered = record.getIntervalCovered();
        value.settledAt = record.getSettledAt();
        value.outcomeNote = record.getOutcomeNote();
        return value;
    }
}
