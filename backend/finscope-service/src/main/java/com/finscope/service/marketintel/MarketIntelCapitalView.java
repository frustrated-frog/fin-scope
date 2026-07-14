package com.finscope.service.marketintel;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalRuleExplanation;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class MarketIntelCapitalView {
    private Instrument instrument;
    private CapitalBehaviorSnapshot snapshot;
    private List<CapitalFlowPoint> intradayTimeline= Collections.emptyList();
    private List<CapitalFlowPoint> dailyTrend=Collections.emptyList();
    private CapitalBehaviorMetrics metrics;
    private CapitalRuleExplanation ruleExplanation;
    private Health health;
    @Data public static class Health{private String status;private LocalDateTime asOf;private String providerCode;private List<String> warnings=Collections.emptyList();}
}
