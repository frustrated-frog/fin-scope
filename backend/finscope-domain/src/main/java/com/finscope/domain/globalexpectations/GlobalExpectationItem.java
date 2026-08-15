package com.finscope.domain.globalexpectations;

import lombok.Data;

import java.util.List;

@Data
public class GlobalExpectationItem {
    private Long id;
    private String theme;
    private String question;
    private String marketUrl;
    private Integer probability;
    private Double change5m;
    private Double change1h;
    private Double change24h;
    private Double volume;
    private Double volume24h;
    private Double openInterest;
    private Integer spread;
    private String endDate;
    private String observation;
    private String status;
    private String dataStatus;
    private String observedAt;
    private String lastRefreshAt;
    private List<GlobalExpectationPricePoint> priceHistory;
}
