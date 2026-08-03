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

    @Test
    void deletesOnlyTheSelectedRunAndItsScopedArtifacts() {
        ResearchRun deleted = repository.save(run());
        ResearchRun retained = repository.save(run());
        String now = LocalDateTime.now().toString();

        jdbc.update("INSERT INTO research_run_source(run_id,source_name,enabled,position) VALUES(?,?,?,?)",
                deleted.getId(), "研究来源", 1, 0);
        jdbc.update("INSERT INTO research_runtime_checkpoint(research_run_id,phase,status,max_actions,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?)",
                deleted.getId(), "COLLECT", "COMPLETED", 6, now, now);
        jdbc.update("INSERT INTO research_runtime_event(research_run_id,sequence_no,event_type,status,created_at) VALUES(?,?,?,?,?)",
                deleted.getId(), 1, "TERMINATED", "COMPLETED", now);
        jdbc.update("INSERT INTO research_run_output(research_run_id,output_type,output_id,created_at) VALUES(?,?,?,?)",
                deleted.getId(), "REPORT", 1, now);
        jdbc.update("INSERT INTO agent_run(research_run_id,node_name,status,created_at) VALUES(?,?,?,?)",
                deleted.getId(), "research-orchestrate", "SUCCESS", now);
        jdbc.update("INSERT INTO research_run_plan(research_run_id,step_id,title,step_type,executor,status,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?)",
                deleted.getId(), "collect", "收集资料", "COLLECT", "FetchService", "SUCCESS", now, now);
        jdbc.update("INSERT INTO research_report(research_run_id,report_type,status,title,conclusion,conclusion_direction,"
                        + "confidence,executive_summary,content_markdown,markdown_path,generation_mode,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                deleted.getId(), "THESIS", "COMPLETED", "测试报告", "结论", "NEUTRAL", "MEDIUM", "摘要", "# 报告",
                "data/vault/research/reports/run-test.md", "RULE", now, now);

        assertEquals(1, repository.deleteById(deleted.getId()));

        assertEquals(0, count("research_run", "id", deleted.getId()));
        assertEquals(0, count("research_run_source", "run_id", deleted.getId()));
        assertEquals(0, count("research_runtime_checkpoint", "research_run_id", deleted.getId()));
        assertEquals(0, count("research_runtime_event", "research_run_id", deleted.getId()));
        assertEquals(0, count("research_run_output", "research_run_id", deleted.getId()));
        assertEquals(0, count("agent_run", "research_run_id", deleted.getId()));
        assertEquals(0, count("research_run_plan", "research_run_id", deleted.getId()));
        assertEquals(0, count("research_report", "research_run_id", deleted.getId()));
        assertEquals(1, count("research_run", "id", retained.getId()));
    }

    private int count(String table, String column, Long value) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
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
