package com.finscope.web.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateMajorEventRequest {
    private LocalDate occurredDate;
    private String note;
}
