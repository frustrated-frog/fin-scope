package com.finscope.service.fetch;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RawItemSelectorTest {
    private final RawItemSelector selector = new RawItemSelector(new RawItemSignalScorer());

    @Test
    void deduplicatesByCanonicalUrlKeepsRicherItemAndSortsBySignal() {
        Source source = source();
        RawItem lowDuplicate = item("同一事件快讯", "https://example.com/a?utm_source=x#top",
                "短摘要", "短内容", 55, 3);
        RawItem richDuplicate = item("同一事件深度报道", "https://www.example.com/a/",
                "较完整摘要", repeat("更完整正文。", 40), 86, 1);
        RawItem different = item("另一条重要新闻", "https://example.com/b",
                "摘要", repeat("正文。", 25), 75, 2);

        List<RawItem> selected = selector.select(source, Arrays.asList(lowDuplicate, richDuplicate, different));

        assertEquals(2, selected.size());
        assertEquals("同一事件深度报道", selected.get(0).getTitle());
        assertEquals("另一条重要新闻", selected.get(1).getTitle());
        assertEquals(1, selected.get(0).getSourceRank());
        assertEquals(2, selected.get(1).getSourceRank());
    }

    @Test
    void dropsThinItemsBeforeExpensiveIngestPipeline() {
        Source source = source();
        RawItem thin = item("空壳页", "https://example.com/empty", "", "一句话", 35, 1);
        RawItem useful = item("有价值内容", "https://example.com/useful",
                "摘要", repeat("正文。", 25), 72, 1);

        List<RawItem> selected = selector.select(source, Arrays.asList(thin, useful));

        assertEquals(1, selected.size());
        assertEquals("有价值内容", selected.get(0).getTitle());
    }

    private Source source() {
        Source source = new Source();
        source.setType("RSS");
        source.setName("测试来源");
        source.setUrl("https://example.com/rss");
        source.setCredibility(4);
        return source;
    }

    private RawItem item(String title, String url, String summary, String body, int qualityScore, int daysAgo) {
        RawItem item = new RawItem(title, url, LocalDateTime.now().minusDays(daysAgo), summary, body);
        item.setQualityScore(qualityScore);
        item.setExtractionMethod("rss:rome-markdown");
        return item;
    }

    private String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
