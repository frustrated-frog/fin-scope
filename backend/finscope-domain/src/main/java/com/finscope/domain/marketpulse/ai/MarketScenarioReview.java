package com.finscope.domain.marketpulse.ai;

import com.finscope.common.enums.marketpulse.MarketConditionStatus;
import lombok.Data;

@Data
public class MarketScenarioReview {
    private String sourceDate;
    private String title;
    private MarketConditionStatus confirmation;
    private MarketConditionStatus invalidation;
    private String confirmationDetail;
    private String invalidationDetail;
}
