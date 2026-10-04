package com.finscope.domain.quant.overnight;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class OvernightAutomationContext {
    private boolean enabled;
    private int candidateLimit;
    private double tailCostBps;
    private double holdingCostBps;
    private List<OvernightLedgerPosition> positions = new ArrayList<>();
}
