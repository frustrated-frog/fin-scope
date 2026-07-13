package com.finscope.dao.research;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.research.ResearchReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchReportRepositoryTest {
    private ResearchReportRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-research-report-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();
        repository = new ResearchReportRepository(jdbcTemplate);
    }

    @Test
    void upsertsOneReportPerResearchRun() {
        ResearchReport first = repository.upsert(report("PARTIAL_SUPPORT", "MEDIUM", "第一版"));
        ResearchReport replaced = repository.upsert(report("SUPPORT", "HIGH", "第二版"));

        assertEquals(first.getId(), replaced.getId());
        ResearchReport loaded = repository.findByRunId(14L).orElseThrow(AssertionError::new);
        assertEquals("SUPPORT", loaded.getConclusionDirection());
        assertEquals("第二版", loaded.getConclusion());
        assertEquals(1, repository.findByThesisId(1L).size());
        assertTrue(loaded.getUpdatedAt() != null);
    }

    private ResearchReport report(String direction, String confidence, String conclusion) {
        ResearchReport report = new ResearchReport();
        report.setResearchRunId(14L);
        report.setThesisId(1L);
        report.setReportType("THESIS");
        report.setStatus("COMPLETED");
        report.setTitle("半导体设备周期研究报告");
        report.setConclusion(conclusion);
        report.setConclusionDirection(direction);
        report.setConfidence(confidence);
        report.setExecutiveSummary("阶段性摘要");
        report.setContentMarkdown("# 报告\n\n正文");
        report.setMarkdownPath("vault/research-reports/thesis-1/run-14.md");
        report.setGenerationMode("AGENT");
        report.setEvidenceCount(8);
        report.setSourceCount(3);
        report.setCharacterCount(9);
        return report;
    }
}
