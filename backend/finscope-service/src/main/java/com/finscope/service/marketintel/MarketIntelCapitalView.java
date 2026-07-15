package com.finscope.service.marketintel;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalBehaviorEvaluation;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import com.finscope.domain.marketintel.CapitalFactorObservation;
import com.finscope.domain.marketintel.CapitalWatchCondition;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class MarketIntelCapitalView {
    private Instrument instrument;
    private CapitalBehaviorSnapshot snapshot;
    private List<CapitalFlowPoint> intradayTimeline = Collections.emptyList();
    private List<CapitalFlowPoint> dailyTrend = Collections.emptyList();
    private CapitalBehaviorMetrics metrics;
    private CapitalRuleExplanation ruleExplanation;
    private CapitalBehaviorEvaluation historicalEvaluation;
    private List<CapitalFactorObservation> factorObservations = Collections.emptyList();
    private List<CapitalWatchCondition> watchConditions = Collections.emptyList();
    private String factorVersion;
    private String signalVersion;
    private Health health;

    @Data
    public static class Health {
        private String status;
        private LocalDateTime asOf;
        private String providerCode;
        private List<String> warnings = Collections.emptyList();
    }
}
