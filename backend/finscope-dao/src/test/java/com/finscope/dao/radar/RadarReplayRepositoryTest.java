package com.finscope.dao.radar;

import com.finscope.common.enums.radar.RadarReplayChange;
import com.finscope.domain.radar.RadarReplayItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RadarReplayRepositoryTest {
    @TempDir Path directory;
    private RadarReplayRepository repository;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private final LocalDateTime now = LocalDateTime.parse("2026-11-17T10:00:00");

    @BeforeEach
    void setup() {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("replay.db"));
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        repository = new RadarReplayRepository();
        ReflectionTestUtils.setField(repository, "jdbc", jdbc);
        repository.afterPropertiesSet();
        repository.afterPropertiesSet();
    }

    @Test
    void freezesPublishedTextAndRanksAndCountsLateArrivalOnlyWhenItWasObserved() {
        RadarReplayItem a = item("6140854697134002116", "旧标题", "FINANCE");
        RadarReplayItem b = item("2", "科技发布", "TECHNOLOGY");
        save("one", now, List.of(a, b), List.of(b, a));
        String first = repository.frames(now.toLocalDate()).get(0).getId();
        a.setTitle("后来才出现的标题");
        a.setSummary("后来才出现的摘要");
        save("two", now.plusMinutes(10), List.of(a, b), List.of(a));
        RadarReplayItem late = item("3", "昨日发布但刚刚收到", "POLITICS");
        save("three", now.plusMinutes(35), List.of(a, b, late), List.of(a, b));
        var old = repository.ranking(Long.parseLong(first), "ALL");
        assertEquals("旧标题", old.get(1).getTitle());
        assertEquals("6140854697134002116", old.get(1).getEventId());
        assertEquals(2, old.get(1).getRank());
        var frames = repository.frames(now.toLocalDate());
        assertEquals(RadarReplayChange.RISING, repository.ranking(Long.parseLong(frames.get(1).getId()), "ALL").get(0).getChange());
        assertEquals(RadarReplayChange.RETURNING, repository.ranking(Long.parseLong(frames.get(2).getId()), "ALL").get(1).getChange());
        var buckets = repository.buckets(now.toLocalDate());
        assertEquals(2, buckets.size());
        assertEquals(1, buckets.get(0).getFinance());
        assertEquals(1, buckets.get(0).getTechnology());
        assertEquals(1, buckets.get(1).getPolitics());
        assertEquals(2, repository.observedCount(now, now.plusMinutes(10), "ALL"));
        assertEquals(1, repository.observed(now, now.plusMinutes(10), "FINANCE", 0).size());
        assertEquals("旧标题", repository.observed(now, now.plusMinutes(10), "FINANCE", 0).get(0).getTitle());
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM radar_replay_version", Integer.class));
        save("one", now.plusHours(1), List.of(a), List.of(a));
        assertEquals(3, repository.frames(now.toLocalDate()).size());
        assertEquals("旧标题", repository.ranking(Long.parseLong(first), "FINANCE").get(0).getTitle());
    }

    @Test
    void rollsBackHalfWrittenFramesAndPrunesUnreferencedVersions() {
        RadarReplayItem a = item("1", "有效事件", "FINANCE");
        RadarReplayItem invalid = item("2", "无效事件", "FINANCE");
        invalid.setEventKey(null);
        assertThrows(IllegalArgumentException.class, () -> save("broken", now, List.of(a, invalid), List.of(a)));
        assertTrue(repository.frames(now.toLocalDate()).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM radar_replay_seen", Integer.class));
        save("old", now.minusDays(8), List.of(a), List.of(a));
        save("new", now, List.of(item("3", "新事件", "POLITICS")), List.of());
        assertTrue(repository.frames(now.minusDays(8).toLocalDate()).isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM radar_replay_version", Integer.class));
    }

    @Test
    void retainsFirstSeenIdentityForLongLivedEventsOutsideTheTopTwenty() {
        RadarReplayItem active = item("1", "长期跟踪事件", "FINANCE");
        save("old", now.minusDays(8), List.of(active), List.of());
        save("still-active", now, List.of(active), List.of());
        save("next", now.plusMinutes(5), List.of(active), List.of());
        assertTrue(repository.buckets(now.toLocalDate()).isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM radar_replay_seen", Integer.class));
        save("expired", now.plusMinutes(10), List.of(), List.of());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM radar_replay_seen", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM radar_replay_version", Integer.class));
    }

    @Test
    void stablePagesContainNoEventsArrivingAfterTheSelectedFrame() {
        var events = java.util.stream.IntStream.range(0, 120).mapToObj(i -> item(String.valueOf(i), "事件" + i, "OTHER")).toList();
        save("many", now, events, events.subList(0, 20));
        assertEquals(50, repository.observed(now, now, "ALL", 0).size());
        assertEquals(50, repository.observed(now, now, "ALL", 1).size());
        assertEquals(20, repository.observed(now, now, "ALL", 2).size());
        save("late", now.plusSeconds(1), List.of(item("late", "未来事件", "FINANCE")), List.of());
        assertEquals(120, repository.observedCount(now, now, "ALL"));
        assertEquals(0, repository.observedCount(now, now, "FINANCE"));
    }

    private void save(String key, LocalDateTime at, List<RadarReplayItem> observed, List<RadarReplayItem> ranks) {
        transaction.executeWithoutResult(status -> repository.savePublished(key, at, observed, ranks));
    }
    private RadarReplayItem item(String id, String title, String category) {
        RadarReplayItem item = new RadarReplayItem();
        item.setEventId(id); item.setEventKey("event:" + id); item.setTitle(title); item.setSummary("当时摘要");
        item.setCategory(category); item.setHotspotScore(85); item.setSourceCount(2); item.setReportCount(3);
        return item;
    }
}
