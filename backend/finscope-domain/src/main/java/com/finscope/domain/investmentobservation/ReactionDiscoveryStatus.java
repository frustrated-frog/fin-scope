package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReactionDiscoveryStatus {
    private boolean running;
    private LocalDateTime lastCompletedAt;
    private int captured;
    private int resolved;
    private String message = "自动发现尚未执行";
}
