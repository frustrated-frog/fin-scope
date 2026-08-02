package com.finscope.dao.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.radar.RadarEventInterpretation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RadarEventInterpretationRepositoryTest {
    @TempDir Path tempDir;
    private RadarEventInterpretationRepository repository;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("interpretation.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE radar_event_interpretation("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,event_fingerprint TEXT NOT NULL,"
                + "status TEXT NOT NULL,result_json TEXT,failure_code TEXT,failure_message TEXT,duration_ms INTEGER,"
                + "created_at TEXT NOT NULL,started_at TEXT,completed_at TEXT,UNIQUE(event_id,event_fingerprint))");
        repository = new RadarEventInterpretationRepository(jdbc, new ObjectMapper());
    }

    @Test
    void reusesOneRowForTheSameEventVersion() {
        RadarEventInterpretation first = repository.saveQueued(7L, "fingerprint-a");
        RadarEventInterpretation second = repository.saveQueued(7L, "fingerprint-a");

        assertEquals(first.getId(), second.getId());
        assertEquals(1, repository.findHistory(7L, 10).size());
    }

    @Test
    void roundTripsCompletedStructuredResult() {
        RadarEventInterpretation value = repository.saveQueued(7L, "fingerprint-a");
        RadarEventInterpretation.Result result = new RadarEventInterpretation.Result();
        result.setFactSummary("公司发布新产品");
        result.setNewDevelopment("新增量产时间");
        result.setWhyItMatters("可能影响供应链订单预期");
        result.setImpactChain(Arrays.asList("产品发布→量产验证→供应链订单"));
        result.setUncertainties(Arrays.asList("价格尚未披露"));
        result.setNextObservations(Arrays.asList("观察公司公告"));
        result.setEvidenceRefs(Arrays.asList("signal:1"));
        value.setStatus("SUCCESS");
        value.setResult(result);
        repository.update(value);

        RadarEventInterpretation stored = repository.findByEventFingerprint(7L, "fingerprint-a").get();
        assertNotNull(stored.getResult());
        assertEquals("公司发布新产品", stored.getResult().getFactSummary());
        assertEquals(Arrays.asList("signal:1"), stored.getResult().getEvidenceRefs());
    }

    @Test
    void findsTheLatestInterpretationForEachRequestedEvent() {
        repository.saveQueued(7L, "fingerprint-old");
        RadarEventInterpretation latest = repository.saveQueued(7L, "fingerprint-new");
        RadarEventInterpretation other = repository.saveQueued(8L, "fingerprint-other");

        Map<Long, RadarEventInterpretation> values = repository.findLatestByEventIds(Arrays.asList(7L, 8L));

        assertEquals(latest.getId(), values.get(7L).getId());
        assertEquals(other.getId(), values.get(8L).getId());
    }
}
