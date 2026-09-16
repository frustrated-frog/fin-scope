package com.finscope.web.request.quant;

import com.finscope.common.enums.overnight.OvernightMode;
import com.finscope.domain.quant.overnight.OvernightResearchInput;
import lombok.Data;
import java.time.LocalDate;

@Data
public class OvernightResearchRequest {
    private String instrumentCode;
    private LocalDate signalDate;
    private OvernightMode mode;
    private String cutoff;
    private double costBps = 20;

    public OvernightResearchInput toInput() {
        OvernightResearchInput input = new OvernightResearchInput();
        input.setInstrumentCode(instrumentCode);
        input.setSignalDate(signalDate);
        input.setMode(mode);
        input.setCutoff(cutoff);
        input.setCostBps(costBps);
        return input;
    }
}
