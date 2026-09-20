package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.time.LocalDate;
import com.finscope.common.enums.investmentobservation.ReactionEventType;

@Data
public class ReactionCandidate {
    private Long majorEventId;
    private String title;
    private String summary;
    private String sourceUrl;
    private LocalDate occurredDate;
    private ReactionEventType suggestedType;
}
