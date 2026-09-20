package com.finscope.service.news;

import com.finscope.domain.news.NewsCategory;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NewsRuleClassifierTest {
    private final NewsRuleClassifier classifier = new NewsRuleClassifier();
    private final List<NewsCategory> categories = List.of(
            new NewsCategory("MACRO_POLICY", "政策宏观", "", true, 10),
            new NewsCategory("COMPANY", "公司", "", true, 20));

    @Test
    void distinguishesCentralBankLiquidityFromShareBuybacks() {
        assertEquals("MACRO_POLICY", classifier.classify(item("央行开展逆回购"), categories).getCategoryCode());
        assertEquals("COMPANY", classifier.classify(item("公司宣布回购股份"), categories).getCategoryCode());
    }

    @Test
    void unknownOrDisabledCategoriesRemainUnclassifiedWithEvidence() {
        assertEquals("UNCLASSIFIED", classifier.classify(item("今日天气晴"), categories).getStatus());
        assertNull(classifier.classify(item("公司宣布回购股份"), List.of()).getCategoryCode());
        assertTrue(classifier.classify(item("央行开展逆回购"), categories).getReason().contains("逆回购"));
    }

    private NewsClassificationCandidate item(String title) {
        return new NewsClassificationCandidate("test:1", title, "", "测试", null);
    }
}
