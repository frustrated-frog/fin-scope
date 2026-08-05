package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarEventSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarEventSnapshotRepositoryTest {
    @TempDir Path tempDir;
    private RadarEventSnapshotRepository repository;
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 0);

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("radar.db") + "?foreign_keys=on");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE radar_event(id INTEGER PRIMARY KEY AUTOINCREMENT,event_key TEXT NOT NULL UNIQUE)");
        jdbc.execute("INSERT INTO radar_event(id,event_key) VALUES(7,'event:test')");
        jdbc.execute("CREATE TABLE radar_event_snapshot(id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,"
                + "snapshot_at TEXT NOT NULL,signal_count INTEGER NOT NULL,independent_source_count INTEGER NOT NULL,"
                + "velocity_score REAL NOT NULL,hotness_score INTEGER NOT NULL,lifecycle_state TEXT NOT NULL,"
                + "explanation TEXT,UNIQUE(event_id,snapshot_at),FOREIGN KEY(event_id) REFERENCES radar_event(id))");
        repository = new RadarEventSnapshotRepository(jdbc);
    }

    @Test
    void savesAndReadsTheLatestPriorObservation() {
        repository.save(snapshot(now.minusMinutes(30), 2, 44));
        repository.save(snapshot(now.minusMinutes(10), 4, 68));

        RadarEventSnapshot value = repository.findLatestBefore(7L, now.minusMinutes(5)).orElseThrow(AssertionError::new);

        assertEquals(4, value.getSignalCount());
        assertEquals(68, value.getHotnessScore());
        assertEquals("RISING", value.getLifecycleState());
    }

    @Test
    void doesNotReturnAnObservationAtOrAfterTheScoringMoment() {
        repository.save(snapshot(now, 5, 80));

        assertTrue(repository.findLatestBefore(7L, now).isEmpty());
    }

    private RadarEventSnapshot snapshot(LocalDateTime at, int signals, int score) {
        RadarEventSnapshot value = new RadarEventSnapshot();
        value.setEventId(7L); value.setSnapshotAt(at); value.setSignalCount(signals);
        value.setIndependentSourceCount(2); value.setVelocityScore(0.8D); value.setHotnessScore(score);
        value.setLifecycleState("RISING"); value.setExplanation("test"); return value;
    }
}
