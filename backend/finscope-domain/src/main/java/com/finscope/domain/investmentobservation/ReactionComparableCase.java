package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReactionComparableCase {
    private Long sampleId;
    private String eventKey;
    private String title;
    private String instrumentCode;
    private String instrumentName;
    private LocalDateTime publishedAt;
    private String matchReason;
    private ReactionCalculation calculation;
}
