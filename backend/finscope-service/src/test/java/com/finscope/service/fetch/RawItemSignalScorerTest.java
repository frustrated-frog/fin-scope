package com.finscope.service.fetch;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RawItemSignalScorerTest {
    private final RawItemSignalScorer scorer = new RawItemSignalScorer();

    @Test
    void scoresRichRecentOfficialItemsAboveThinItems() {
        Source official = source(5);
        RawItem rich = item("美联储发布议息声明",
                "美联储发布最新议息声明，市场关注降息路径。",
                repeat("声明提到通胀、就业和金融条件变化。", 30),
                88,
                LocalDateTime.now().minusHours(2));
        rich.setExtractionMethod("web:profile:federal-reserve");

        RawItem thin = item("转载快讯",
                "",
                "一句话。",
                40,
                LocalDateTime.now().minusDays(10));
        thin.setExtractionMethod("web:generic-score");

        RawItemSignal richSignal = scorer.score(official, rich);
        RawItemSignal thinSignal = scorer.score(source(3), thin);

        assertTrue(richSignal.getScore() > thinSignal.getScore());
        assertTrue(richSignal.isSelectable());
        assertTrue(!thinSignal.isSelectable());
    }

    private Source source(int credibility) {
        Source source = new Source();
        source.setType("WEB");
        source.setName("测试来源");
        source.setUrl("https://example.com");
        source.setCredibility(credibility);
        return source;
    }

    private RawItem item(String title, String summary, String body, int qualityScore, LocalDateTime publishedAt) {
        RawItem item = new RawItem(title, "https://example.com/" + title.hashCode(), publishedAt, summary, body);
        item.setQualityScore(qualityScore);
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
