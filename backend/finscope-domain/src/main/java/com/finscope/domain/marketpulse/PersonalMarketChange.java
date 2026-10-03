package com.finscope.domain.marketpulse;

import lombok.Data;

@Data
public class PersonalMarketChange {
    private String id;
    private com.finscope.common.enums.marketpulse.PersonalChangeCategory category;
    private String code;
    private String name;
    private String title;
    private String summary;
    private String occurredAt;
    private String reason;
    private String nextWatch;
    private String eventKey;
    private Long sampleId;
    private Long reportId;
}
