package com.finscope.web.request.strategy;

import lombok.Data;

@Data
public class UpdateStrategyHoldingRequest {
    private String role;
    private double targetWeight;
    private double currentWeight;
    private String note;
    private long revision;
}
