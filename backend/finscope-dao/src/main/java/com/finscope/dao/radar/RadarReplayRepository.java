package com.finscope.dao.radar;

import com.finscope.common.enums.radar.RadarReplayChange;
import com.finscope.domain.radar.RadarReplayFrame;
import com.finscope.domain.radar.RadarReplayItem;
import com.finscope.domain.radar.RadarRiverBucket;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 仅保存实际发布批次；正文版本复用，排名按批次保存，不读取当前 Redis 重建历史。 */
@Repository
@DependsOn("databaseInitializer")
public class RadarReplayRepository implements InitializingBean {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS");
    private static final String CONTENT = "v.event_id,v.event_key,v.title,v.summary,v.category,s.observed_at";
    @Resource
    private JdbcTemplate jdbc;

    @Override
    public void afterPropertiesSet() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS radar_replay_frame (id INTEGER PRIMARY KEY AUTOINCREMENT,run_key TEXT NOT NULL UNIQUE,published_at TEXT NOT NULL,event_count INTEGER NOT NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_replay_frame_time ON radar_replay_frame(published_at,id)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS radar_replay_version (hash TEXT PRIMARY KEY,event_id TEXT NOT NULL,event_key TEXT NOT NULL,title TEXT NOT NULL,summary TEXT NOT NULL,category TEXT NOT NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_replay_version_event ON radar_replay_version(event_key)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS radar_replay_seen (event_key TEXT PRIMARY KEY,version_hash TEXT NOT NULL,observed_at TEXT NOT NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_replay_seen_time ON radar_replay_seen(observed_at,event_key)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS radar_replay_rank (frame_id INTEGER NOT NULL,position INTEGER NOT NULL,version_hash TEXT NOT NULL,score INTEGER NOT NULL,sources INTEGER NOT NULL,reports INTEGER NOT NULL,lifecycle TEXT,change_code TEXT NOT NULL,rank_delta INTEGER NOT NULL,PRIMARY KEY(frame_id,position))");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_replay_rank_version ON radar_replay_rank(version_hash)");
    }

    @Transactional(rollbackFor = Exception.class)
    public void savePublished(String runKey, LocalDateTime at, List<RadarReplayItem> observed, List<RadarReplayItem> ranking) {
        if (runKey == null || runKey.isBlank() || at == null || ranking.size() > 50 || observed.size() > 20000) {
            throw new IllegalArgumentException("资讯回放批次参数无效");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM radar_replay_frame WHERE run_key=?", Integer.class, runKey) > 0) {
            return;
        }
        Map<String, Integer> previous = new HashMap<>();
        jdbc.query("SELECT v.event_key,r.position FROM radar_replay_rank r JOIN radar_replay_version v ON v.hash=r.version_hash "
                + "WHERE frame_id=(SELECT id FROM radar_replay_frame ORDER BY published_at DESC,id DESC LIMIT 1)", rs -> {
            previous.put(rs.getString(1), rs.getInt(2));
        });
        if (jdbc.update("INSERT INTO radar_replay_frame(run_key,published_at,event_count) VALUES(?,?,?)", runKey, time(at), ranking.size()) != 1) {
            throw new IllegalStateException("资讯回放批次保存失败");
        }
        Long frameId = jdbc.queryForObject("SELECT id FROM radar_replay_frame WHERE run_key=?", Long.class, runKey);
        Set<String> known = new HashSet<>(jdbc.queryForList("SELECT event_key FROM radar_replay_seen", String.class));
        for (RadarReplayItem item : observed) {
            // 已收录事件不重复保存正文；只有首次进入系统及入榜时的内容才产生版本。
            if (known.add(item.getEventKey())) {
                String hash = saveVersion(item);
                jdbc.update("INSERT OR IGNORE INTO radar_replay_seen VALUES(?,?,?)", item.getEventKey(), hash, time(at));
            }
        }
        for (int index = 0; index < ranking.size(); index++) {
            RadarReplayItem item = ranking.get(index);
            String hash = saveVersion(item);
            Integer before = previous.get(item.getEventKey());
            Integer pastEntries = jdbc.queryForObject("SELECT COUNT(*) FROM radar_replay_rank r JOIN radar_replay_version v ON v.hash=r.version_hash WHERE v.event_key=?", Integer.class, item.getEventKey());
            int delta = before == null ? 0 : before - index - 1;
            RadarReplayChange change = before == null ? (pastEntries > 0 ? RadarReplayChange.RETURNING : RadarReplayChange.FIRST_ENTRY)
                    : delta > 0 ? RadarReplayChange.RISING : delta < 0 ? RadarReplayChange.FALLING : RadarReplayChange.STEADY;
            if (jdbc.update("INSERT INTO radar_replay_rank VALUES(?,?,?,?,?,?,?,?,?)", frameId, index + 1, hash,
                    item.getHotspotScore(), item.getSourceCount(), item.getReportCount(), item.getLifecycle(), change.name(), delta) != 1) {
                throw new IllegalStateException("资讯历史排名保存失败");
            }
        }
        prune(at.minusDays(7));
    }

    private String saveVersion(RadarReplayItem item) {
        if (item.getEventId() == null || item.getEventKey() == null || item.getTitle() == null) {
            throw new IllegalArgumentException("资讯事件身份和标题不能为空");
        }
        String summary = item.getSummary() == null ? "" : item.getSummary();
        String category = item.getCategory() == null ? "OTHER" : item.getCategory();
        String hash;
        try {
            String content = String.join("\u0000", item.getEventId(), item.getEventKey(), item.getTitle(), summary, category);
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前环境不支持资讯版本摘要", error);
        }
        jdbc.update("INSERT OR IGNORE INTO radar_replay_version VALUES(?,?,?,?,?,?)", hash, item.getEventId(), item.getEventKey(), item.getTitle(), summary, category);
        return hash;
    }

    public List<RadarReplayFrame> frames(LocalDate date) {
        return jdbc.query("SELECT * FROM radar_replay_frame WHERE published_at>=? AND published_at<? ORDER BY published_at,id LIMIT 3000", frameMapper(), time(date.atStartOfDay()), time(date.plusDays(1).atStartOfDay()));
    }

    public Optional<RadarReplayFrame> frame(long id) {
        return jdbc.query("SELECT * FROM radar_replay_frame WHERE id=?", frameMapper(), id).stream().findFirst();
    }

    public List<RadarRiverBucket> buckets(LocalDate date) {
        return jdbc.query("SELECT substr(s.observed_at,1,14)||CASE WHEN CAST(substr(s.observed_at,15,2) AS INTEGER)<30 THEN '00' ELSE '30' END AS bucket,"
                + "SUM(v.category='FINANCE') finance,SUM(v.category='TECHNOLOGY') technology,SUM(v.category='POLITICS') politics,"
                + "SUM(v.category NOT IN ('FINANCE','TECHNOLOGY','POLITICS')) other FROM radar_replay_seen s "
                + "JOIN radar_replay_version v ON v.hash=s.version_hash WHERE s.observed_at>=? AND s.observed_at<? GROUP BY bucket ORDER BY bucket", (rs, i) -> {
            RadarRiverBucket bucket = new RadarRiverBucket();
            bucket.setStart(LocalDateTime.parse(rs.getString("bucket") + ":00"));
            bucket.setFinance(rs.getInt("finance"));
            bucket.setTechnology(rs.getInt("technology"));
            bucket.setPolitics(rs.getInt("politics"));
            bucket.setOther(rs.getInt("other"));
            return bucket;
        }, time(date.atStartOfDay()), time(date.plusDays(1).atStartOfDay()));
    }

    public List<RadarReplayItem> ranking(long frameId, String category) {
        return jdbc.query("SELECT " + CONTENT + ",r.position,r.score,r.sources,r.reports,r.lifecycle,r.change_code,r.rank_delta "
                + "FROM radar_replay_rank r JOIN radar_replay_version v ON v.hash=r.version_hash "
                + "LEFT JOIN radar_replay_seen s ON s.event_key=v.event_key WHERE r.frame_id=? AND (?='ALL' OR v.category=?) ORDER BY r.position", (rs, i) -> {
            RadarReplayItem item = content(rs);
            item.setRank(rs.getInt("position"));
            item.setHotspotScore(rs.getInt("score"));
            item.setSourceCount(rs.getInt("sources"));
            item.setReportCount(rs.getInt("reports"));
            item.setLifecycle(rs.getString("lifecycle"));
            item.setChange(RadarReplayChange.valueOf(rs.getString("change_code")));
            item.setRankDelta(rs.getInt("rank_delta"));
            return item;
        }, frameId, category, category);
    }

    public List<RadarReplayItem> observed(LocalDateTime from, LocalDateTime until, String category, int page) {
        return jdbc.query("SELECT " + CONTENT + " FROM radar_replay_seen s JOIN radar_replay_version v ON v.hash=s.version_hash "
                + "WHERE s.observed_at>=? AND s.observed_at<=? AND (?='ALL' OR v.category=?) ORDER BY s.observed_at,s.event_key LIMIT 50 OFFSET ?",
                (rs, i) -> content(rs), time(from), time(until), category, category, page * 50);
    }

    public int observedCount(LocalDateTime from, LocalDateTime until, String category) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM radar_replay_seen s JOIN radar_replay_version v ON v.hash=s.version_hash "
                + "WHERE s.observed_at>=? AND s.observed_at<=? AND (?='ALL' OR v.category=?)", Integer.class, time(from), time(until), category, category);
    }

    private RadarReplayItem content(ResultSet rs) throws SQLException {
        RadarReplayItem item = new RadarReplayItem();
        item.setEventId(rs.getString("event_id"));
        item.setEventKey(rs.getString("event_key"));
        item.setTitle(rs.getString("title"));
        item.setSummary(rs.getString("summary"));
        item.setCategory(rs.getString("category"));
        String observedAt = rs.getString("observed_at");
        item.setObservedAt(observedAt == null ? null : LocalDateTime.parse(observedAt));
        return item;
    }

    private RowMapper<RadarReplayFrame> frameMapper() {
        return (rs, i) -> {
            RadarReplayFrame frame = new RadarReplayFrame();
            frame.setId(rs.getString("id"));
            frame.setPublishedAt(LocalDateTime.parse(rs.getString("published_at")));
            frame.setEventCount(rs.getInt("event_count"));
            return frame;
        };
    }

    private void prune(LocalDateTime before) {
        jdbc.update("DELETE FROM radar_replay_rank WHERE frame_id IN (SELECT id FROM radar_replay_frame WHERE published_at<?)", time(before));
        jdbc.update("DELETE FROM radar_replay_frame WHERE published_at<?", time(before));
        jdbc.update("DELETE FROM radar_replay_seen WHERE observed_at<? AND event_key NOT IN (SELECT v.event_key FROM radar_replay_rank r JOIN radar_replay_version v ON v.hash=r.version_hash)", time(before));
        jdbc.update("DELETE FROM radar_replay_version WHERE hash NOT IN (SELECT version_hash FROM radar_replay_seen UNION SELECT version_hash FROM radar_replay_rank)");
    }

    private String time(LocalDateTime value) {
        return TIME.format(value);
    }
}
