package com.finscope.web.request;

import lombok.Data;

@Data
public class StartAttributionRequest {
    private String code;
    private String type;
    private String name;
    private Double changePct;
    /** 归因目标日期，yyyy-MM-dd；股票仅支持北京时间今天、昨天、前天，省略时为今天。 */
    private String quoteDate;
}
