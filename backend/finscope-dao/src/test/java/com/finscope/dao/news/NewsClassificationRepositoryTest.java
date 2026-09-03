package com.finscope.dao.news;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.news.NewsCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsClassificationRepositoryTest {
    @TempDir
    Path dataRoot;

    private NewsCategoryRepository categories;

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
}
