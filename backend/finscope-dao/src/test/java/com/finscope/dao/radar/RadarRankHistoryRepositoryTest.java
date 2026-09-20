package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarRankPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RadarRankHistoryRepositoryTest {
    @TempDir Path temp;

    @Test
    void sameBucketReplacesItsRecordAndHistoryRemainsSeparateFromCurrentCache() {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + temp.resolve("rank.db"));
        RadarRankHistoryRepository repository = new RadarRankHistoryRepository();
        ReflectionTestUtils.setField(repository, "jdbc", new JdbcTemplate(source));
        repository.afterPropertiesSet();
        repository.afterPropertiesSet();
        LocalDateTime now = LocalDateTime.parse("2026-09-21T10:00:00");
        RadarRankPoint point = new RadarRankPoint();
        point.setEventKey("stable-event");
        point.setObservedAt(now);
        point.setRankPosition(4);
        repository.save(List.of(point));
        point.setRankPosition(2);
        repository.save(List.of(point));
        assertEquals(1, repository.history("stable-event", now.minusDays(7)).size());
        assertEquals(2, repository.history("stable-event", now.minusDays(7)).get(0).getRankPosition());
        point.setObservedAt(now.plusMinutes(30));
        point.setRankPosition(1);
        repository.save(List.of(point));
        assertEquals(2, repository.history("stable-event", now.minusDays(7)).size());
        repository.prune(now.plusMinutes(1));
        assertEquals(1, repository.history("stable-event", now.minusDays(7)).size());
    }
}
