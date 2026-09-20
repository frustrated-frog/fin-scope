package com.finscope.domain.news;

import lombok.Data;

@Data
public class NewsSavedFilter {
    private String id;
    private String name;
    private NewsWindowQuery query;
    private long unreadCount;
}
