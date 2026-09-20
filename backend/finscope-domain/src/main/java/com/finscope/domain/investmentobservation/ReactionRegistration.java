package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.time.LocalDateTime;
import com.finscope.common.enums.investmentobservation.ReactionEventType;

@Data
public class ReactionRegistration {
    private String instrumentCode;
    private String instrumentName;
    private ReactionEventType eventType;
    private LocalDateTime publishedAt;
    private String relationNote;
    private int revision;
}
