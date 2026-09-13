package com.finscope.domain.desktopths;

import com.finscope.common.enums.desktopths.ThsCaptureStatus;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** One manual desktop observation; quote time is intentionally not inferred. */
@Data
public class ThsSnapshot {
    private ThsCaptureStatus status;
    private String message;
    private String source;
    private String capturedAt;
    private String dataDate;
    private String stockCode;
    private String stockName;
    private String price;
    private String changePct;
    private String netBuy;
    private String reason;
    private List<ThsField> fields = new ArrayList<>();
    private List<String> buySeats = new ArrayList<>();
    private List<String> sellSeats = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
