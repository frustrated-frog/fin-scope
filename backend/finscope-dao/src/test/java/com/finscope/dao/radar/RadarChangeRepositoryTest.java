package com.finscope.dao.radar;

import com.finscope.common.enums.radar.RadarChangeKind;
import com.finscope.dao.news.NewsAiRepository;
import com.finscope.domain.radar.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RadarChangeRepositoryTest {
    @TempDir Path dir;
    private JdbcTemplate jdbc;
    private RadarChangeRepository changes;
    private RadarReplayRepository replay;
    private NewsAiRepository ai;
    private final LocalDateTime today = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).withHour(9).withMinute(0).withSecond(0).withNano(0);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS");

    @BeforeEach
    void setup() {
        var ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dir.resolve("changes.db"));
        jdbc = new JdbcTemplate(ds);
        changes = new RadarChangeRepository();
        ReflectionTestUtils.setField(changes, "jdbc", jdbc);
        changes.afterPropertiesSet();
        replay = new RadarReplayRepository();
        ReflectionTestUtils.setField(replay, "jdbc", jdbc);
        ReflectionTestUtils.setField(replay, "changes", changes);
        replay.afterPropertiesSet();
        jdbc.execute("CREATE TABLE news_report(id TEXT PRIMARY KEY,content_version INTEGER,title TEXT,content TEXT)");
        jdbc.execute("CREATE TABLE news_report_version(report_id TEXT,version INTEGER,detected_at TEXT)");
        jdbc.execute("CREATE TABLE news_ai_report(report_id TEXT,content_version INTEGER,event_key TEXT,content_key TEXT,relation TEXT)");
        ai = new NewsAiRepository();
        ReflectionTestUtils.setField(ai, "jdbc", jdbc);
        ai.afterPropertiesSet();
    }

    @Test
    void accumulatesObservedGrowthOffTheLeaderboardAndCollapsesReprints() {
        publish(0, item("r", 1, 1), item("a", 1, 1));
        publish(40, item("r", 1, 1), item("a", 1, 1));
        publish(44, item("r", 1, 8), item("a", 2, 2));
        publish(49, item("r", 1, 15), item("a", 3, 3), item("new", 1, 1));
        publish(50, item("r", 1, 15), item("a", 3, 3), item("new", 1, 1));
        var list = list(30, 60);
        assertEquals(List.of(RadarChangeKind.SPREAD, RadarChangeKind.NEW, RadarChangeKind.COVERAGE), list.stream().map(RadarRecentChange::getKind).toList());
        assertEquals(1, list.getFirst().getSourcesBefore());
        assertEquals(3, list.getFirst().getSourcesAfter());
        assertEquals(14, list.getLast().getAddedReports());
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM radar_change_delta", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM radar_replay_rank", Integer.class));
        assertTrue(list(51, 81).isEmpty());
    }

    @Test
    void confirmedNewFactWinsEvenWithOneSourceAndOldOrUnverifiedVersionsNeverLeak() {
        publish(0, item("a", 1, 1));
        publish(40, item("a", 1, 1));
        publish(45, item("a", 1, 2));
        bind("a", "贷款承诺函已取得", 46);
        assertEquals(RadarChangeKind.COVERAGE, list(30, 45).getFirst().getKind());
        var rows = list(30, 60);
        assertEquals(1, rows.size());
        assertEquals(RadarChangeKind.PROGRESS, rows.getFirst().getKind());
        assertEquals("贷款承诺函已取得", rows.getFirst().getNewFact());
        jdbc.update("UPDATE news_report SET content_version=2");
        assertEquals(RadarChangeKind.COVERAGE, list(30, 60).getFirst().getKind());
        jdbc.update("UPDATE news_report SET content_version=1");
        jdbc.update("UPDATE news_report_version SET detected_at=?", today.toString());
        assertEquals(RadarChangeKind.COVERAGE, list(30, 60).getFirst().getKind());
    }

    @Test
    void oldBindingRestoresOnlyAnUnambiguousSavedFactAndOtherwiseUsesReportTitle() {
        publish(0, item("a", 1, 1));
        bind("a", null, 45);
        assertEquals("后续公告", list(30, 60).getFirst().getReportTitle());
        jdbc.update("INSERT INTO news_ai_event VALUES('a',1,json_object('analysis',json_object('summary',?, 'facts',json_array('新事实'))),?)",
                "已有事实\n最新进展：新事实", today.plusMinutes(45).toString());
        jdbc.update("INSERT INTO news_ai_content(cache_key,content_hash,state,updated_at) VALUES('ambiguous','other','READY',?)",today.plusMinutes(45).toString());
        jdbc.update("INSERT INTO news_ai_binding SELECT 'ambiguous',payload FROM news_ai_binding WHERE cache_key='job'");
        ai.afterPropertiesSet();
        assertEquals(RadarChangeKind.COVERAGE, list(30, 60).getFirst().getKind());
        jdbc.update("DELETE FROM news_ai_content WHERE cache_key='ambiguous'");
        ai.afterPropertiesSet();
        ai.afterPropertiesSet();
        assertEquals("新事实", list(30, 60).getFirst().getNewFact());
    }

    @Test
    void reappearingEventsDoNotGetAnInventedSourceBaseline() {
        publish(0, item("a", 1, 1));
        publish(40);
        publish(45, item("a", 4, 8));
        assertTrue(list(30, 60).isEmpty());
    }

    @Test
    void legacyAndAiIdentitiesCollapseOnlyAfterTheirExactReportHasBeenBound() {
        var local = item("local", 1, 1);
        local.setTitle("后续公告");
        local.setSummary("同一篇公告原文");
        var different = item("different", 1, 1);
        different.setTitle("后续公告");
        different.setSummary("相同标题但不同正文");
        publish(40, local, different, item("news-ai:a", 1, 1));
        bind("news-ai:a", "具体进展", 45);
        assertEquals(3, list(30, 44).size());
        var after = list(30, 60);
        assertEquals(2, after.size());
        assertEquals(RadarChangeKind.PROGRESS, after.getFirst().getKind());
        assertEquals("相同标题但不同正文", after.getLast().getSummary());
    }

    private void bind(String event, String fact, int minute) {
        String at = today.plusMinutes(minute).toString();
        jdbc.update("INSERT INTO news_ai_content(cache_key,content_hash,state,updated_at) VALUES('job','occurrence','READY',?)", at);
        jdbc.update("INSERT INTO news_ai_binding VALUES('job',json_object('eventKey',?,'relation','PROGRESS','analysis',json_object('summary','报道摘要')))",event);
        if (fact != null) {
            jdbc.update("UPDATE news_ai_binding SET payload=json_set(payload,'$.newFacts',json_array(?))", fact);
        }
        jdbc.update("INSERT INTO news_report VALUES('original',1,'后续公告','同一篇公告原文')");
        jdbc.update("INSERT INTO news_ai_report VALUES('original',1,?,'occurrence','PROGRESS')",event);
        jdbc.update("INSERT INTO news_report_version VALUES('original',1,?)",at);
    }

    private List<RadarRecentChange> list(int from, int until) {
        var start = today.plusMinutes(from);
        var end = today.plusMinutes(until);
        return changes.recent(TIME.format(start), TIME.format(end), start.toString(), end.toString(), "ALL", 40, 0);
    }
    private void publish(int minute, RadarReplayItem... items) {
        replay.savePublished("r"+minute, today.plusMinutes(minute), List.of(items), List.of());
    }
    private RadarReplayItem item(String key, int sources, int reports) {
        var item = new RadarReplayItem();
        item.setEventKey(key);
        item.setEventId("9007199254740993" + key);
        item.setTitle("事件" + key);
        item.setSourceCount(sources);
        item.setReportCount(reports);
        return item;
    }
}
