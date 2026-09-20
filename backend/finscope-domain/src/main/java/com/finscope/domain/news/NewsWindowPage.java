package com.finscope.domain.news;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class NewsWindowPage {
    private List<NewsReport> items;
    private long total;
    private long asOfSequence;
    private String asOfTime;
    private int page;
    private int size;
    private List<String> sources;
    private Map<String, Integer> categoryCounts;
    private List<NewsSourceHealth> sourceHealth;
}
