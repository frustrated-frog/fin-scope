package com.finscope.dao.research;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.ThesisFinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchThesisRepositoryTest {
    private ResearchThesisRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-research-thesis-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();
        repository = new ResearchThesisRepository(jdbcTemplate);
    }

    @Test
    void persistsThesisAndFindingsInReverseUpdatedOrder() {
        ResearchThesis first = thesis("半导体设备景气是否转向分化？", "INDUSTRY", "半导体设备", null);
        ResearchThesis second = thesis("英伟达盈利预期是否仍在上修？", "COMPANY", "英伟达", "NVDA");
        repository.save(first);
        repository.save(second);

        ThesisFinding finding = new ThesisFinding();
        finding.setThesisId(second.getId());
        finding.setStance("COUNTER");
        finding.setSummary("估值已经反映较高增长预期，需验证订单持续性。");
        finding.setEvidenceId(42L);
        repository.saveFinding(finding);

        List<ResearchThesis> theses = repository.findAll();
        assertEquals(second.getId(), theses.get(0).getId());
        assertEquals("NVDA", theses.get(0).getSubjectCode());
        assertEquals(1, repository.findFindingsByThesisId(second.getId()).size());
        assertTrue(repository.findFindingsByThesisId(second.getId()).get(0).getCreatedAt() != null);
    }

    private ResearchThesis thesis(String question, String subjectType, String subjectName, String subjectCode) {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setQuestion(question);
        thesis.setSubjectType(subjectType);
        thesis.setSubjectName(subjectName);
        thesis.setSubjectCode(subjectCode);
        thesis.setStatus("OPEN");
        return thesis;
    }
}
