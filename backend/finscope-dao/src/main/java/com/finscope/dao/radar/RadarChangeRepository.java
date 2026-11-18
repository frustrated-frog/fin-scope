package com.finscope.dao.radar;

import com.finscope.common.enums.radar.RadarChangeKind;
import com.finscope.domain.radar.RadarRecentChange;
import com.finscope.domain.radar.RadarReplayItem;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

/** 与回放共用发布事务。按事件只写变化量，重复发布不复制所有事件计数。 */
@Repository
@DependsOn("databaseInitializer")
public class RadarChangeRepository implements InitializingBean {
    @Resource
    private JdbcTemplate jdbc;

    @Override
    public void afterPropertiesSet() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS radar_change_state(event_key TEXT PRIMARY KEY,sources INTEGER NOT NULL,reports INTEGER NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS radar_change_epoch(id INTEGER PRIMARY KEY CHECK(id=1),first_frame INTEGER NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS radar_change_delta(frame_id INTEGER NOT NULL,event_key TEXT NOT NULL,previous_at TEXT NOT NULL,"
                + "observed_at TEXT NOT NULL,old_sources INTEGER NOT NULL,sources INTEGER NOT NULL,old_reports INTEGER NOT NULL,reports INTEGER NOT NULL,PRIMARY KEY(frame_id,event_key))");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_radar_delta_time ON radar_change_delta(observed_at,event_key)");
    }

    private record Counts(int sources, int reports) {}

    @Transactional(rollbackFor = Exception.class)
    public void record(long frame, String at, List<RadarReplayItem> events, String expiry) {
        String previousAt = jdbc.queryForObject("SELECT MAX(published_at) FROM radar_replay_frame WHERE id<?", String.class, frame);
        jdbc.update("INSERT OR IGNORE INTO radar_change_epoch VALUES(1,?)", frame);
        Map<String, Counts> previous = new HashMap<>();
        jdbc.query("SELECT * FROM radar_change_state", rs -> {
            previous.put(rs.getString(1), new Counts(rs.getInt(2), rs.getInt(3)));
        });
        List<Object[]> updates = new ArrayList<>();
        List<Object[]> deltas = new ArrayList<>();
        for (RadarReplayItem event : events) {
            String key = event.getEventKey();
            Counts old = previous.remove(key);
            Counts current = new Counts(event.getSourceCount(), event.getReportCount());
            if (current.equals(old)) {
                continue;
            }
            updates.add(new Object[]{key, current.sources(), current.reports()});
            if (old != null && previousAt != null) {
                deltas.add(new Object[]{frame, key, previousAt, at, old.sources(), current.sources(), old.reports(), current.reports()});
            }
        }
        jdbc.batchUpdate("INSERT INTO radar_change_delta VALUES(?,?,?,?,?,?,?,?)", deltas);
        jdbc.batchUpdate("INSERT INTO radar_change_state VALUES(?,?,?) ON CONFLICT(event_key) DO UPDATE SET sources=excluded.sources,reports=excluded.reports", updates);
        jdbc.batchUpdate("DELETE FROM radar_change_state WHERE event_key=?", previous.keySet().stream().map(key -> new Object[]{key}).toList());
        jdbc.update("DELETE FROM radar_change_delta WHERE observed_at<?", expiry);
    }

    // 旧批次只有前二十名的精确计数。只比较相邻已发布批次均出现的事件，离榜重返不算升温。
    private static final String CHANGES = """
        WITH frames AS (
            SELECT id,published_at,lag(id) OVER (ORDER BY id) prev,lag(published_at) OVER (ORDER BY id) previous_at
            FROM radar_replay_frame
            WHERE published_at BETWEEN :since AND :until
              AND id < COALESCE((SELECT first_frame FROM radar_change_epoch WHERE id=1),9223372036854775807)
        ), deltas AS (
            SELECT event_key,observed_at,old_sources,sources,old_reports,reports FROM radar_change_delta
            WHERE observed_at BETWEEN :since AND :until AND previous_at >= :since
            UNION ALL
            SELECT v.event_key,f.published_at,p.sources,r.sources,p.reports,r.reports
            FROM frames f JOIN radar_replay_rank r ON r.frame_id=f.id JOIN radar_replay_version v ON v.hash=r.version_hash
            JOIN radar_replay_version u ON u.event_key=v.event_key JOIN radar_replay_rank p ON p.frame_id=f.prev AND p.version_hash=u.hash
            WHERE p.sources<>r.sources OR p.reports<>r.reports
        ), continuity AS (
            SELECT *,CASE WHEN lag(sources) OVER w<>old_sources OR lag(reports) OVER w<>old_reports THEN 1 ELSE 0 END changed_base
            FROM deltas WINDOW w AS (PARTITION BY event_key ORDER BY observed_at)
        ), segments AS (
            SELECT *,sum(changed_base) OVER (PARTITION BY event_key ORDER BY observed_at) segment FROM continuity
        ), counts AS (
            SELECT *,first_value(old_sources) OVER w sources_before,first_value(old_reports) OVER w reports_before,
                   row_number() OVER (PARTITION BY event_key ORDER BY observed_at DESC) last_row
            FROM segments WINDOW w AS (PARTITION BY event_key,segment ORDER BY observed_at)
        ), progress AS (
            SELECT a.event_key,c.updated_at at,n.title report_title,json_extract(b.payload,'$.newFacts[0]') fact,
                   row_number() OVER (PARTITION BY a.event_key ORDER BY (length(trim(json_extract(b.payload,'$.newFacts[0]')))>0) DESC,c.updated_at DESC,c.cache_key,a.report_id) latest
            FROM news_ai_content c JOIN news_ai_binding b ON b.cache_key=c.cache_key
            JOIN news_ai_report a ON a.event_key=json_extract(b.payload,'$.eventKey') AND a.content_key=c.content_hash
            JOIN news_report n ON n.id=a.report_id AND n.content_version=a.content_version
            JOIN news_report_version rv ON rv.report_id=n.id AND rv.version=n.content_version
            WHERE c.state='READY' AND c.updated_at BETWEEN :aiSince AND :aiUntil
              AND a.relation='PROGRESS' AND json_extract(b.payload,'$.relation')='PROGRESS'
              AND rv.detected_at BETWEEN :aiSince AND :aiUntil
        ), candidates AS (
            SELECT event_key,observed_at at,'NEW' kind,NULL fact,NULL report_title,2 priority FROM radar_replay_seen WHERE observed_at BETWEEN :since AND :until
            UNION ALL
            SELECT event_key,observed_at,CASE WHEN sources>sources_before THEN 'SPREAD' ELSE 'COVERAGE' END,NULL,NULL,
                   CASE WHEN sources>sources_before THEN 1 ELSE 3 END
            FROM counts WHERE last_row=1 AND reports>reports_before
            UNION ALL
            SELECT event_key,at,CASE WHEN length(trim(fact))>0 THEN 'PROGRESS' ELSE 'COVERAGE' END,fact,report_title,
                   CASE WHEN length(trim(fact))>0 THEN 0 ELSE 3 END FROM progress WHERE latest=1
        ), chosen AS (
            SELECT *,row_number() OVER (PARTITION BY event_key ORDER BY priority,at DESC) winner FROM candidates
        )
        , visible AS MATERIALIZED (
            SELECT k.*,v.event_id,v.title,v.summary,s.observed_at FROM chosen k
            JOIN radar_replay_seen s USING(event_key) JOIN radar_replay_version v ON v.hash=s.version_hash
            WHERE k.winner=1 AND s.observed_at<=:until
        ), aliases AS (
            SELECT k.event_key,min(a.event_key) target FROM visible k
            JOIN news_report n ON n.title=k.title AND n.content=k.summary
            JOIN news_ai_report a ON a.report_id=n.id AND a.content_version=n.content_version
            JOIN news_report_version rv ON rv.report_id=n.id AND rv.version=n.content_version
            JOIN news_ai_content job ON job.content_hash=a.content_key AND job.state='READY' AND job.updated_at<=:aiUntil
            JOIN news_ai_binding b ON b.cache_key=job.cache_key AND json_extract(b.payload,'$.eventKey')=a.event_key
            WHERE k.event_key NOT LIKE 'news-ai:%' AND length(trim(k.summary))>0
              AND rv.detected_at<=:aiUntil AND substr(rv.detected_at,1,10)=substr(k.observed_at,1,10)
            GROUP BY k.event_key HAVING count(DISTINCT a.event_key)=1 AND min(a.event_key)<>k.event_key
        )
        , resolved AS (
            SELECT k.*,COALESCE(canonical.event_key,k.event_key) final_key,
                   COALESCE(v.event_id,k.event_id) final_id,COALESCE(v.title,k.title) final_title,
                   COALESCE(v.summary,k.summary) final_summary,min(k.observed_at,COALESCE(canonical.observed_at,k.observed_at)) first_at
            FROM visible k LEFT JOIN aliases a ON a.event_key=k.event_key
            LEFT JOIN radar_replay_seen canonical ON canonical.event_key=a.target AND canonical.observed_at<=:until
            LEFT JOIN radar_replay_version v ON v.hash=canonical.version_hash
        ), identified AS (
            SELECT *,min(first_at) OVER (PARTITION BY final_key) first_observation FROM resolved
        ), merged AS (
            SELECT *,row_number() OVER (PARTITION BY final_key ORDER BY priority,at DESC,event_key) row_num
            FROM identified WHERE kind<>'NEW' OR first_observation>=:since
        )
        SELECT k.final_id event_id,k.final_title title,k.final_summary summary,k.first_observation observed_at,
               CASE WHEN k.kind='NEW' THEN k.first_observation ELSE k.at END at,k.kind,k.fact,k.report_title,c.sources_before,c.sources,
               max(0,c.reports-c.reports_before) added_reports
        FROM merged k LEFT JOIN counts c ON c.event_key=k.event_key AND c.last_row=1
        WHERE k.row_num=1 AND (:kind='ALL' OR k.kind=:kind)
        ORDER BY k.priority,at DESC,k.final_key LIMIT :limit OFFSET :offset
        """;

    public List<RadarRecentChange> recent(String since, String until, String aiSince, String aiUntil, String kind, int limit, int offset) {
        var named = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
        return named.query(CHANGES, Map.of("since", since, "until", until, "aiSince", aiSince, "aiUntil", aiUntil, "kind", kind, "limit", limit, "offset", offset), (rs, index) -> {
            var change = new RadarRecentChange();
            change.setEventId(rs.getString("event_id"));
            change.setTitle(rs.getString("title"));
            change.setSummary(rs.getString("summary"));
            change.setFirstObservedAt(rs.getString("observed_at"));
            change.setChangedAt(rs.getString("at"));
            change.setKind(RadarChangeKind.valueOf(rs.getString("kind")));
            change.setReportTitle(rs.getString("report_title"));
            change.setNewFact(rs.getString("fact"));
            change.setSourcesBefore((Integer) rs.getObject("sources_before"));
            change.setSourcesAfter((Integer) rs.getObject("sources"));
            change.setAddedReports((Integer) rs.getObject("added_reports"));
            return change;
        });
    }
}
