package com.finscope.domain.marketpulse.ai;

import com.finscope.common.enums.marketpulse.MarketAiFailureCode;
import lombok.Data;

@Data
public class MarketAiFailure {
    private MarketAiFailureCode code;
    private String path;
    private String errorType;
}
