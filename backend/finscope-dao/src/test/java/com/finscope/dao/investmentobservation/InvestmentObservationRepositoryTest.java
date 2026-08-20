package com.finscope.dao.investmentobservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.investmentobservation.InvestmentObservationDisposition;
import com.finscope.common.enums.investmentobservation.InvestmentObservationSourceType;
import com.finscope.common.enums.investmentobservation.InvestmentObservationStage;
import com.finscope.common.enums.investmentobservation.InvestmentObservationSubjectType;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.investmentobservation.InvestmentObservation;
import com.finscope.domain.investmentobservation.InvestmentObservationScoreDimension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestmentObservationRepositoryTest {
    private InvestmentObservationRepository repository;
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 20, 9, 30);

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + Files.createTempDirectory("investment-observation").resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", Files.createTempDirectory("investment-observation-root").toString());
        initializer.afterPropertiesSet();
        repository = new InvestmentObservationRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(repository, "objectMapper", new ObjectMapper());
    }

    @Test
    void upsertsTheSameSourceWithoutDuplicatingAndRecordsStageChanges() {
        InvestmentObservation first = repository.upsertGenerated(observation(81, InvestmentObservationStage.FOCUS), now);
        InvestmentObservation changed = observation(63, InvestmentObservationStage.TRACKING);
        changed.setTitle("资本开支上修进入验证期");

        InvestmentObservation second = repository.upsertGenerated(changed, now.plusHours(1));

        assertEquals(first.getId(), second.getId());
        assertEquals(1, repository.findAll().size());
        assertEquals(63, second.getScore());
        assertEquals("资本开支上修进入验证期", second.getTitle());
        assertEquals(2, repository.findTransitions(first.getId()).size());
        assertEquals(InvestmentObservationStage.FOCUS,
                repository.findTransitions(first.getId()).get(1).getFromStage());
        assertEquals(InvestmentObservationStage.TRACKING,
                repository.findTransitions(first.getId()).get(1).getToStage());
    }

    @Test
    void preservesUserDispositionDuringAutomaticRefreshAndUsesRevisionForUpdates() {
        InvestmentObservation saved = repository.upsertGenerated(observation(72, InvestmentObservationStage.FOCUS), now);
        assertTrue(repository.updateDisposition(saved.getId(), InvestmentObservationDisposition.LATER,
                saved.getRevision(), now.plusMinutes(1)));
        InvestmentObservation later = repository.findById(saved.getId()).orElseThrow(AssertionError::new);

        InvestmentObservation refreshedInput = observation(77, InvestmentObservationStage.FOCUS);
        refreshedInput.setDisposition(InvestmentObservationDisposition.ACTIVE);
        InvestmentObservation refreshed = repository.upsertGenerated(refreshedInput, now.plusMinutes(2));

        assertEquals(InvestmentObservationDisposition.LATER, refreshed.getDisposition());
        assertFalse(repository.updateDisposition(saved.getId(), InvestmentObservationDisposition.IGNORED,
                saved.getRevision(), now.plusMinutes(3)));
        assertTrue(repository.updateDisposition(saved.getId(), InvestmentObservationDisposition.ACTIVE,
                refreshed.getRevision(), now.plusMinutes(3)));
    }

    @Test
    void archivesWithAnAuditableTransition() {
        InvestmentObservation saved = repository.upsertGenerated(observation(58, InvestmentObservationStage.TRACKING), now);

        assertTrue(repository.archive(saved.getId(), saved.getRevision(), "用户结束本轮观察", now.plusDays(1)));

        InvestmentObservation archived = repository.findById(saved.getId()).orElseThrow(AssertionError::new);
        assertEquals(InvestmentObservationStage.ARCHIVED, archived.getStage());
        assertEquals("用户结束本轮观察", repository.findTransitions(saved.getId()).get(1).getReason());
    }

    private InvestmentObservation observation(int score, InvestmentObservationStage stage) {
        InvestmentObservationScoreDimension dimension = new InvestmentObservationScoreDimension();
        dimension.setCode("CHANGE");
        dimension.setLabel("变化强度");
        dimension.setScore(18);
        dimension.setMaxScore(20);
        dimension.setExplanation("出现明确变化");
        InvestmentObservation value = new InvestmentObservation();
        value.setSourceType(InvestmentObservationSourceType.RADAR_EVENT);
        value.setSourceId(101L);
        value.setTitle("算力资本开支持续上修");
        value.setSummary("多项信息显示资本开支预期提升");
        value.setSubjectType(InvestmentObservationSubjectType.THEME);
        value.setSubjectName("AI算力");
        value.setStage(stage);
        value.setScore(score);
        value.setScoreDimensions(Collections.singletonList(dimension));
        value.setWhyItMatters("可能改变服务器产业链需求预期");
        value.setUncertainty("尚待公司公告确认");
        value.setNextValidation("检查季度资本开支与出货数据");
        value.setSupportingEvidenceCount(3);
        value.setIndependentSourceCount(2);
        value.setLastSourceFingerprint("event-v1");
        value.setDisposition(InvestmentObservationDisposition.ACTIVE);
        return value;
    }
}
