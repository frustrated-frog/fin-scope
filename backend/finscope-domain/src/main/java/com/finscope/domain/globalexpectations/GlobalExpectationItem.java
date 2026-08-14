package com.finscope.domain.globalexpectations;

import lombok.Data;

@Data
public class GlobalExpectationItem {
    private Long id;
    private String theme;
    private String question;
    private String marketUrl;
    private Integer probability;
    private Double change24h;
    private Double volume;
    private Double openInterest;
    private Integer spread;
    private String endDate;
    private String observation;
    private String status;
    private String observedAt;
}
