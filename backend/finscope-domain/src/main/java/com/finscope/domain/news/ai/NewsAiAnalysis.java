package com.finscope.domain.news.ai;

import lombok.Data;
import com.finscope.common.enums.news.NewsAiDisplayCategory;
import com.finscope.common.enums.news.NewsAiEventType;
import java.util.ArrayList;
import java.util.List;

/** 与事件候选无关，可跨来源复用的内容理解。 */
@Data
public class NewsAiAnalysis {
    private String categoryCode;
    private NewsAiDisplayCategory displayCategory;
    private NewsAiEventType eventType;
    private String title;
    private String summary;
    private List<String> topics = new ArrayList<>();
    private List<String> entities = new ArrayList<>();
    private List<NewsCompanyAction> companyActions = new ArrayList<>();
    private List<String> policyProducts = new ArrayList<>();
    private List<String> facts = new ArrayList<>();
}
