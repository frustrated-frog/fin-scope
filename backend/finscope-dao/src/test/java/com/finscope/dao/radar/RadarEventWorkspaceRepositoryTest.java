package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarEventWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarEventWorkspaceRepositoryTest {
    @TempDir Path tempDir;
    private RadarEventWorkspaceRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("workspace.db"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE radar_event(id INTEGER PRIMARY KEY,status TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE radar_event_user_state(event_id INTEGER PRIMARY KEY,read_at TEXT,followed INTEGER NOT NULL DEFAULT 0,disposition TEXT NOT NULL DEFAULT 'ACTIVE',last_viewed_fingerprint TEXT,updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE radar_event_observation(id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,content TEXT NOT NULL,normalized_content TEXT NOT NULL,status TEXT NOT NULL,source TEXT NOT NULL,created_at TEXT NOT NULL,completed_at TEXT,updated_at TEXT NOT NULL,UNIQUE(event_id,normalized_content,source))");
        jdbc.execute("CREATE TABLE radar_event_research_link(id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,research_run_id INTEGER NOT NULL,question_snapshot TEXT,created_at TEXT NOT NULL,UNIQUE(event_id,research_run_id))");
        jdbc.execute("CREATE TABLE radar_event_notification(id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER,notification_type TEXT NOT NULL,fingerprint TEXT NOT NULL,title TEXT NOT NULL,message TEXT,read_at TEXT,created_at TEXT NOT NULL,UNIQUE(notification_type,fingerprint))");
        repository = new RadarEventWorkspaceRepository(jdbc);
    }

    @Test
    void upsertsReadFollowAndDispositionState() {
        RadarEventWorkspace.State state = repository.updateState(7L, true, "LATER", true, "fp-1");

        assertTrue(state.isRead());
        assertTrue(state.isFollowed());
        assertEquals("LATER", state.getDisposition());
        assertEquals("fp-1", state.getLastViewedFingerprint());
        assertNotNull(state.getReadAt());
    }

    @Test
    void preservesFieldsThatAreNotPartOfAPatch() {
        repository.updateState(7L, true, "LATER", true, "fp-1");

        RadarEventWorkspace.State state = repository.updateState(7L, false, null, null, null);

        assertTrue(state.isRead());
        assertTrue(state.isFollowed());
        assertEquals("LATER", state.getDisposition());
    }

    @Test
    void returnsEachActiveFollowedEventOnceAndExcludesExpiredHistory() {
        jdbc.update("INSERT INTO radar_event(id,status) VALUES(7,'ACTIVE'),(8,'QUIET'),(9,'EXPIRED')");
        repository.updateState(7L, false, "ACTIVE", true, null);
        repository.updateState(8L, false, "ACTIVE", true, null);
        repository.updateState(9L, false, "ACTIVE", true, null);
        repository.updateState(8L, false, null, true, null);

        // The state table is the source of truth, while expired radar events no longer belong in the active follow list.
        assertEquals(Arrays.asList(8L, 7L), repository.findFollowedEventIds(20));
    }

    @Test
    void validatesDisposition() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.updateState(7L, false, "DELETED", false, null));
    }

    @Test
    void createsOneNormalizedDefaultObservationAndTracksCompletion() {
        List<RadarEventWorkspace.Observation> first = repository.ensureDefaultObservation(7L, "观察公司公告");
        List<RadarEventWorkspace.Observation> second = repository.ensureDefaultObservation(7L, "  观察公司公告  ");

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        RadarEventWorkspace.Observation done = repository.setObservationStatus(7L, first.get(0).getId(), "DONE");
        assertEquals("DONE", done.getStatus());
        assertNotNull(done.getCompletedAt());
    }

    @Test
    void addsAndDeletesUserObservationButProtectsSystemObservation() {
        RadarEventWorkspace.Observation user = repository.addObservation(7L, "跟踪下一次销量公告");
        RadarEventWorkspace.Observation system = repository.ensureDefaultObservation(7L, "观察公司公告").get(1);

        repository.deleteObservation(7L, user.getId());

        assertEquals(1, repository.findObservations(7L).size());
        assertEquals(system.getId(), repository.findObservations(7L).get(0).getId());
        assertThrows(IllegalArgumentException.class, () -> repository.deleteObservation(7L, system.getId()));
    }

    @Test
    void returnsBatchSummariesWithoutPerEventQueries() {
        repository.updateState(7L, false, "ACTIVE", true, null);
        repository.ensureDefaultObservation(7L, "观察公告");
        RadarEventWorkspace.Observation done = repository.addObservation(7L, "观察销量");
        repository.setObservationStatus(7L, done.getId(), "DONE");

        Map<Long, RadarEventWorkspace.Summary> values = repository.findSummaries(Arrays.asList(7L, 8L));

        assertTrue(values.get(7L).isFollowed());
        assertEquals(2, values.get(7L).getObservationCount());
        assertEquals(1, values.get(7L).getOpenObservationCount());
        assertFalse(values.get(8L).isFollowed());
        assertEquals(0, values.get(8L).getObservationCount());
    }
}
