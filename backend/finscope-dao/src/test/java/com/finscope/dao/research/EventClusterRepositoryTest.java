package com.finscope.dao.research;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.research.EventArticleLink;
import com.finscope.domain.research.ResearchEnums;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventClusterRepositoryTest {
    private EventClusterRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-event-cluster-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();

        repository = new EventClusterRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    void movingLinkIntoExistingTargetMergesMetadataInsteadOfReplacingIt() {
        EventArticleLink source = link(1L, 10L, ResearchEnums.RELATION_SUPPORTING, 0.70,
                ResearchEnums.NOVELTY_FOLLOW_UP, "源事件原因", LocalDateTime.of(2026, 6, 28, 8, 0));
        EventArticleLink target = link(2L, 10L, ResearchEnums.RELATION_PRIMARY, 0.90,
                ResearchEnums.NOVELTY_NEW, "目标事件原因", LocalDateTime.of(2026, 6, 28, 9, 0));
        repository.linkArticle(source);
        repository.linkArticle(target);

        int moved = repository.moveArticleLink(1L, 10L, 2L, "源事件原因；人工治理调整");

        EventArticleLink merged = repository.findLink(2L, 10L).get();
        assertEquals(1, moved);
        assertFalse(repository.findLink(1L, 10L).isPresent());
        assertEquals(ResearchEnums.RELATION_PRIMARY, merged.getRelationType());
        assertEquals(0.90, merged.getMatchScore(), 0.001);
        assertEquals(ResearchEnums.NOVELTY_NEW, merged.getNoveltyType());
        assertEquals(LocalDateTime.of(2026, 6, 28, 8, 0), merged.getCreatedAt());
        assertTrue(merged.getNoveltyReason().contains("目标事件原因"));
        assertTrue(merged.getNoveltyReason().contains("源事件原因"));
        assertTrue(merged.getNoveltyReason().contains("人工治理调整"));
    }

    private EventArticleLink link(Long eventId,
                                  Long articleId,
                                  String relationType,
                                  Double matchScore,
                                  String noveltyType,
                                  String noveltyReason,
                                  LocalDateTime createdAt) {
        EventArticleLink link = new EventArticleLink();
        link.setEventId(eventId);
        link.setArticleId(articleId);
        link.setRelationType(relationType);
        link.setMatchScore(matchScore);
        link.setNoveltyType(noveltyType);
        link.setNoveltyReason(noveltyReason);
        link.setCreatedAt(createdAt);
        return link;
    }
}
