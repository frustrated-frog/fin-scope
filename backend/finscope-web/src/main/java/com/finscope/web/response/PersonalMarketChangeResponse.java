package com.finscope.web.response;

import com.finscope.domain.marketpulse.PersonalMarketChange;
import lombok.Data;

@Data
public class PersonalMarketChangeResponse {
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

    public static PersonalMarketChangeResponse of(PersonalMarketChange value) {
        var response = new PersonalMarketChangeResponse();
        response.setId(value.getId());
        response.setCategory(value.getCategory());
        response.setCode(value.getCode());
        response.setName(value.getName());
        response.setTitle(value.getTitle());
        response.setSummary(value.getSummary());
        response.setOccurredAt(value.getOccurredAt());
        response.setReason(value.getReason());
        response.setNextWatch(value.getNextWatch());
        response.setEventKey(value.getEventKey());
        response.setSampleId(value.getSampleId());
        response.setReportId(value.getReportId());
        return response;
    }
}
