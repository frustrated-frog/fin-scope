package com.finscope.service.knowledge;

import com.finscope.common.util.TimeUtil;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.dao.knowledge.KnowledgeEntryRepository;
import com.finscope.dao.knowledge.KnowledgeProjectionJobRepository;
import com.finscope.dao.knowledge.KnowledgeSchemaMigrator;
import com.finscope.dao.knowledge.TopicEventRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.knowledge.KnowledgeEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeLearningTransactionTest {
    private static final long TOPIC_ID = 10L;
    private static final long EVENT_ID = 20L;
    private static final long ARTICLE_ID = 30L;
    private static final long EVIDENCE_ID = 40L;
    private static final long TASK_ID = 50L;

    @TempDir
    Path tempDir;

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private KnowledgeLearningService failingService;
    private KnowledgeLearningService normalService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "transaction-test",
                Collections.<String, Object>singletonMap("test.data-root", tempDir.toString())
        ));
        context.register(TransactionTestConfiguration.class);
        context.refresh();

        jdbc = context.getBean(JdbcTemplate.class);
        failingService = context.getBean("failingKnowledgeLearningService", KnowledgeLearningService.class);
        normalService = context.getBean("normalKnowledgeLearningService", KnowledgeLearningService.class);
        insertFixture();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void completionRollsBackEveryWriteBeforeTaskTransitionThenCommitsNormally() {
        assertThrows(InjectedCompletionFailure.class, () -> failingService.completeTask(
                TASK_ID, TOPIC_ID, "transactional answer", "HIGH",
                Collections.singletonList(EVIDENCE_ID), 0L
        ));

        assertEquals(0, count("knowledge_entry"));
        assertEquals(0, count("knowledge_entry_evidence"));
        assertEquals(0, count("topic_event"));
        assertEquals(0, count("knowledge_projection_job"));
        assertEquals("IN_PROGRESS", taskValue("status", String.class));
        assertEquals(0L, taskValue("revision", Long.class));

        KnowledgeEntry completed = normalService.completeTask(
                TASK_ID, TOPIC_ID, "transactional answer", "HIGH",
                Collections.singletonList(EVIDENCE_ID), 0L
        );

        assertEquals("FINAL", completed.getEntryStatus());
        assertEquals(1, count("knowledge_entry"));
        assertEquals(1, count("knowledge_entry_evidence"));
        assertEquals(1, count("topic_event"));
        assertEquals(1, count("knowledge_projection_job"));
        assertEquals("DONE", taskValue("status", String.class));
        assertEquals("RECORDED", taskValue("completion_mode", String.class));
        assertEquals(1L, taskValue("revision", Long.class));
    }

    private void insertFixture() {
        String now = TimeUtil.text(LocalDateTime.of(2026, 7, 13, 12, 0));
        jdbc.update("INSERT INTO topic(id,name,slug,description,status,lifecycle_status," +
                        "mastery_status,revision,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                TOPIC_ID, "AI Agents", "ai-agents", "Agent research", "LEARNING",
                "ACTIVE", "EXPLORING", 0, now, now);
        jdbc.update("INSERT INTO event_cluster(id,canonical_title,canonical_event_key,theme_code," +
                        "summary,status,first_seen_at,last_seen_at,importance_score,novelty_state," +
                        "evidence_count,article_count,created_at,updated_at) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                EVENT_ID, "Agent event", "agent-event", "AI", "Summary", "ACTIVE",
                now, now, 80, "NEW", 1, 1, now, now);
        jdbc.update("INSERT INTO article(id,title,url,fetched_at) VALUES(?,?,?,?)",
                ARTICLE_ID, "Primary source", "https://example.test/source", now);
        jdbc.update("INSERT INTO evidence_item(id,event_id,article_id,source_tier,evidence_type," +
                        "claim,claim_key,confidence,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                EVIDENCE_ID, EVENT_ID, ARTICLE_ID, "A", "FACT", "Verified fact",
                "verified fact", 90, now);
        jdbc.update("INSERT INTO learning_task(id,event_id,topic_id,theme_code,question,difficulty," +
                        "status,origin,priority,revision,created_at,updated_at) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                TASK_ID, EVENT_ID, TOPIC_ID, "AI", "What did we learn?", "MEDIUM",
                "IN_PROGRESS", "USER", 80, 0, now, now);
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private <T> T taskValue(String column, Class<T> type) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM learning_task WHERE id=?", type, TASK_ID);
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionTestConfiguration {
        @Bean
        DataSource dataSource(@Value("${test.data-root}") String dataRoot) {
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + Paths.get(dataRoot).resolve("finance.db") +
                    "?foreign_keys=on");
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        DatabaseInitializer databaseInitializer(JdbcTemplate jdbcTemplate,
                                                @Value("${test.data-root}") String dataRoot) {
            DatabaseInitializer initializer = new DatabaseInitializer();
            ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
            ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot);
            return initializer;
        }

        @Bean
        KnowledgeSchemaMigrator knowledgeSchemaMigrator(
                JdbcTemplate jdbcTemplate,
                PlatformTransactionManager transactionManager,
                DatabaseInitializer databaseInitializer) {
            return new KnowledgeSchemaMigrator(jdbcTemplate, transactionManager);
        }

        @Bean
        TopicRepository topicRepository() {
            return new TopicRepository();
        }

        @Bean
        LearningTaskRepository learningTaskRepository() {
            return new LearningTaskRepository();
        }

        @Bean
        EvidenceItemRepository evidenceItemRepository() {
            return new EvidenceItemRepository();
        }

        @Bean
        TopicEventRepository topicEventRepository(JdbcTemplate jdbcTemplate) {
            return new TopicEventRepository(jdbcTemplate);
        }

        @Bean("normalKnowledgeEntries")
        KnowledgeEntryRepository normalKnowledgeEntries(JdbcTemplate jdbcTemplate) {
            return new KnowledgeEntryRepository(jdbcTemplate);
        }

        @Bean("failingKnowledgeEntries")
        KnowledgeEntryRepository failingKnowledgeEntries(JdbcTemplate jdbcTemplate) {
            return new FailingKnowledgeEntryRepository(jdbcTemplate);
        }

        @Bean
        KnowledgeProjectionJobRepository knowledgeProjectionJobRepository(JdbcTemplate jdbcTemplate) {
            return new KnowledgeProjectionJobRepository(jdbcTemplate);
        }

        @Bean
        LearningTaskPolicy learningTaskPolicy() {
            return new LearningTaskPolicy();
        }

        @Bean("normalKnowledgeLearningService")
        KnowledgeLearningService normalKnowledgeLearningService(
                LearningTaskRepository tasks,
                TopicRepository topics,
                @Qualifier("normalKnowledgeEntries") KnowledgeEntryRepository entries,
                EvidenceItemRepository evidence,
                TopicEventRepository topicEvents,
                KnowledgeProjectionJobRepository projectionJobs,
                LearningTaskPolicy policy,
                ApplicationEventPublisher events) {
            return new KnowledgeLearningService(
                    tasks, topics, entries, evidence, topicEvents, projectionJobs, policy, events);
        }

        @Bean("failingKnowledgeLearningService")
        KnowledgeLearningService failingKnowledgeLearningService(
                LearningTaskRepository tasks,
                TopicRepository topics,
                @Qualifier("failingKnowledgeEntries") KnowledgeEntryRepository entries,
                EvidenceItemRepository evidence,
                TopicEventRepository topicEvents,
                KnowledgeProjectionJobRepository projectionJobs,
                LearningTaskPolicy policy,
                ApplicationEventPublisher events) {
            return new KnowledgeLearningService(
                    tasks, topics, entries, evidence, topicEvents, projectionJobs, policy, events);
        }
    }

    static class FailingKnowledgeEntryRepository extends KnowledgeEntryRepository {
        FailingKnowledgeEntryRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        @Override
        public boolean finalizeDraft(Long id, long expectedRevision) {
            super.finalizeDraft(id, expectedRevision);
            throw new InjectedCompletionFailure();
        }
    }

    static class InjectedCompletionFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
