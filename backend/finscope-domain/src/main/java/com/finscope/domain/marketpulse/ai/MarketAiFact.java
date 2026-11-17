package com.finscope.domain.marketpulse.ai;

import lombok.Data;

@Data
public class MarketAiFact {
    private String id;
    private String label;
    private String value;
    private String businessDate;
    private String sectorCode;
}
