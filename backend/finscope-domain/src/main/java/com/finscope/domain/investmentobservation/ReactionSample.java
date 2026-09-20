package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;

@Data
public class ReactionSample {
    private com.finscope.common.enums.investmentobservation.ReactionEventSubtype eventSubtype =
            com.finscope.common.enums.investmentobservation.ReactionEventSubtype.UNCLASSIFIED;
    private String ruleVersion;
    private String ruleEvidence;
    private String fact;
    private boolean followed;
    private Long id;
    private Long majorEventId;
    private String sourceIdentity;
    private boolean automatic;
    private String discoveryIssue;
    private LocalDateTime enrichmentAttemptAt;
    private String sourceOriginType;
    private String sourceOriginKey;
    private String title;
    private String summary;
    private String sourceUrl;
    private LocalDate occurredDate;
    private LocalDateTime firstCapturedAt;
    private LocalDateTime registeredAt;
    private String instrumentCode = "";
    private String instrumentName;
    private ReactionEventType eventType;
    private LocalDateTime publishedAt;
    private String relationNote;
    private ReactionSampleState state = ReactionSampleState.DRAFT;
    private boolean historicalBackfill;
    private int revision;
    private ReactionCalculation calculation;
    private LocalDateTime lastAttemptAt;
    private String refreshError;
}
