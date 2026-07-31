package com.finscope.dao.news;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.news.NewsCategory;
import com.finscope.domain.news.NewsItemClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsClassificationRepositoryTest {
    @TempDir
    Path dataRoot;

    private NewsCategoryRepository categories;
    private NewsClassificationRepository classifications;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();
        categories = new NewsCategoryRepository(jdbc);
        classifications = new NewsClassificationRepository(jdbc);
    }

    @Test
    void initializesEnabledCategoriesInDisplayOrder() {
        List<NewsCategory> values = categories.findEnabled();

        assertEquals(Arrays.asList("COMPANY", "INDUSTRY", "MACRO_POLICY", "GLOBAL", "MARKET_MOVE"),
                Arrays.asList(values.get(0).getCode(), values.get(1).getCode(), values.get(2).getCode(),
                        values.get(3).getCode(), values.get(4).getCode()));
        assertEquals("公司动态", values.get(0).getName());
        assertTrue(values.get(0).getClassificationGuidance().contains("公司"));
    }

    @Test
    void claimsEachItemOnceAndLoadsCompletedClassificationsInBatch() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 10, 0);

        assertTrue(classifications.claim("CLS:1", now, now.minusMinutes(5)));
        assertFalse(classifications.claim("CLS:1", now.plusSeconds(1), now.minusMinutes(5)));

        classifications.markClassified("CLS:1", "COMPANY", 0.91, "涉及公司重大订单", "model-a", now.plusSeconds(2));
        Map<String, NewsItemClassification> loaded = classifications.findByItemIds(Arrays.asList("CLS:1", "THS:2"));

        assertEquals(1, loaded.size());
        assertEquals("CLASSIFIED", loaded.get("CLS:1").getStatus());
        assertEquals("COMPANY", loaded.get("CLS:1").getCategoryCode());
        assertEquals(0.91, loaded.get("CLS:1").getConfidence(), 0.001);
    }

    @Test
    void retriesFailedClassificationOnlyAfterRetryBoundary() {
        LocalDateTime failedAt = LocalDateTime.of(2026, 7, 31, 10, 0);
        assertTrue(classifications.claim("THS:2", failedAt, failedAt.minusMinutes(5)));
        classifications.markFailed("THS:2", "模型暂不可用", "model-a", failedAt);

        assertFalse(classifications.claim("THS:2", failedAt.plusMinutes(4), failedAt.minusMinutes(1)));
        assertTrue(classifications.claim("THS:2", failedAt.plusMinutes(6), failedAt.plusMinutes(1)));
        assertEquals("PENDING", classifications.findByItemIds(Arrays.asList("THS:2")).get("THS:2").getStatus());
    }

    @Test
    void marksLowConfidenceItemsForReview() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 10, 0);
        assertTrue(classifications.claim("CLS:LOW", now, now.minusMinutes(5)));

        classifications.markClassified("CLS:LOW", "COMPANY", 0.69,
                "主体可能同时涉及行业变化", "model-a", now.plusSeconds(1));

        NewsItemClassification value = classifications.findByItemIds(Arrays.asList("CLS:LOW")).get("CLS:LOW");
        assertEquals("PENDING_REVIEW", value.getReviewStatus());
        assertTrue(value.isPendingReview());
        assertEquals("COMPANY", value.getEffectiveCategoryCode());
    }

    @Test
    void manualCorrectionPreservesAgentDecision() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 10, 0);
        assertTrue(classifications.claim("CLS:CORRECT", now, now.minusMinutes(5)));
        classifications.markClassified("CLS:CORRECT", "COMPANY", 0.65,
                "公司公告", "model-a", now.plusSeconds(1));

        classifications.review("CLS:CORRECT", "INDUSTRY", "产业链影响", now.plusMinutes(1));

        NewsItemClassification value = classifications.findByItemIds(Arrays.asList("CLS:CORRECT"))
                .get("CLS:CORRECT");
        assertEquals("COMPANY", value.getCategoryCode());
        assertEquals("公司公告", value.getReason());
        assertEquals("INDUSTRY", value.getEffectiveCategoryCode());
        assertEquals("产业链影响", value.getManualReason());
        assertEquals("CORRECTED", value.getReviewStatus());
        assertTrue(value.isManuallyReviewed());
    }

    @Test
    void migratesExistingClassificationTableAndBackfillsReviewStatus() throws Exception {
        SQLiteDataSource legacyDataSource = new SQLiteDataSource();
        legacyDataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("legacy.db"));
        JdbcTemplate legacyJdbc = new JdbcTemplate(legacyDataSource);
        legacyJdbc.execute("CREATE TABLE news_item_classification (item_id TEXT PRIMARY KEY,status TEXT NOT NULL,"
                + "category_code TEXT,confidence REAL,reason TEXT,model_name TEXT,error_message TEXT,"
                + "created_at TEXT NOT NULL,updated_at TEXT NOT NULL)");
        legacyJdbc.update("INSERT INTO news_item_classification(item_id,status,category_code,confidence,reason,"
                        + "model_name,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                "LEGACY:1", "CLASSIFIED", "COMPANY", 0.68, "旧分类结果", "model-a",
                "2026-07-31T10:00:00", "2026-07-31T10:00:00");
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", legacyJdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());

        initializer.afterPropertiesSet();

        NewsItemClassification migrated = new NewsClassificationRepository(legacyJdbc)
                .findByItemIds(Arrays.asList("LEGACY:1")).get("LEGACY:1");
        assertEquals("PENDING_REVIEW", migrated.getReviewStatus());
        assertEquals("COMPANY", migrated.getEffectiveCategoryCode());
        List<Map<String, Object>> columns = legacyJdbc.queryForList(
                "PRAGMA table_info(news_item_classification)");
        assertTrue(columns.stream().anyMatch(column -> "manual_category_code".equals(column.get("name"))));
        assertTrue(columns.stream().anyMatch(column -> "reviewed_at".equals(column.get("name"))));
    }
}
