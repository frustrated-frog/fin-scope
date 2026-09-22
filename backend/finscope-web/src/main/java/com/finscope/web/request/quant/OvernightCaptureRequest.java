package com.finscope.web.request.quant;

import com.finscope.domain.quant.overnight.OvernightCapturePlan;
import lombok.Data;
import java.util.List;

@Data
public class OvernightCaptureRequest {
    private boolean enabled;
    private List<String> instrumentCodes;
    private double costBps = 20;

    public OvernightCapturePlan toPlan() {
        OvernightCapturePlan plan = new OvernightCapturePlan();
        plan.setEnabled(enabled);
        plan.setInstrumentCodes(instrumentCodes);
        plan.setCostBps(costBps);
        return plan;
    }
}
