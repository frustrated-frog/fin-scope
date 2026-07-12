package com.finscope.web.request.strategy;

import lombok.Data;

@Data
public class AddStrategyHoldingRequest {
    private String code;
    private String type;
    private String role;
    private double targetWeight;
    private double currentWeight;
    private String note;
}
