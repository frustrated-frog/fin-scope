package com.finscope.dao.research.mission;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.domain.research.mission.ResearchMissionTask;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchMissionRepositoryTest {
    @TempDir
    Path tempDir;
    private ResearchMissionRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("finance.db"));
        jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        insertResearchRun(9L);

        repository = new ResearchMissionRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void persistsMissionTaskLifecycleAndOrderedGapSnapshots() {
        repository.initialize(9L, "验证AI资本开支能否持续", "AI基础设施",
                "聚焦需求、供给与反方风险", Arrays.asList("至少两个独立来源", "包含正反证据"), 12);
        repository.replacePlan(9L, "LLM_VALIDATED", "聚焦产业链兑现",
                Arrays.asList("至少六条证据"), Arrays.asList(
                        task("baseline_scan", "基线扫描", "source_scan", "BASELINE"),
                        task("search_counter", "反方证据搜索", "public_news_search", "COUNTER")),
                "PLAN_REJECTED", "任务 search_counter 使用了未注册工具 external_browser");

        assertTrue(repository.startTask(9L, "baseline_scan"));
        assertTrue(repository.completeTask(9L, "baseline_scan", "完成三个信息源扫描", 4, 2));

        ResearchMissionGap first = gap(9L, "baseline_scan", false, 4, 2, 4, 0,
                Arrays.asList("缺少反向或风险证据"), "COUNTER", "hash-1");
        ResearchMissionGap second = gap(9L, "search_counter", true, 7, 3, 5, 2,
                Arrays.<String>asList(), "NONE", "hash-2");
        repository.appendGap(first);
        repository.appendGap(second);

        assertEquals("RUNNING", repository.findMission(9L).get().getStatus());
        assertEquals("LLM_VALIDATED", repository.findMission(9L).get().getPlanningMode());
        assertEquals("任务 search_counter 使用了未注册工具 external_browser",
                repository.findMission(9L).get().getFallbackDetail());
        assertEquals(Arrays.asList("baseline_scan", "search_counter"),
                repository.findTasks(9L).stream()
                        .map(ResearchMissionTask::getTaskKey)
                        .collect(Collectors.toList()));
        assertEquals("COMPLETED", repository.findTasks(9L).get(0).getStatus());
        assertEquals(4, repository.findTasks(9L).get(0).getEvidenceDelta());
        assertEquals(2, repository.findGaps(9L).size());
        assertEquals(1, repository.findGaps(9L).get(0).getAssessmentIndex());
        assertEquals(2, repository.findGaps(9L).get(1).getAssessmentIndex());
        assertEquals("COUNTER", repository.findGaps(9L).get(0).getRecommendedIntent());
        assertNull(repository.findMission(9L).get().getActiveTaskKey());
    }

    @Test
    void skipsOnlyPendingSearchTasksAndKeepsCompletedTasksImmutable() {
        repository.initialize(9L, "目标", "对象", "范围", Arrays.asList("标准"), 8);
        repository.replacePlan(9L, "DETERMINISTIC", "范围", Arrays.asList("标准"), Arrays.asList(
                task("baseline_scan", "基线扫描", "source_scan", "BASELINE"),
                task("search_support", "支持证据搜索", "public_news_search", "SUPPORT"),
                task("search_counter", "反方证据搜索", "public_news_search", "COUNTER"),
                task("assess_evidence", "证据判断", "evidence_assess", "ASSESS")), "MODEL_DISABLED", null);
        repository.startTask(9L, "search_support");
        repository.completeTask(9L, "search_support", "已完成", 2, 1);

        assertEquals(1, repository.skipPendingTasksByTool(9L, "public_news_search", "SUFFICIENT_EVIDENCE"));

        List<ResearchMissionTask> tasks = repository.findTasks(9L);
        assertEquals("COMPLETED", tasks.get(1).getStatus());
        assertEquals("SKIPPED", tasks.get(2).getStatus());
        assertEquals("SUFFICIENT_EVIDENCE", tasks.get(2).getSkipReason());
        assertEquals("PENDING", tasks.get(3).getStatus());
        assertFalse(repository.completeTask(9L, "search_counter", "不应覆盖", 9, 9));
    }

    @Test
    void finalizationSkipsEveryUnfinishedTaskWithoutOverwritingCompletedWork() {
        repository.initialize(9L, "目标", "对象", "范围", Arrays.asList("标准"), 8);
        repository.replacePlan(9L, "DETERMINISTIC", "范围", Arrays.asList("标准"), Arrays.asList(
                task("baseline_scan", "基线扫描", "source_scan", "BASELINE"),
                task("search_support", "支持证据搜索", "public_news_search", "SUPPORT"),
                task("search_counter", "反方证据搜索", "public_news_search", "COUNTER")), "MODEL_DISABLED", null);
        repository.startTask(9L, "baseline_scan");
        repository.completeTask(9L, "baseline_scan", "完成扫描", 2, 1);
        repository.startTask(9L, "search_support");

        assertEquals(2, repository.skipUnfinishedTasks(9L, "RUNTIME_TERMINATED:NO_PROGRESS"));

        List<ResearchMissionTask> tasks = repository.findTasks(9L);
        assertEquals("COMPLETED", tasks.get(0).getStatus());
        assertEquals("SKIPPED", tasks.get(1).getStatus());
        assertEquals("RUNTIME_TERMINATED:NO_PROGRESS", tasks.get(1).getSkipReason());
        assertEquals("SKIPPED", tasks.get(2).getStatus());
        assertNull(repository.findMission(9L).get().getActiveTaskKey());
    }

    private ResearchMissionTask task(String key, String title, String toolCode, String intent) {
        ResearchMissionTask task = new ResearchMissionTask();
        task.setTaskKey(key);
        task.setTitle(title);
        task.setQuestion(title + "要回答什么？");
        task.setTaskType("SEARCH");
        task.setToolCode(toolCode);
        task.setIntent(intent);
        task.setDependencies("baseline_scan".equals(key)
                ? Arrays.<String>asList()
                : Arrays.asList("baseline_scan"));
        task.setParallelGroup("evidence_search");
        task.setQueryText(title + " 最新信息");
        task.setRationale("补齐研究证据");
        task.setExpectedEvidence("公开一手资料");
        return task;
    }

    private ResearchMissionGap gap(Long runId,
                                   String afterTaskKey,
                                   boolean sufficient,
                                   int evidenceCount,
                                   int sourceCount,
                                   int supportCount,
                                   int counterCount,
                                   List<String> warnings,
                                   String recommendedIntent,
                                   String stateHash) {
        ResearchMissionGap gap = new ResearchMissionGap();
        gap.setResearchRunId(runId);
        gap.setAfterTaskKey(afterTaskKey);
        gap.setSufficient(sufficient);
        gap.setEvidenceCount(evidenceCount);
        gap.setSourceCount(sourceCount);
        gap.setSupportCount(supportCount);
        gap.setCounterCount(counterCount);
        gap.setWarnings(warnings);
        gap.setRecommendedIntent(recommendedIntent);
        gap.setStateHash(stateHash);
        return gap;
    }

    private void insertResearchRun(Long id) {
        String now = LocalDateTime.now().toString();
        jdbc.update("INSERT INTO research_run(id,run_date,theme_codes,status,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                id, "2026-07-26", "china_macro", "RUNNING", now, now);
    }
}
