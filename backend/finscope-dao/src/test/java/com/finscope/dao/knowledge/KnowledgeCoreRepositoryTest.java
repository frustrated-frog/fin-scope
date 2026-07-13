package com.finscope.dao.knowledge;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.research.LearningTask;
import com.finscope.domain.topic.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeCoreRepositoryTest {
    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private TopicRepository topics;
    private LearningTaskRepository tasks;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("finance.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(dataSource);

        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();
        new KnowledgeSchemaMigrator(jdbc, new DataSourceTransactionManager(dataSource)).migrate();

        topics = new TopicRepository();
        tasks = new LearningTaskRepository();
        ReflectionTestUtils.setField(topics, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(tasks, "jdbcTemplate", jdbc);
    }

    @Test
    void mapsTopicKnowledgeStateAndUsesOptimisticRevision() {
        Topic topic = saveTopic("AI Infrastructure", "ai-infrastructure");

        assertEquals("ACTIVE", topic.getLifecycleStatus());
        assertEquals("EXPLORING", topic.getMasteryStatus());
        assertEquals(0L, topic.getRevision());
        assertTrue(topics.updateKnowledgeState(
                topic.getId(), "PAUSED", "BUILDING", topic.getRevision()));
        assertFalse(topics.updateKnowledgeState(
                topic.getId(), "ACTIVE", "MATURE", topic.getRevision()));

        Topic updated = topics.findById(topic.getId()).orElseThrow(AssertionError::new);
        assertEquals("PAUSED", updated.getLifecycleStatus());
        assertEquals("BUILDING", updated.getMasteryStatus());
        assertEquals(1L, updated.getRevision());
    }

    @Test
    void supportsUnassignedSuggestionsAndGuardedTransitions() {
        Topic topic = saveTopic("Robotics", "robotics");
        LearningTask suggestion = saveTask("What changes in embodied AI?", "SUGGESTED", null, 50);

        assertNull(tasks.findById(suggestion.getId()).orElseThrow(AssertionError::new).getTopicId());
        assertTrue(tasks.transition(
                suggestion.getId(), "SUGGESTED", "TODO", topic.getId(),
                LocalDateTime.of(2026, 7, 13, 10, 0), null, null, suggestion.getRevision()));
        assertFalse(tasks.transition(
                suggestion.getId(), "SUGGESTED", "TODO", topic.getId(),
                LocalDateTime.of(2026, 7, 13, 10, 1), null, null, suggestion.getRevision()));

        LearningTask accepted = tasks.findById(suggestion.getId()).orElseThrow(AssertionError::new);
        assertEquals(topic.getId(), accepted.getTopicId());
        assertEquals("TODO", accepted.getStatus());
        assertEquals(1L, accepted.getRevision());
    }

    @Test
    void pagesByPriorityThenUpdateTimeAndEscapesLikeMetacharacters() {
        saveTask("regular question", "TODO", null, 60);
        saveTask("literal 100% signal", "TODO", null, 90);
        saveTask("literal under_score signal", "TODO", null, 70);
        saveTask("literal underXscore signal", "TODO", null, 80);

        List<LearningTask> ordered = tasks.findPage("TODO", null, null, 0, 10);
        assertEquals("literal 100% signal", ordered.get(0).getQuestion());
        assertEquals("literal underXscore signal", ordered.get(1).getQuestion());

        List<LearningTask> percent = tasks.findPage("TODO", null, "%", 0, 10);
        assertEquals(1, percent.size());
        assertEquals("literal 100% signal", percent.get(0).getQuestion());

        List<LearningTask> underscore = tasks.findPage("TODO", null, "_", 0, 10);
        assertEquals(1, underscore.size());
        assertEquals("literal under_score signal", underscore.get(0).getQuestion());
    }

    @Test
    void ignoresDuplicateSuggestionKeysWithinTheSameEvent() {
        LearningTask first = suggestion(77L, "same-key", "First wording");
        LearningTask duplicate = suggestion(77L, "same-key", "Duplicate wording");
        LearningTask anotherEvent = suggestion(78L, "same-key", "Another event");

        assertTrue(tasks.insertSuggestionIfAbsent(first));
        assertFalse(tasks.insertSuggestionIfAbsent(duplicate));
        assertTrue(tasks.insertSuggestionIfAbsent(anotherEvent));

        assertEquals(1, tasks.findByEventId(77L).size());
        assertEquals("First wording", tasks.findByEventId(77L).get(0).getQuestion());
        assertEquals(1, tasks.findByEventId(78L).size());
    }

    private Topic saveTopic(String name, String slug) {
        Topic topic = new Topic();
        topic.setName(name);
        topic.setSlug(slug);
        topic.setDescription(name + " description");
        return topics.save(topic);
    }

    private LearningTask saveTask(String question, String status, Long topicId, int priority) {
        LearningTask task = new LearningTask();
        task.setThemeCode("AI");
        task.setQuestion(question);
        task.setDifficulty("MEDIUM");
        task.setStatus(status);
        task.setOrigin("USER");
        task.setTopicId(topicId);
        task.setPriority(priority);
        return tasks.save(task);
    }

    private LearningTask suggestion(long eventId, String taskKey, String question) {
        LearningTask task = new LearningTask();
        task.setEventId(eventId);
        task.setThemeCode("AI");
        task.setQuestion(question);
        task.setDifficulty("FOUNDATION");
        task.setStatus("SUGGESTED");
        task.setOrigin("AGENT");
        task.setTaskKey(taskKey);
        task.setPriority(50);
        return task;
    }
}
