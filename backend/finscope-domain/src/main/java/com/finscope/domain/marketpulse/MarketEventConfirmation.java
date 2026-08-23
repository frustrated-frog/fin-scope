package com.finscope.domain.marketpulse;

import com.finscope.common.enums.marketpulse.MarketEventConfirmationState;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MarketEventConfirmation {
    private Long radarEventId;
    private String title;
    private String sectorCode;
    private String sectorName;
    private String mappingSource;
    private int mappingConfidence;
    private int eventScore;
    private int marketReactionScore;
    private boolean eligibleForRanking;
    private MarketEventConfirmationState confirmationState;
    private List<String> evidence = new ArrayList<>();
}
