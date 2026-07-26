package com.finscope.dao.insight;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.insight.InsightCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InsightCardRepositoryTest {
    @TempDir
    Path tempDir;
    private InsightCardRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();

        repository = new InsightCardRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void persistsInterpretationProvenanceAndDefaultsLegacyCallersToUnknown() {
        InsightCard modelCard = card(101L);
        modelCard.setInterpretationSource("LLM");
        InsightCard savedModelCard = repository.save(modelCard);
        assertEquals("LLM", savedModelCard.getInterpretationSource());

        InsightCard legacyCard = card(102L);
        InsightCard savedLegacyCard = repository.save(legacyCard);
        assertEquals("UNKNOWN", savedLegacyCard.getInterpretationSource());
    }

    private InsightCard card(Long articleId) {
        InsightCard value = new InsightCard();
        value.setArticleId(articleId);
        value.setTitle("可核查解读");
        value.setCardMarkdown("## 情报卡片");
        return value;
    }
}
