package com.finscope.domain.quant.overnight;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class OvernightCapturePlan {
    private boolean enabled;
    private List<String> instrumentCodes = new ArrayList<>();
    private double costBps = 20;
    private String enabledSince;
    private String updatedAt;
}
