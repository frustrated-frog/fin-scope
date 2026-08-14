package com.finscope.domain.quant.discovery;

import lombok.Data;

@Data
public class StockDiscoveryRequestedEvent {
    private String eventId;
    private int eventVersion = 1;
    private Long runId;
    private String runKey;
    private String businessDate;
    private double budget;
    private String policyVersion;
}
