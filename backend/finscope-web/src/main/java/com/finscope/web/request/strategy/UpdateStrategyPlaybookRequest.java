package com.finscope.web.request.strategy;

import lombok.Data;

@Data
public class UpdateStrategyPlaybookRequest {
    private String status;
    private String note;
    private long revision;
}
