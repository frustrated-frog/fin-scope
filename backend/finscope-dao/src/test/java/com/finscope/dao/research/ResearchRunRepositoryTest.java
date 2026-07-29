package com.finscope.dao.research;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.research.ResearchMode;
import com.finscope.domain.research.ResearchRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResearchRunRepositoryTest {
    @TempDir
    Path tempDir;
    private JdbcTemplate jdbc;
    private ResearchRunRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("finance.db"));
        jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        repository = new ResearchRunRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void savesExplicitQuickModeAndReadsLegacyDefaultAsDeep() {
        ResearchRun quick = run();
        quick.setMode(ResearchMode.QUICK);
        ResearchRun saved = repository.save(quick);

        String now = LocalDateTime.now().toString();
        jdbc.update("INSERT INTO research_run(run_date,theme_codes,status,created_at,updated_at) VALUES(?,?,?,?,?)",
                "2026-07-28", "china_macro", "RUNNING", now, now);

        assertEquals(ResearchMode.QUICK, repository.findById(saved.getId()).get().getMode());
        assertEquals(ResearchMode.DEEP, repository.findAll().stream()
                .filter(item -> !item.getId().equals(saved.getId())).findFirst().get().getMode());
    }

    private ResearchRun run() {
        ResearchRun value = new ResearchRun();
        value.setRunDate(LocalDate.of(2026, 7, 29));
        value.setThemeCodes(Collections.singletonList("china_macro"));
        value.setSourceCount(0);
        value.setStatus("RUNNING");
        value.setSummary("test");
        return value;
    }
}
