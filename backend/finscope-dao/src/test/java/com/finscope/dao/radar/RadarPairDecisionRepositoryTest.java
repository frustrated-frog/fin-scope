package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarPairDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarPairDecisionRepositoryTest {
    @TempDir Path tempDir;
    private RadarPairDecisionRepository repository;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("pair-cache.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE radar_pair_decision(pair_key TEXT PRIMARY KEY,left_fingerprint TEXT NOT NULL,"
                + "right_fingerprint TEXT NOT NULL,same_event INTEGER NOT NULL,confidence REAL NOT NULL,"
                + "reason TEXT,decision_source TEXT NOT NULL,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)");
        repository = new RadarPairDecisionRepository(jdbc);
    }

    @Test
    void pairKeyIsSymmetricAndDecisionRoundTrips() {
        String key = RadarPairDecision.pairKey("fingerprint-z", "fingerprint-a");
        RadarPairDecision value = new RadarPairDecision();
        value.setPairKey(key);
        value.setLeftFingerprint("fingerprint-a");
        value.setRightFingerprint("fingerprint-z");
        value.setSameEvent(true);
        value.setConfidence(0.91D);
        value.setReason("同一主体、动作和时间窗口");
        value.setDecisionSource("AGENT");
        value.setUpdatedAt(LocalDateTime.of(2026, 7, 31, 18, 0));

        repository.save(value);

        RadarPairDecision stored = repository.find(
                RadarPairDecision.pairKey("fingerprint-a", "fingerprint-z")).orElseThrow(AssertionError::new);
        assertEquals(key, RadarPairDecision.pairKey("fingerprint-z", "fingerprint-a"));
        assertTrue(stored.isSameEvent());
        assertEquals(0.91D, stored.getConfidence(), 0.001D);
        assertEquals("同一主体、动作和时间窗口", stored.getReason());
        assertEquals("AGENT", stored.getDecisionSource());
    }
}
