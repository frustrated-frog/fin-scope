package com.finscope.service.dedupe;

import com.finscope.service.dedupe.FingerprintService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FingerprintServiceTest {
    private final FingerprintService service = new FingerprintService();

    @Test
    void normalizesTrackingParametersWhenHashingUrls() {
        String first = service.urlFingerprint("https://example.com/a?utm_source=x&id=1#top");
        String second = service.urlFingerprint("https://example.com/a?id=1");

        assertEquals(first, second);
    }

    @Test
    void detectsHighlySimilarChineseFinancialTitles() {
        double score = service.titleSimilarity("美联储释放降息信号，黄金价格走强", "美联储降息预期升温 黄金价格继续走强");

        assertTrue(score >= 0.45, "expected similar titles to score high, got " + score);
    }

    @Test
    void createsStableBodySimhashForSimilarContent() {
        long first = service.bodySimhash("今日市场关注美联储降息预期，黄金和美债价格同步走强。");
        long second = service.bodySimhash("市场继续关注美联储降息预期，黄金与美债价格同步走强。");

        assertTrue(service.hammingDistance(first, second) <= 18);
    }
}
