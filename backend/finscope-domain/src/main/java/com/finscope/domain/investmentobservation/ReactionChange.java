package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.finscope.common.enums.investmentobservation.ReactionChangeType;

@Data
public class ReactionChange {
    private Long id;
    private Long sampleId;
    private String eventKey;
    private String title;
    private String instrumentName;
    private ReactionChangeType changeType;
    private LocalDate tradeDate;
    private LocalDateTime detectedAt;
    private String summary;
    private boolean followed;
}
