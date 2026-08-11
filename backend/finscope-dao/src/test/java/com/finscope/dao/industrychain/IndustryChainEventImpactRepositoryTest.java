package com.finscope.dao.industrychain;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.industrychain.IndustryChain;
import com.finscope.domain.industrychain.IndustryChainEventImpact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustryChainEventImpactRepositoryTest {
    private JdbcTemplate jdbc;
    private IndustryChainEventImpactRepository repository;
    private Long chainId;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("finscope-industry-chain-event-test");
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        jdbc = new JdbcTemplate(source);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        IndustryChainRepository chainRepository = new IndustryChainRepository();
        ReflectionTestUtils.setField(chainRepository, "jdbcTemplate", jdbc);
        IndustryChain chain = chainRepository.createChain("AI 算力", "ai 算力");
        chainId = chain.getId();
        jdbc.update("INSERT INTO radar_event(event_key,canonical_title,status,first_seen_at,last_seen_at,updated_at) VALUES(?,?,?,?,?,?)",
                "event:hbm", "HBM 涨价", "ACTIVE", "2026-08-10T09:00:00", "2026-08-11T09:00:00", "2026-08-11T09:00:00");
        repository = new IndustryChainEventImpactRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void upsertsOneRelationshipAndPreservesPathOrder() {
        Long eventId = jdbc.queryForObject("SELECT id FROM radar_event WHERE event_key='event:hbm'", Long.class);
        IndustryChainEventImpact first = impact(eventId, "product:hbm",
                Arrays.asList("product:hbm", "product:server"));
        IndustryChainEventImpact updated = impact(eventId, "product:hbm",
                Arrays.asList("product:hbm", "product:server", "stage:application"));

        assertTrue(repository.upsert(first, LocalDateTime.of(2026, 8, 11, 10, 0)));
        assertFalse(repository.upsert(updated, LocalDateTime.of(2026, 8, 11, 11, 0)));

        List<IndustryChainEventImpact> restored = repository.findByChainId(chainId);
        assertEquals(1, restored.size());
        assertEquals(Arrays.asList("product:hbm", "product:server", "stage:application"),
                restored.get(0).getPathNodeKeys());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM industry_chain_event_impact", Integer.class));
    }

    private IndustryChainEventImpact impact(Long eventId, String directNodeKey, List<String> path) {
        IndustryChainEventImpact impact = new IndustryChainEventImpact();
        impact.setChainId(chainId);
        impact.setRadarEventId(eventId);
        impact.setDirectNodeKey(directNodeKey);
        impact.setDirection(IndustryChainEventImpact.Direction.POSITIVE);
        impact.setMechanism(IndustryChainEventImpact.Mechanism.PRICE);
        impact.setHorizon(IndustryChainEventImpact.Horizon.SHORT);
        impact.setConfidence(IndustryChainEventImpact.Confidence.HIGH);
        impact.setImpactSummary("价格上涨沿服务器链路传导");
        impact.setAnalysisVersion("RULES_V1");
        impact.setPathNodeKeys(path);
        return impact;
    }
}
