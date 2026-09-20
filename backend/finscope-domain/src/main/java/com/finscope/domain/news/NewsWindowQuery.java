package com.finscope.domain.news;

import lombok.Data;

/** 单用户工作台共享筛选契约；关键词按字面子串匹配。 */
@Data
public class NewsWindowQuery {
    private String query = "";
    private String exclude = "";
    private String source = "ALL";
    private String category = "ALL";
    private String kind = "ALL";
    private boolean unreadOnly;
    private int hours = 36;
    private int page = 0;
    private int size = 50;
    private long asOfSequence;
    private String asOfTime;
}
