package com.finscope.dao.intake;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.intake.FetchBatch;
import com.finscope.domain.intake.IntakeCandidate;
import com.finscope.domain.intake.IntakeEnums;
import com.finscope.domain.source.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntakeRepositoryTest {
    private SourceRepository sourceRepository;
    private FetchBatchRepository fetchBatchRepository;
    private IntakeCandidateRepository candidateRepository;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-intake-repository-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();

        sourceRepository = new SourceRepository();
        ReflectionTestUtils.setField(sourceRepository, "jdbcTemplate", jdbcTemplate);
        fetchBatchRepository = new FetchBatchRepository();
        ReflectionTestUtils.setField(fetchBatchRepository, "jdbcTemplate", jdbcTemplate);
        candidateRepository = new IntakeCandidateRepository();
        ReflectionTestUtils.setField(candidateRepository, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    void persistsSourceIntakeSettingsBatchAndCandidateStatus() {
        Source source = new Source();
        source.setName("宏观 RSS");
        source.setType("RSS");
        source.setUrl("https://example.com/rss");
        source.setEnabled(true);
        source.setScheduledEnabled(true);
        source.setScheduleTimes("08:30,21:30");
        source.setMaxItemsPerRun(3);
        source.setFetchFrequencyMinutes(60);
        source.setCredibility(4);
        source.setTags("宏观,市场");

        Source savedSource = sourceRepository.save(source);
        Source loadedSource = sourceRepository.findById(savedSource.getId()).get();

        assertTrue(loadedSource.isScheduledEnabled());
        assertEquals("08:30,21:30", loadedSource.getScheduleTimes());
        assertEquals(3, loadedSource.getMaxItemsPerRun());

        FetchBatch batch = new FetchBatch();
        batch.setSourceId(savedSource.getId());
        batch.setSourceName(savedSource.getName());
        batch.setTriggerType(IntakeEnums.TRIGGER_MANUAL);
        batch.setLookbackDays(3);
        batch.setMaxItemsRequested(3);
        FetchBatch running = fetchBatchRepository.start(batch);

        assertNotNull(running.getId());
        assertEquals(IntakeEnums.BATCH_RUNNING, running.getStatus());

        IntakeCandidate candidate = new IntakeCandidate();
        candidate.setBatchId(running.getId());
        candidate.setSourceId(savedSource.getId());
        candidate.setSourceName(savedSource.getName());
        candidate.setSourceType("RSS");
        candidate.setOriginalTitle("Fed signals rate cuts");
        candidate.setOriginalUrl("https://example.com/fed");
        candidate.setOriginalSummary("The Federal Reserve signaled a softer path.");
        candidate.setOriginalBody("The Federal Reserve signaled a softer path for rates.");
        candidate.setContentType("ARTICLE");
        candidate.setExtractionMethod("rss:rome-markdown");
        candidate.setExtractionQualityScore(90);
        candidate.setPublishedAt(LocalDateTime.of(2026, 7, 9, 8, 0));
        candidate.setFetchedAt(LocalDateTime.of(2026, 7, 9, 8, 1));
        candidate.setChineseTitle("美联储释放更温和利率路径信号");
        candidate.setDecisionSummary("值得入库：它影响利率预期和风险资产定价。");
        candidate.setKeyFactsJson("[\"美联储释放更温和信号\"]");
        candidate.setWhyItMatters("影响利率预期、黄金和权益资产。");
        candidate.setNoveltyJudgment("NEW_EVENT");
        candidate.setRiskFlagsJson("[\"需继续观察数据验证\"]");
        candidate.setAgentScore(82);
        candidate.setAgentRecommendation(IntakeEnums.AGENT_PROMOTABLE);
        candidate.setAgentReason("高可信宏观变量。");
        candidate.setAgentModel("fallback");
        candidate.setAgentStatus(IntakeEnums.AGENT_FALLBACK);
        candidate.setAgentReviewJson("{\"score\":82}");
        candidate.setHumanStatus(IntakeEnums.HUMAN_PENDING);
        candidate.setUrlFingerprint("https://example.com/fed");
        candidate.setTitleFingerprint("fedsignalsratecuts");
        candidate.setBodyFingerprint("12345");

        IntakeCandidate savedCandidate = candidateRepository.save(candidate);
        fetchBatchRepository.finish(running, IntakeEnums.BATCH_COMPLETED, 1, 1, 1, 0, 0, null,
                "{\"summaryText\":\"本批共 1 条候选\"}", "本批共 1 条候选");

        List<IntakeCandidate> pending = candidateRepository.findByStatus(IntakeEnums.HUMAN_PENDING, null, null);
        assertEquals(1, pending.size());
        assertEquals("美联储释放更温和利率路径信号", pending.get(0).getChineseTitle());

        candidateRepository.updateHumanStatus(savedCandidate.getId(), IntakeEnums.HUMAN_SAVED_FOR_LATER, "晚上再看");
        assertEquals(0, candidateRepository.findByStatus(IntakeEnums.HUMAN_PENDING, null, null).size());
        assertEquals(1, candidateRepository.findByStatus(IntakeEnums.HUMAN_SAVED_FOR_LATER, null, null).size());

        FetchBatch completed = fetchBatchRepository.findById(running.getId()).get();
        assertEquals(IntakeEnums.BATCH_COMPLETED, completed.getStatus());
        assertEquals(1, completed.getCandidateCount());
        assertEquals("本批共 1 条候选", completed.getBatchSummaryText());
    }
}
