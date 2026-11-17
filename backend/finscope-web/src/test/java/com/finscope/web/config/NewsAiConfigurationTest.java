package com.finscope.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.news.NewsAiRepository;
import com.finscope.dao.news.NewsWindowSchemaMigrator;
import com.finscope.domain.news.ai.NewsAiProviderConfig;
import com.finscope.rpc.llm.NewsAiLunaClient;
import com.finscope.service.news.NewsAiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.sqlite.SQLiteDataSource;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class NewsAiConfigurationTest {
    @Test
    void bindsProductionDefaultsWithoutAKeyAndCreatesSchemaInDependencyOrder(@TempDir Path directory) {
        new ApplicationContextRunner().withUserConfiguration(Config.class, NewsAiConfiguration.class)
                .withBean("newsWindowSchemaMigrator", NewsWindowSchemaMigrator.class)
                .withBean(DataSource.class, () -> {
                    var source = new SQLiteDataSource();
                    source.setUrl("jdbc:sqlite:" + directory.resolve("news.db"));
                    return source;
                }).withInitializer(context -> {
                    try {
                        for (var source : new YamlPropertySourceLoader().load("news-ai-test", new ClassPathResource("application.yml"))) {
                            context.getEnvironment().getPropertySources().addLast(source);
                        }
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                }).run(context -> {
                    assertNull(context.getStartupFailure());
                    var properties = context.getBean(NewsAiProperties.class);
                    var provider = context.getBean(NewsAiProviderConfig.class);
                    assertTrue(properties.isEnabled());
                    assertFalse(properties.isBackfillExisting());
                    assertEquals(6, properties.getBatchSize());
                    assertEquals(1_500_000L, properties.getDailyBudgetMicros());
                    assertEquals("gpt-6-luna", provider.getModel());
                    assertFalse(context.getBean(NewsAiLunaClient.class).isConfigured());
                    var repository = context.getBean(NewsAiRepository.class);
                    var now = LocalDateTime.parse("2026-11-17T12:00:00");
                    assertEquals(now, repository.observeMode(false, now));
                    repository.prune(now);
                    assertEquals(0, ((Number) repository.dailyUsage(now).get("calls")).intValue());
                    assertEquals(0, context.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM news_ai_report", Integer.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NewsAiProperties.class)
    @EnableTransactionManagement
    @Import({NewsAiRepository.class, NewsAiLunaClient.class})
    static class Config {
        @Bean(name = "databaseInitializer")
        Object databaseInitializer() {
            return new Object();
        }

        @Bean
        JdbcTemplate jdbc(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        ObjectMapper mapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
