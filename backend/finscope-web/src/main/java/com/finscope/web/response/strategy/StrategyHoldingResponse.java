package com.finscope.web.response.strategy;

import com.finscope.domain.strategy.StrategyHolding;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StrategyHoldingResponse {
    private Long id;
    private Long instrumentId;
    private String code;
    private String type;
    private String name;
    private String role;
    private double targetWeight;
    private double currentWeight;
    private Double quantity;
    private Double averageCost;
    private String note;
    private long revision;
    private LocalDateTime updatedAt;

    public static StrategyHoldingResponse of(StrategyHolding value) {
        StrategyHoldingResponse response = new StrategyHoldingResponse();
        response.id = value.getId();
        response.instrumentId = value.getInstrumentId();
        response.code = value.getCode();
        response.type = value.getType();
        response.name = value.getName();
        response.role = value.getRole();
        response.targetWeight = value.getTargetWeight();
        response.currentWeight = value.getCurrentWeight();
        response.quantity = value.getQuantity();
        response.averageCost = value.getAverageCost();
        response.note = value.getNote();
        response.revision = value.getRevision();
        response.updatedAt = value.getUpdatedAt();
        return response;
    }
}
