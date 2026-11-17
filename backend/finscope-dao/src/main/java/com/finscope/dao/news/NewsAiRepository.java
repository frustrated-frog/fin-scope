package com.finscope.dao.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.news.NewsAiJobState;
import com.finscope.domain.news.ai.NewsAiAnalysis;
import com.finscope.domain.news.ai.NewsAiBinding;
import com.finscope.domain.news.ai.NewsAiEvent;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 持久化幂等、候选版本与费用预留；网络调用不在事务内。金额单位为百万分之一美元。 */
@Repository
@DependsOn("newsWindowSchemaMigrator")
public class NewsAiRepository implements InitializingBean {
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private ObjectMapper mapper;

    @Override
    public void afterPropertiesSet() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_content (cache_key TEXT PRIMARY KEY,content_hash TEXT NOT NULL,state TEXT NOT NULL,owner TEXT,lease_until TEXT,call_id TEXT,attempt INTEGER NOT NULL DEFAULT 1,retry_after TEXT,analysis_json TEXT,updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_ai_hash ON news_ai_content(content_hash,updated_at)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_binding (cache_key TEXT PRIMARY KEY,payload TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_event (event_key TEXT PRIMARY KEY,revision INTEGER NOT NULL,payload TEXT NOT NULL,updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_ai_event_time ON news_ai_event(updated_at)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_call (id TEXT PRIMARY KEY,created_at TEXT NOT NULL,model TEXT NOT NULL,state TEXT NOT NULL,reserved_micros INTEGER NOT NULL,actual_micros INTEGER,input_tokens INTEGER,output_tokens INTEGER,error_type TEXT)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_ai_call_time ON news_ai_call(created_at)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_control (id INTEGER PRIMARY KEY CHECK(id=1),enabled INTEGER NOT NULL,started_at TEXT NOT NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_version_detected ON news_report_version(detected_at,report_id)");
    }

    @Transactional(rollbackFor = Exception.class)
    public LocalDateTime observeMode(boolean enabled, LocalDateTime now) {
        jdbc.update("INSERT OR IGNORE INTO news_ai_control VALUES(1,?,?)", enabled ? 1 : 0, now.toString());
        if (enabled) {
            jdbc.update("UPDATE news_ai_control SET enabled=1,started_at=? WHERE id=1 AND enabled=0", now.toString());
        } else {
            jdbc.update("UPDATE news_ai_control SET enabled=0 WHERE id=1");
        }
        return LocalDateTime.parse(jdbc.queryForObject("SELECT started_at FROM news_ai_control WHERE id=1", String.class));
    }

    public java.util.Set<String> changedReportsSince(LocalDateTime since) {
        return new java.util.HashSet<>(jdbc.queryForList("SELECT report_id FROM news_report_version WHERE detected_at>=? GROUP BY report_id ORDER BY MAX(detected_at) DESC LIMIT 20000", String.class, since.toString()));
    }

    public void defer(String key, String owner, LocalDateTime retryAfter) {
        jdbc.update("UPDATE news_ai_content SET state=?,owner=NULL,attempt=MAX(0,attempt-1),retry_after=? WHERE cache_key=? AND owner=? AND call_id IS NULL",
                NewsAiJobState.RETRYABLE.name(), retryAfter.toString(), key, owner);
    }


    /** 多进程也只有一个 owner 可领取；已发出而租约过期的请求保留 UNKNOWN，不自动重发。 */
    public boolean claim(String key, String hash, String owner, LocalDateTime now, int maxAttempts) {
        jdbc.update("UPDATE news_ai_content SET state=?,owner=NULL WHERE cache_key=? AND state=? AND lease_until<? AND call_id IS NOT NULL",
                NewsAiJobState.UNKNOWN.name(), key, NewsAiJobState.CLAIMED.name(), now.toString());
        return jdbc.update("INSERT INTO news_ai_content(cache_key,content_hash,state,owner,lease_until,updated_at) VALUES(?,?,?,?,?,?) "
                        + "ON CONFLICT(cache_key) DO UPDATE SET state=excluded.state,owner=excluded.owner,lease_until=excluded.lease_until,call_id=NULL,attempt=news_ai_content.attempt+1,updated_at=excluded.updated_at "
                        + "WHERE news_ai_content.attempt<? AND ((news_ai_content.state=? AND news_ai_content.retry_after<=?) OR "
                        + "(news_ai_content.state=? AND news_ai_content.lease_until<? AND news_ai_content.call_id IS NULL) OR "
                        + "(news_ai_content.state=? AND NOT EXISTS(SELECT 1 FROM news_ai_binding b WHERE b.cache_key=excluded.cache_key)))",
                key, hash, NewsAiJobState.CLAIMED.name(), owner, now.plusMinutes(5).toString(), now.toString(), maxAttempts,
                NewsAiJobState.RETRYABLE.name(), now.toString(), NewsAiJobState.CLAIMED.name(), now.toString(), NewsAiJobState.READY.name()) == 1;
    }

    public boolean reserve(String callId, String model, long upperMicros, long dailyMicros, int dailyCalls, LocalDateTime now) {
        if (upperMicros <= 0 || dailyMicros <= 0 || dailyCalls <= 0) {
            return false;
        }
        return jdbc.update("INSERT INTO news_ai_call(id,created_at,model,state,reserved_micros) SELECT ?,?,?,'RESERVED',? "
                        + "WHERE (SELECT COALESCE(SUM(COALESCE(actual_micros,reserved_micros)),0) FROM news_ai_call WHERE created_at>=? AND created_at<?)+?<=? "
                        + "AND (SELECT COUNT(*) FROM news_ai_call WHERE created_at>=? AND created_at<?)<?",
                callId, now.toString(), model, upperMicros, now.toLocalDate().atStartOfDay().toString(), now.toLocalDate().plusDays(1).atStartOfDay().toString(),
                upperMicros, dailyMicros, now.toLocalDate().atStartOfDay().toString(), now.toLocalDate().plusDays(1).atStartOfDay().toString(), dailyCalls) == 1;
    }

    public boolean attachCall(String key, String owner, String callId) {
        return jdbc.update("UPDATE news_ai_content SET call_id=? WHERE cache_key=? AND owner=? AND state=?", callId, key, owner, NewsAiJobState.CLAIMED.name()) == 1;
    }

    public void settle(String callId, Long input, Long output, Long actualMicros, String errorType) {
        if (jdbc.update("UPDATE news_ai_call SET state=?,input_tokens=?,output_tokens=?,actual_micros=?,error_type=? WHERE id=? AND state='RESERVED'",
                actualMicros == null ? "UNKNOWN" : "SETTLED", input, output, actualMicros, errorType, callId) != 1) {
            throw new IllegalStateException("新闻 AI 调用账本结算状态冲突");
        }
    }

    public void fail(String key, String owner, NewsAiJobState state, LocalDateTime retryAfter) {
        jdbc.update("UPDATE news_ai_content SET state=?,owner=NULL,retry_after=?,updated_at=? WHERE cache_key=? AND owner=?",
                state.name(), retryAfter.toString(), retryAfter.toString(), key, owner);
    }

    public boolean saveAnalysis(String key, String owner, NewsAiAnalysis analysis, LocalDateTime now) {
        return jdbc.update("UPDATE news_ai_content SET analysis_json=?,updated_at=? WHERE cache_key=? AND owner=? AND state=?",
                json(analysis), now.toString(), key, owner, NewsAiJobState.CLAIMED.name()) == 1;
    }

    public NewsAiAnalysis analysis(String key) {
        return jdbc.query("SELECT analysis_json FROM news_ai_content WHERE cache_key=? AND analysis_json IS NOT NULL",
                (rs, i) -> read(rs.getString(1), NewsAiAnalysis.class), key).stream().findFirst().orElse(null);
    }

    public NewsAiAnalysis reusableAnalysis(String occurrenceKey) {
        String hash = occurrenceKey.split("@", 2)[0];
        return jdbc.query("SELECT analysis_json FROM news_ai_content WHERE content_hash>=? AND content_hash<? AND analysis_json IS NOT NULL ORDER BY updated_at DESC LIMIT 1",
                (rs, i) -> read(rs.getString(1), NewsAiAnalysis.class), hash + "@", hash + "A").stream().findFirst().orElse(null);
    }

    public Map<String, NewsAiBinding> bindings(Collection<String> keys) {
        Map<String, NewsAiBinding> result = new HashMap<>();
        List<String> values = List.copyOf(keys);
        for (int start = 0; start < values.size(); start += 300) {
            List<String> batch = values.subList(start, Math.min(values.size(), start + 300));
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            jdbc.query("SELECT cache_key,payload FROM news_ai_binding WHERE cache_key IN (" + placeholders + ")", rs -> {
                result.put(rs.getString(1), read(rs.getString(2), NewsAiBinding.class));
            }, batch.toArray());
        }
        return result;
    }

    public Map<String, NewsAiBinding> bindingsByHashes(Collection<String> hashes) {
        Map<String, NewsAiBinding> result = new HashMap<>();
        List<String> values = List.copyOf(hashes);
        for (int start = 0; start < values.size(); start += 300) {
            List<String> batch = values.subList(start, Math.min(values.size(), start + 300));
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            jdbc.query("SELECT c.content_hash,b.payload FROM news_ai_content c JOIN news_ai_binding b ON b.cache_key=c.cache_key WHERE c.content_hash IN (" + placeholders + ") ORDER BY c.updated_at DESC", rs -> {
                result.putIfAbsent(rs.getString(1), read(rs.getString(2), NewsAiBinding.class));
            }, batch.toArray());
        }
        return result;
    }

    public List<NewsAiEvent> events(LocalDateTime since, int limit) {
        return jdbc.query("SELECT payload FROM news_ai_event WHERE updated_at>=? ORDER BY updated_at DESC,event_key LIMIT ?",
                (rs, i) -> read(rs.getString(1), NewsAiEvent.class), since.toString(), Math.max(1, Math.min(limit, 10000)));
    }

    public NewsAiEvent event(String key) {
        return jdbc.query("SELECT payload FROM news_ai_event WHERE event_key=?", (rs, i) -> read(rs.getString(1), NewsAiEvent.class), key).stream().findFirst().orElse(null);
    }

    /** 事件按候选版本 CAS；内容理解已独立保存，冲突时只重做归属判断。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean bind(String key, String owner, NewsAiBinding binding, NewsAiEvent event, int expectedRevision, LocalDateTime now) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM news_ai_content WHERE cache_key=? AND owner=? AND state=?", Integer.class,
                key, owner, NewsAiJobState.CLAIMED.name()) != 1) {
            return false;
        }
        int changed;
        if (expectedRevision == 0) {
            changed = jdbc.update("INSERT OR IGNORE INTO news_ai_event VALUES(?,?,?,?)", event.getEventKey(), event.getRevision(), json(event), now.toString());
        } else {
            changed = jdbc.update("UPDATE news_ai_event SET revision=?,payload=?,updated_at=? WHERE event_key=? AND revision=?",
                    event.getRevision(), json(event), now.toString(), event.getEventKey(), expectedRevision);
        }
        if (changed != 1) {
            return false;
        }
        if (jdbc.update("INSERT INTO news_ai_binding VALUES(?,?) ON CONFLICT(cache_key) DO UPDATE SET payload=excluded.payload", key, json(binding)) != 1
                || jdbc.update("UPDATE news_ai_content SET state=?,owner=NULL,updated_at=? WHERE cache_key=? AND owner=?",
                NewsAiJobState.READY.name(), now.toString(), key, owner) != 1) {
            throw new IllegalStateException("新闻 AI 归属保存状态冲突");
        }
        return true;
    }

    public Map<String, Object> dailyUsage(LocalDateTime now) {
        return jdbc.queryForMap("SELECT COUNT(*) calls,COALESCE(SUM(input_tokens),0) input_tokens,COALESCE(SUM(output_tokens),0) output_tokens,"
                        + "COALESCE(SUM(actual_micros),0) actual_micros,COALESCE(SUM(CASE WHEN actual_micros IS NULL THEN reserved_micros ELSE 0 END),0) unresolved_micros "
                        + "FROM news_ai_call WHERE created_at>=? AND created_at<?", now.toLocalDate().atStartOfDay().toString(), now.toLocalDate().plusDays(1).atStartOfDay().toString());
    }

    /** 按实际读取的原文版本关联；正文变化后旧关系不会参与查询。 */
    public boolean linkReport(String id, String title, String content, NewsAiBinding binding) {
        return linkReport(id, title, content, binding, true);
    }

    public boolean linkOriginalReport(String id, String title, String content, NewsAiBinding binding) {
        return linkReport(id, title, content, binding, false);
    }

    private boolean linkReport(String id, String title, String content, NewsAiBinding binding, boolean replace) {
        String condition = replace ? "news_ai_report.content_key<>excluded.content_key OR news_ai_report.event_key<>excluded.event_key "
                + "OR news_ai_report.relation<>excluded.relation OR news_ai_report.content_version<>excluded.content_version"
                : "news_ai_report.content_version<>excluded.content_version";
        return jdbc.update("INSERT INTO news_ai_report(report_id,content_key,event_key,relation,content_version) "
                        + "SELECT id,?,?,?,content_version FROM news_report WHERE id=? AND title=? AND content=? "
                        + "ON CONFLICT(report_id) DO UPDATE SET content_key=excluded.content_key,event_key=excluded.event_key,"
                        + "relation=excluded.relation,content_version=excluded.content_version "
                        + "WHERE " + condition,
                binding.getContentKey(), binding.getEventKey(), binding.getRelation().name(), id, title, content) == 1;
    }

    public Map<String, String> linkedReportKeys(Collection<String> ids) {
        Map<String, String> result = new HashMap<>();
        List<String> values = List.copyOf(ids);
        for (int start = 0; start < values.size(); start += 300) {
            List<String> batch = values.subList(start, Math.min(values.size(), start + 300));
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            jdbc.query("SELECT a.report_id,a.content_key FROM news_ai_report a JOIN news_report n "
                    + "ON n.id=a.report_id AND n.content_version=a.content_version WHERE a.report_id IN (" + placeholders + ")", rs -> {
                result.put(rs.getString(1), rs.getString(2));
            }, batch.toArray());
        }
        return result;
    }

    /** 理解缓存保留30天、调用明细保留90天；未决费用与仍被新闻引用的事件不清理。 */
    @Transactional(rollbackFor = Exception.class)
    public void prune(LocalDateTime now) {
        jdbc.update("DELETE FROM news_ai_report WHERE NOT EXISTS(SELECT 1 FROM news_report n WHERE n.id=report_id AND n.content_version=news_ai_report.content_version)");
        jdbc.update("DELETE FROM news_ai_content WHERE updated_at<? AND state<>? AND NOT EXISTS(SELECT 1 FROM news_ai_report r WHERE r.content_key=news_ai_content.content_hash)",
                now.minusDays(30).toString(), NewsAiJobState.CLAIMED.name());
        jdbc.update("DELETE FROM news_ai_binding WHERE NOT EXISTS(SELECT 1 FROM news_ai_content c WHERE c.cache_key=news_ai_binding.cache_key)");
        jdbc.update("DELETE FROM news_ai_event WHERE updated_at<? AND NOT EXISTS(SELECT 1 FROM news_ai_report r WHERE r.event_key=news_ai_event.event_key)", now.minusDays(30).toString());
        jdbc.update("DELETE FROM news_ai_call WHERE created_at<? AND actual_micros IS NOT NULL", now.minusDays(90).toString());
    }

    public Map<String, Long> jobCounts() {
        Map<String, Long> result = new HashMap<>();
        jdbc.query("SELECT state,COUNT(*) total FROM news_ai_content GROUP BY state", rs -> {
            result.put(rs.getString("state"), rs.getLong("total"));
        });
        return result;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("新闻 AI 状态序列化失败", error);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception error) {
            throw new IllegalStateException("新闻 AI 状态读取失败", error);
        }
    }
}
