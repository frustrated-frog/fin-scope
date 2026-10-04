package com.finscope.domain.quant.overnight;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class OvernightAutomationState {
    private boolean enabled;
    private int candidateLimit;
    private String serverTime;
    private boolean calendarAvailable;
    private boolean tradingDay;
    private String nextTailAt;
    private String ledgerReceivedAt;
    private boolean ledgerFresh;
    private int positionCount;
    private String holdingStatus;
    private Map<String, Object> heartbeat;
    private List<Map<String, Object>> jobs;
}
