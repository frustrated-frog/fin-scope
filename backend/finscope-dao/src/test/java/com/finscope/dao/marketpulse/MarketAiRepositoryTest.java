package com.finscope.dao.marketpulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.marketpulse.MarketAiStatus;
import com.finscope.domain.marketpulse.ai.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class MarketAiRepositoryTest {
    @TempDir Path directory;
    private AnnotationConfigApplicationContext context;
    private MarketAiRepository repository;
    private JdbcTemplate jdbc;
    private final LocalDateTime now = LocalDateTime.parse("2026-11-18T19:00:00");

    @Configuration
    @EnableTransactionManagement
    static class Transactions {
    }

    @BeforeEach
    void setup() {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("market-ai.db"));
        jdbc = new JdbcTemplate(source);
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class, MarketAiRepository.class);
        context.registerBean("databaseInitializer", Object.class, Object::new);
        context.registerBean(JdbcTemplate.class, () -> jdbc);
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.refresh();
        repository = context.getBean(MarketAiRepository.class);
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void responseCheckpointAndTokenAuditRollBackTogether() {
        var view = view();
        repository.claim("key", "owner", view, now);
        repository.saveInput("key", "owner", new MarketAiInput(), now);
        view.setStatus(MarketAiStatus.RUNNING);
        repository.markSent("key", "owner", view, now);
        jdbc.update("DELETE FROM market_ai_attempt WHERE cache_key=?", "key");
        assertThrows(IllegalStateException.class, () -> repository.saveReply("key", "owner", view, "paid reply", true, now));
        assertNull(jdbc.queryForObject("SELECT response_text FROM market_ai_artifact WHERE cache_key=?", String.class, "key"));
        view.setStatus(MarketAiStatus.READY);
        assertThrows(IllegalStateException.class, () -> repository.finish("key", "owner", view, now));
        assertEquals(MarketAiStatus.RUNNING, repository.find("key").orElseThrow().getStatus());
    }

    @Test
    void retentionRemovesOnlyOldReplayMaterialAndPreservesAttemptUsage() {
        var old = now.minusDays(31);
        var view = view();
        repository.claim("old", "owner", view, old);
        repository.saveInput("old", "owner", new MarketAiInput(), old);
        view.setStatus(MarketAiStatus.RUNNING);
        repository.markSent("old", "owner", view, old);
        repository.saveReply("old", "owner", view, "paid reply", true, old);
        view.setStatus(MarketAiStatus.FAILED);
        repository.finish("old", "owner", view, old);
        repository.claim("new", "owner", view(), now);
        repository.saveInput("new", "owner", new MarketAiInput(), now);
        assertTrue(repository.findArtifact("old").isEmpty());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM market_ai_attempt", Integer.class));
        assertEquals(200L, jdbc.queryForObject("SELECT output_tokens FROM market_ai_attempt WHERE cache_key=?", Long.class, "old"));
        repository.afterPropertiesSet();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM market_ai_artifact", Integer.class));
    }

    private MarketAiView view() {
        var view = new MarketAiView();
        view.setStatus(MarketAiStatus.QUEUED);
        view.setBusinessDate(now.toLocalDate());
        view.setInputTokens(100L);
        view.setOutputTokens(200L);
        return view;
    }
}
