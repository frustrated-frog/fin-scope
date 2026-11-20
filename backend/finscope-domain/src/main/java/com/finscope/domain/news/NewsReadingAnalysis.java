package com.finscope.domain.news;

import java.util.List;

/** 报道和雷达共用的解读草稿，区分事实、机制、意义、传导和条件情景。 */
public record NewsReadingAnalysis(String lead, List<NewsReading.Concept> concepts, NewsReading.Section change,
                                  NewsReading.Section mechanism, Significance significance,
                                  NewsReading.Section transmission, NewsReading.Section scenario) {
    public record Significance(String implication, String reason) {
    }
}
