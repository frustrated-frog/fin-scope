package com.finscope.web.request;

import lombok.Data;

@Data
public class StartAttributionRequest {
    private String code;
    private String type;
    private String name;
    private Double changePct;
    /** 归因对应的行情交易日，yyyy-MM-dd。 */
    private String quoteDate;
}
