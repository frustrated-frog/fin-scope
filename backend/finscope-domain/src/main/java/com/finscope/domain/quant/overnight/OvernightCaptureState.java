package com.finscope.domain.quant.overnight;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class OvernightCaptureState {
    private OvernightCapturePlan plan;
    private List<String> slots;
    private List<Map<String, Object>> runs;
    private String serverTime;
    private boolean calendarAvailable;
}
