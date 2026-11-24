package com.finscope.dao.news;

import com.finscope.domain.news.NewsContentImportance;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.List;

/** 维护全站事件评分投影。只归并已绑定到当前正文版本的报道，筛选条件不参与计分。 */
final class NewsEventRanking {
    private NewsEventRanking() { }

    private static String aggregate(String scope) {
        return """
                WITH members AS (
                  SELECT a.event_key,n.source_name,n.content_importance,n.arrival_sequence,a.relation,
                    COALESCE(n.published_at,n.first_seen_at) at,
                    max(COALESCE(n.published_at,n.first_seen_at),n.first_seen_at) visible_at,
                    first_value(n.source_name) OVER (PARTITION BY a.event_key ORDER BY
                      COALESCE(n.published_at,n.first_seen_at),n.arrival_sequence) origin_source,
                    row_number() OVER (PARTITION BY a.event_key,a.content_key ORDER BY
                      COALESCE(n.published_at,n.first_seen_at),n.arrival_sequence) occurrence
                  FROM news_ai_report a JOIN news_report n ON n.id=a.report_id AND n.content_version=a.content_version
                  WHERE %s
                ), outlets AS (
                  SELECT event_key,source_name,min(at) first_at,
                    min(min(at)) OVER (PARTITION BY event_key) started,
                    max(CASE WHEN relation<>'DUPLICATE' AND occurrence=1 THEN content_importance END) importance,
                    min(content_importance) fallback_importance,
                    max(CASE WHEN relation='PROGRESS' AND occurrence=1 THEN at END) progress_at,
                    max(CASE WHEN relation='PROGRESS' AND occurrence=1 AND source_name<>origin_source THEN 1 ELSE 0 END) new_evidence,
                    max(arrival_sequence) last_seq,max(visible_at) last_visible
                  FROM members GROUP BY event_key,source_name
                ), ordered AS (
                  SELECT outlets.*,row_number() OVER (PARTITION BY event_key ORDER BY first_at,source_name) outlet_number
                  FROM outlets
                ), totals AS (
                  SELECT event_key,count(*) sources,coalesce(max(importance),min(fallback_importance)) base,
                    min(CASE WHEN outlet_number=2 THEN round((julianday(first_at)-julianday(started))*86400) END) second_delay_seconds,
                    max(new_evidence) evidence,coalesce(max(progress_at),min(first_at)) meaningful_at,
                    max(last_seq) last_seq,max(last_visible) last_visible
                  FROM ordered GROUP BY event_key
                )
                SELECT event_key,sources,base,second_delay_seconds,evidence,meaningful_at,last_seq,last_visible FROM totals
                """.formatted(scope);
    }

    static void initialize(JdbcTemplate jdbc) {
        if (jdbc.queryForList("PRAGMA table_info(news_report)").stream()
                .noneMatch(column -> "content_importance".equals(column.get("name")))) {
            jdbc.execute("ALTER TABLE news_report ADD COLUMN content_importance INTEGER NOT NULL DEFAULT 35");
            backfillImportance(jdbc);
        }
        if (jdbc.queryForObject("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='news_event_rank'", Integer.class) != 0) {
            return;
        }
        jdbc.execute("""
                CREATE TABLE news_event_rank(event_key TEXT PRIMARY KEY,sources INTEGER NOT NULL,
                  base INTEGER NOT NULL,second_delay_seconds INTEGER,evidence INTEGER NOT NULL,meaningful_at TEXT NOT NULL,
                  last_seq INTEGER NOT NULL,last_visible TEXT NOT NULL)
                """);
        jdbc.execute("INSERT INTO news_event_rank " + aggregate("1=1"));
        String oldKey = "OLD.event_key";
        String newKey = "NEW.event_key";
        trigger(jdbc, "link", "INSERT ON news_ai_report", refresh(newKey));
        trigger(jdbc, "unlink", "DELETE ON news_ai_report", refresh(oldKey));
        trigger(jdbc, "relink", "UPDATE ON news_ai_report", refresh(oldKey) + refresh("CASE WHEN NEW.event_key<>OLD.event_key THEN NEW.event_key END"));
        String linkedKey = "(SELECT event_key FROM news_ai_report WHERE report_id=OLD.id)";
        trigger(jdbc, "revision", "UPDATE OF content_version,published_at ON news_report "
                + "WHEN OLD.content_version<>NEW.content_version OR OLD.published_at IS NOT NEW.published_at", refresh(linkedKey));
        trigger(jdbc, "prune", "DELETE ON news_report", refresh(linkedKey));
    }

    private static String refresh(String key) {
        return "DELETE FROM news_event_rank WHERE event_key=" + key + "; INSERT INTO news_event_rank "
                + aggregate("a.event_key=" + key) + "; ";
    }

    private static void trigger(JdbcTemplate jdbc, String name, String change, String body) {
        jdbc.execute("CREATE TRIGGER news_rank_" + name + " AFTER " + change + " BEGIN " + body + " END");
    }

    private static void backfillImportance(JdbcTemplate jdbc) {
        long cursor = 0;
        while (true) {
            var rows = jdbc.queryForList("SELECT arrival_sequence,title FROM news_report WHERE arrival_sequence>? ORDER BY arrival_sequence LIMIT 500", cursor);
            if (rows.isEmpty()) {
                return;
            }
            List<Object[]> updates = new ArrayList<>();
            for (var row : rows) {
                cursor = ((Number) row.get("arrival_sequence")).longValue();
                updates.add(new Object[]{NewsContentImportance.score((String) row.get("title")), cursor});
            }
            jdbc.batchUpdate("UPDATE news_report SET content_importance=? WHERE arrival_sequence=?", updates);
        }
    }

    /** 对越过翻页水位的事件按可见成员重算；普通读取直接用持久投影。 */
    static String page(String candidates, boolean priorityOrder) {
        return "WITH candidates AS (" + candidates + "), cutoff(seq,at) AS (VALUES(?,?)), "
                + """
                  clipped AS (SELECT DISTINCT e.event_key FROM candidates c JOIN news_event_rank e ON e.event_key=c.event_key
                    CROSS JOIN cutoff WHERE e.last_seq>cutoff.seq OR e.last_visible>cutoff.at),
                  historical AS (
                  """ + aggregate("a.event_key IN (SELECT event_key FROM clipped) AND n.arrival_sequence<=(SELECT seq FROM cutoff) "
                        + "AND max(n.first_seen_at,COALESCE(n.published_at,n.first_seen_at))<=(SELECT at FROM cutoff)") + """
                  ), metrics AS (
                    SELECT c.*,coalesce(h.sources,e.sources,1) coverage_sources,
                      coalesce(h.base,e.base,c.content_importance) base_score,
                      coalesce(h.second_delay_seconds,e.second_delay_seconds) second_delay_seconds,
                      coalesce(h.evidence,e.evidence,0) evidence,
                      coalesce(h.meaningful_at,e.meaningful_at,c.published_at,c.first_seen_at) meaningful_at
                    FROM candidates c CROSS JOIN cutoff
                    LEFT JOIN news_event_rank e ON e.event_key=c.event_key AND e.last_seq<=cutoff.seq AND e.last_visible<=cutoff.at
                    LEFT JOIN historical h ON h.event_key=c.event_key
                  ), scored AS (
                    SELECT metrics.*,min(CASE WHEN base_score<=10 THEN base_score/2 ELSE base_score END,
                      (coverage_sources>=2)*? + (coverage_sources>=3)*? + (coverage_sources>=4)*?
                      + coalesce(second_delay_seconds<=60*?,0)*? + evidence*?) coverage_bonus
                    FROM metrics
                  )
                  SELECT scored.*,max(0,min(100,base_score+coverage_bonus)
                    - min(60,cast(max(0,julianday(cutoff.at)-julianday(meaningful_at))*12 AS integer))) event_priority
                  FROM scored CROSS JOIN cutoff ORDER BY
                  """ + (priorityOrder ? "event_priority DESC,meaningful_at DESC," : "")
                + "COALESCE(published_at,first_seen_at) DESC,arrival_sequence DESC LIMIT ? OFFSET ?";
    }
}
