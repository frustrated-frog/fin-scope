package com.finscope.web.response.strategy;

import com.finscope.domain.strategy.StrategyHolding;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StrategyHoldingResponse {
    private Long id; private Long instrumentId; private String code; private String type; private String name; private String role;
    private double targetWeight; private double currentWeight; private String note; private long revision; private LocalDateTime updatedAt;
    public static StrategyHoldingResponse of(StrategyHolding value){StrategyHoldingResponse r=new StrategyHoldingResponse();r.id=value.getId();r.instrumentId=value.getInstrumentId();r.code=value.getCode();r.type=value.getType();r.name=value.getName();r.role=value.getRole();r.targetWeight=value.getTargetWeight();r.currentWeight=value.getCurrentWeight();r.note=value.getNote();r.revision=value.getRevision();r.updatedAt=value.getUpdatedAt();return r;}
}
