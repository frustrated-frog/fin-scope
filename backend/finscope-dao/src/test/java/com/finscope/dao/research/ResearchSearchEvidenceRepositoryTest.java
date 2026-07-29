package com.finscope.dao.research;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.research.ResearchSearchEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResearchSearchEvidenceRepositoryTest {
    private ResearchSearchEvidenceRepository repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-research-search-evidence-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();
        repository = new ResearchSearchEvidenceRepository(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO research_run(id,run_date,theme_codes,status,created_at,updated_at) "
                + "VALUES(21,'2026-07-29','SEMI','RUNNING','2026-07-29T10:00:00','2026-07-29T10:00:00')");
        jdbcTemplate.update("INSERT INTO research_agent_decision(id,research_run_id,iteration,decision_type,"
                + "current_subgoal,decision_summary,confidence,decision_mode,status,created_at,updated_at) "
                + "VALUES(31,21,1,'TOOL_CALL','补充上市影响证据','搜索公开资料',0.9,'MODEL','EXECUTED',"
                + "'2026-07-29T10:00:00','2026-07-29T10:00:00')");
    }

    @Test
    void persistsRunScopedEvidenceAndDeduplicatesUrlWithinRun() {
        ResearchSearchEvidence first = evidence("https://example.com/disclosure", 0.91D);
        ResearchSearchEvidence saved = repository.save(first);
        repository.save(evidence("https://example.com/disclosure", 0.50D));

        List<ResearchSearchEvidence> values = repository.findByRunId(21L);

        assertNotNull(saved.getId());
        assertEquals(1, values.size());
        assertEquals("TAVILY", values.get(0).getProvider());
        assertEquals(0.91D, values.get(0).getRelevanceScore());
    }

    private ResearchSearchEvidence evidence(String url, double score) {
        ResearchSearchEvidence value = new ResearchSearchEvidence();
        value.setResearchRunId(21L);
        value.setDecisionId(31L);
        value.setProvider("TAVILY");
        value.setQueryText("长鑫科技 上市 影响");
        value.setIntent("SUPPORT");
        value.setTitle("上市后研发投入计划");
        value.setUrl(url);
        value.setContent("募集资金用于先进制程研发。");
        value.setSourceDomain("example.com");
        value.setSourceTier("T2");
        value.setRelevanceScore(score);
        return value;
    }
}
