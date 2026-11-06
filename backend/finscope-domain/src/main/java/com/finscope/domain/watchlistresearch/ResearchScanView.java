package com.finscope.domain.watchlistresearch;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ResearchScanView {
    private ResearchScanRun active;
    private ResearchScanRun latest;
    private ResearchScanRun lastAttempt;
    private List<ResearchScanRun> history = new ArrayList<ResearchScanRun>();
}
