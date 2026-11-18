package com.finscope.dao.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.news.NewsAiJobState;
import com.finscope.domain.news.ai.NewsAiAnalysis;
import com.finscope.domain.news.ai.NewsAiBinding;
import com.finscope.domain.news.ai.NewsAiEvent;
import com.finscope.domain.news.ai.NewsAiDispatchItem;
import com.finscope.domain.news.ai.NewsAiEfficiency;
import com.finscope.domain.news.ai.NewsAiCallAudit;
import com.finscope.domain.news.ai.NewsAiCallItemAudit;
import com.finscope.common.enums.news.NewsAiCallState;
import com.finscope.domain.news.ai.NewsAiContentIdentity;
import com.finscope.common.enums.news.NewsAiInputMode;
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
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_ai_ready_time ON news_ai_content(state,updated_at)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_ai_hash ON news_ai_content(content_hash,updated_at)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_binding (cache_key TEXT PRIMARY KEY,payload TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_event (event_key TEXT PRIMARY KEY,revision INTEGER NOT NULL,payload TEXT NOT NULL,updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_ai_event_time ON news_ai_event(updated_at)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_call (id TEXT PRIMARY KEY,created_at TEXT NOT NULL,model TEXT NOT NULL,state TEXT NOT NULL,reserved_micros INTEGER NOT NULL,actual_micros INTEGER,input_tokens INTEGER,output_tokens INTEGER,error_type TEXT)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_ai_call_time ON news_ai_call(created_at)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_control (id INTEGER PRIMARY KEY CHECK(id=1),enabled INTEGER NOT NULL,started_at TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_occurrence_job(occurrence_key TEXT PRIMARY KEY,cache_key TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_understanding(body_hash TEXT PRIMARY KEY,state TEXT NOT NULL,owner TEXT,lease_until TEXT,call_id TEXT,attempt INTEGER NOT NULL DEFAULT 0,retry_after TEXT,analysis_json TEXT,updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_ai_understanding_time ON news_ai_understanding(updated_at)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_dispatch(call_id TEXT PRIMARY KEY,protocol TEXT NOT NULL,request_bytes INTEGER NOT NULL,candidate_refs INTEGER NOT NULL,unique_candidates INTEGER NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_dispatch_item(call_id TEXT NOT NULL,job_key TEXT NOT NULL,occurrence_key TEXT NOT NULL,mode TEXT NOT NULL,attempt INTEGER NOT NULL,source_chars INTEGER NOT NULL,sent_chars INTEGER NOT NULL,PRIMARY KEY(call_id,job_key))");
        if (jdbc.queryForList("PRAGMA table_info(news_ai_dispatch_item)").stream().noneMatch(column -> "report_id".equals(column.get("name")))) {
            jdbc.execute("ALTER TABLE news_ai_dispatch_item ADD COLUMN report_id TEXT NOT NULL DEFAULT ''");
        }
        jdbc.execute("CREATE TABLE IF NOT EXISTS news_ai_efficiency_run(id TEXT PRIMARY KEY,created_at TEXT NOT NULL,eligible_checks INTEGER NOT NULL,binding_reuses INTEGER NOT NULL,guarded_checks INTEGER NOT NULL,processing_ms INTEGER NOT NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_ai_efficiency_time ON news_ai_efficiency_run(created_at)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_news_version_detected ON news_report_version(detected_at,report_id)");
        restoreLegacyProgressFacts();
    }

    /** 恢复旧版在同一事务写入的最后一条明确进展；同一批有多个可能归属时不推测。 */
    private void restoreLegacyProgressFacts() {
        jdbc.update("""
                WITH candidates AS MATERIALIZED (
                    SELECT b.cache_key,e.event_key,e.updated_at,
                           json_extract(e.payload,'$.analysis.summary') summary,e.payload event_payload,
                           json_extract(b.payload,'$.analysis.summary') report_summary,
                           json_extract(b.payload,'$.relation') relation,
                           count(*) OVER (PARTITION BY e.event_key,e.updated_at) matches
                    FROM news_ai_event e JOIN news_ai_content c ON c.state='READY' AND c.updated_at=e.updated_at
                    JOIN news_ai_binding b ON b.cache_key=c.cache_key AND json_extract(b.payload,'$.eventKey')=e.event_key
                    WHERE e.updated_at>=?
                ), facts AS (
                    SELECT k.cache_key,f.value fact, char(10)||'最新进展：'||f.value marker,k.summary
                    FROM candidates k,json_each(k.event_payload,'$.analysis.facts') f
                    WHERE k.matches=1 AND k.relation='PROGRESS' AND k.summary<>k.report_summary
                      AND f.type='text' AND length(trim(f.value)) BETWEEN 1 AND 240
                ), supported AS (
                    SELECT cache_key,min(fact) fact FROM facts
                    WHERE substr(summary,-length(marker))=marker OR
                          (instr(summary,marker||'；')>0 AND instr(substr(summary,instr(summary,marker||'；')+length(marker)),char(10))=0)
                    GROUP BY cache_key HAVING count(*)=1
                )
                UPDATE news_ai_binding AS b SET payload=json_set(b.payload,'$.newFacts',json_array(s.fact))
                FROM supported s WHERE b.cache_key=s.cache_key AND json_type(b.payload,'$.newFacts') IS NULL
                """, LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(7).toString());
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

    public boolean reserve(String callId, String model, long upperMicros, Long dailyMicros, Integer dailyCalls, LocalDateTime now) {
        if (upperMicros <= 0 || (dailyMicros != null && dailyMicros <= 0) || (dailyCalls != null && dailyCalls <= 0)) {
            return false;
        }
        return jdbc.update("INSERT INTO news_ai_call(id,created_at,model,state,reserved_micros) SELECT ?,?,?,'RESERVED',? "
                        + "WHERE (? IS NULL OR (SELECT COALESCE(SUM(COALESCE(actual_micros,reserved_micros)),0) FROM news_ai_call WHERE created_at>=? AND created_at<?)+?<=?) "
                        + "AND (? IS NULL OR (SELECT COUNT(*) FROM news_ai_call WHERE created_at>=? AND created_at<? AND COALESCE(error_type,'')<>'CANCELLED_BEFORE_SEND')<?)",
                callId, now.toString(), model, upperMicros, dailyMicros,
                now.toLocalDate().atStartOfDay().toString(), now.toLocalDate().plusDays(1).atStartOfDay().toString(), upperMicros, dailyMicros,
                dailyCalls, now.toLocalDate().atStartOfDay().toString(), now.toLocalDate().plusDays(1).atStartOfDay().toString(), dailyCalls) == 1;
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

    @Transactional(rollbackFor = Exception.class)
    public boolean saveAnalysis(String key, String owner, NewsAiAnalysis analysis, LocalDateTime now) {
        int changed = jdbc.update("UPDATE news_ai_content SET analysis_json=?,updated_at=? WHERE cache_key=? AND owner=? AND state=?",
                json(analysis), now.toString(), key, owner, NewsAiJobState.CLAIMED.name());
        if (changed == 1) {
            String occurrence = jdbc.queryForObject("SELECT content_hash FROM news_ai_content WHERE cache_key=?", String.class, key);
            jdbc.update("INSERT INTO news_ai_understanding(body_hash,state,analysis_json,updated_at) VALUES(?,?,?,?) "
                            + "ON CONFLICT(body_hash) DO UPDATE SET state=excluded.state,analysis_json=excluded.analysis_json,owner=NULL,call_id=NULL,updated_at=excluded.updated_at "
                            + "WHERE news_ai_understanding.owner=? OR news_ai_understanding.analysis_json IS NOT NULL",
                    NewsAiContentIdentity.bodyHash(occurrence), NewsAiJobState.READY.name(), json(analysis), now.toString(), owner);
        }
        return changed == 1;
    }

    public NewsAiAnalysis analysis(String key) {
        return jdbc.query("SELECT analysis_json FROM news_ai_content WHERE cache_key=? AND analysis_json IS NOT NULL",
                (rs, i) -> read(rs.getString(1), NewsAiAnalysis.class), key).stream().findFirst().orElse(null);
    }

    public NewsAiAnalysis reusableAnalysis(String occurrenceKey) {
        String hash = NewsAiContentIdentity.bodyHash(occurrenceKey);
        var stored = jdbc.query("SELECT analysis_json FROM news_ai_understanding WHERE body_hash=? AND analysis_json IS NOT NULL",
                (rs, i) -> read(rs.getString(1), NewsAiAnalysis.class), hash);
        if (!stored.isEmpty()) {
            return stored.getFirst();
        }
        return jdbc.query("SELECT analysis_json FROM news_ai_content WHERE content_hash>=? AND content_hash<? AND analysis_json IS NOT NULL ORDER BY updated_at DESC LIMIT 1",
                (rs, i) -> read(rs.getString(1), NewsAiAnalysis.class), hash + "@", hash + "A").stream().findFirst().orElse(null);
    }

    /** 任务身份不跟随模型或提示词变化；升级时优先继承旧任务，UNKNOWN不能绕过。 */
    public String jobKey(String occurrence) {
        jdbc.update("INSERT OR IGNORE INTO news_ai_occurrence_job(occurrence_key,cache_key) VALUES(?,COALESCE("
                        + "(SELECT cache_key FROM news_ai_content WHERE content_hash=? ORDER BY CASE state WHEN 'UNKNOWN' THEN 0 WHEN 'CLAIMED' THEN 1 WHEN 'READY' THEN 2 ELSE 3 END,updated_at DESC,cache_key LIMIT 1),?))",
                occurrence, occurrence, occurrence + ":job");
        return jdbc.queryForObject("SELECT cache_key FROM news_ai_occurrence_job WHERE occurrence_key=?", String.class, occurrence);
    }

    public boolean claimUnderstanding(String occurrence, String owner, LocalDateTime now, int maxAttempts) {
        String hash = NewsAiContentIdentity.bodyHash(occurrence);
        jdbc.update("UPDATE news_ai_understanding SET state=?,owner=NULL WHERE body_hash=? AND state=? AND lease_until<? AND call_id IS NOT NULL AND analysis_json IS NULL",
                NewsAiJobState.UNKNOWN.name(), hash, NewsAiJobState.CLAIMED.name(), now.toString());
        // 旧版已发送且结果未知的全文同样不能因换日绕过。
        if (jdbc.queryForObject("SELECT COUNT(*) FROM news_ai_content WHERE content_hash>=? AND content_hash<? AND analysis_json IS NULL "
                + "AND (state=? OR (state=? AND call_id IS NOT NULL))", Integer.class, hash + "@", hash + "A",
                NewsAiJobState.UNKNOWN.name(), NewsAiJobState.CLAIMED.name()) > 0) {
            return false;
        }
        return jdbc.update("INSERT INTO news_ai_understanding(body_hash,state,owner,lease_until,updated_at) VALUES(?,?,?,?,?) "
                        + "ON CONFLICT(body_hash) DO UPDATE SET state=excluded.state,owner=excluded.owner,lease_until=excluded.lease_until,call_id=NULL,updated_at=excluded.updated_at "
                        + "WHERE analysis_json IS NULL AND attempt<? AND ((state=? AND retry_after<=?) OR (state=? AND lease_until<? AND call_id IS NULL))",
                hash, NewsAiJobState.CLAIMED.name(), owner, now.plusMinutes(5).toString(), now.toString(), maxAttempts,
                NewsAiJobState.RETRYABLE.name(), now.toString(), NewsAiJobState.CLAIMED.name(), now.toString()) == 1;
    }

    public void releaseUnderstanding(String occurrence, String owner, NewsAiJobState state, LocalDateTime retryAfter) {
        jdbc.update("UPDATE news_ai_understanding SET state=?,owner=NULL,retry_after=?,call_id=CASE WHEN ?='UNKNOWN' THEN call_id ELSE NULL END,updated_at=? "
                        + "WHERE body_hash=? AND owner=? AND analysis_json IS NULL",
                state.name(), retryAfter.toString(), state.name(), retryAfter.toString(), NewsAiContentIdentity.bodyHash(occurrence), owner);
    }

    /** 一次短事务原子绑定所有任务与全文租约；慢模型请求在返回后发出。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean attachDispatch(String callId, List<NewsAiDispatchItem> items, String protocol, long requestBytes,
                                  int candidateRefs, int uniqueCandidates, LocalDateTime now) {
        for (var item : items) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM news_ai_content WHERE cache_key=? AND owner=? AND state=? AND call_id IS NULL AND lease_until>=?",
                    Integer.class, item.getJobKey(), item.getOwner(), NewsAiJobState.CLAIMED.name(), now.toString()) != 1) {
                return false;
            }
            if (item.getMode() == NewsAiInputMode.UNDERSTAND && jdbc.queryForObject(
                    "SELECT COUNT(*) FROM news_ai_understanding WHERE body_hash=? AND owner=? AND state=? AND call_id IS NULL AND analysis_json IS NULL AND lease_until>=?",
                    Integer.class, NewsAiContentIdentity.bodyHash(item.getOccurrenceKey()), item.getOwner(), NewsAiJobState.CLAIMED.name(), now.toString()) != 1) {
                return false;
            }
        }
        for (var item : items) {
            if (!attachCall(item.getJobKey(), item.getOwner(), callId)) {
                throw new IllegalStateException("新闻任务发送所有权冲突");
            }
            if (item.getMode() == NewsAiInputMode.UNDERSTAND && jdbc.update("UPDATE news_ai_understanding SET call_id=?,attempt=attempt+1 WHERE body_hash=? AND owner=? AND state=? AND analysis_json IS NULL",
                    callId, NewsAiContentIdentity.bodyHash(item.getOccurrenceKey()), item.getOwner(), NewsAiJobState.CLAIMED.name()) != 1) {
                throw new IllegalStateException("新闻理解发送所有权冲突");
            }
            jdbc.update("INSERT INTO news_ai_dispatch_item(call_id,job_key,occurrence_key,mode,attempt,source_chars,sent_chars,report_id) SELECT ?,cache_key,content_hash,?,attempt,?,?,? FROM news_ai_content WHERE cache_key=?",
                    callId, item.getMode().name(), item.getSourceChars(), item.getSentChars(), java.util.Objects.toString(item.getReportId(), ""), item.getJobKey());
        }
        jdbc.update("INSERT INTO news_ai_dispatch VALUES(?,?,?,?,?)", callId, protocol, requestBytes, candidateRefs, uniqueCandidates);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelDispatch(String callId, List<NewsAiDispatchItem> items, LocalDateTime now) {
        for (var item : items) {
            jdbc.update("UPDATE news_ai_content SET state=?,owner=NULL,call_id=NULL,attempt=MAX(0,attempt-1),retry_after=? WHERE cache_key=? AND owner=? AND (call_id IS NULL OR call_id=?)",
                    NewsAiJobState.RETRYABLE.name(), now.plusMinutes(5).toString(), item.getJobKey(), item.getOwner(), callId);
            jdbc.update("UPDATE news_ai_understanding SET state=?,owner=NULL,attempt=MAX(0,attempt-CASE WHEN call_id=? THEN 1 ELSE 0 END),call_id=NULL,retry_after=? "
                            + "WHERE body_hash=? AND owner=? AND analysis_json IS NULL AND (call_id IS NULL OR call_id=?)",
                    NewsAiJobState.RETRYABLE.name(), callId, now.toString(), NewsAiContentIdentity.bodyHash(item.getOccurrenceKey()), item.getOwner(), callId);
        }
        settle(callId, 0L, 0L, 0L, "CANCELLED_BEFORE_SEND");
    }

    public void recordEfficiency(LocalDateTime now, NewsAiEfficiency value) {
        jdbc.update("INSERT INTO news_ai_efficiency_run VALUES(?,?,?,?,?,?)", java.util.UUID.randomUUID().toString(), now.toString(),
                value.getEligibleChecks(), value.getBindingReuseChecks(), value.getGuardedChecks(), value.getProcessingMs());
    }

    public NewsAiEfficiency dailyEfficiency(LocalDateTime now) {
        String start = now.toLocalDate().atStartOfDay().toString();
        String end = now.toLocalDate().plusDays(1).atStartOfDay().toString();
        var value = new NewsAiEfficiency();
        var runs = jdbc.queryForMap("SELECT COALESCE(SUM(eligible_checks),0) eligible,COALESCE(SUM(binding_reuses),0) reused,COALESCE(SUM(guarded_checks),0) guarded,COALESCE(SUM(processing_ms),0) elapsed FROM news_ai_efficiency_run WHERE created_at>=? AND created_at<?", start, end);
        value.setEligibleChecks(((Number) runs.get("eligible")).longValue());
        value.setBindingReuseChecks(((Number) runs.get("reused")).longValue());
        value.setGuardedChecks(((Number) runs.get("guarded")).longValue());
        value.setProcessingMs(((Number) runs.get("elapsed")).longValue());
        var sent = jdbc.queryForMap("SELECT COUNT(*) items,COALESCE(SUM(mode='UNDERSTAND'),0) full_text,COALESCE(SUM(mode='MATCH'),0) matching,COALESCE(SUM(mode='RECLASSIFY'),0) classification,COALESCE(SUM(attempt>1),0) retries,COALESCE(SUM(source_chars),0) original,COALESCE(SUM(sent_chars),0) transmitted "
                + "FROM news_ai_dispatch_item i JOIN news_ai_call c ON c.id=i.call_id WHERE c.created_at>=? AND c.created_at<? AND COALESCE(c.error_type,'')<>'CANCELLED_BEFORE_SEND'", start, end);
        value.setItemsSent(((Number) sent.get("items")).longValue());
        value.setFullTextItems(((Number) sent.get("full_text")).longValue());
        value.setMatchingOnlyItems(((Number) sent.get("matching")).longValue());
        value.setReclassificationItems(((Number) sent.get("classification")).longValue());
        value.setRetryItems(((Number) sent.get("retries")).longValue());
        value.setSourceChars(((Number) sent.get("original")).longValue());
        value.setSentChars(((Number) sent.get("transmitted")).longValue());
        var payload = jdbc.queryForMap("SELECT COALESCE(SUM(request_bytes),0) bytes,COALESCE(SUM(candidate_refs),0) refs,COALESCE(SUM(unique_candidates),0) cards FROM news_ai_dispatch d JOIN news_ai_call c ON c.id=d.call_id WHERE c.created_at>=? AND c.created_at<? AND COALESCE(c.error_type,'')<>'CANCELLED_BEFORE_SEND'", start, end);
        value.setRequestBytes(((Number) payload.get("bytes")).longValue());
        value.setCandidateReferences(((Number) payload.get("refs")).longValue());
        value.setUniqueCandidateCards(((Number) payload.get("cards")).longValue());
        return value;
    }

    public List<NewsAiCallAudit> recentCalls(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("新闻AI调用审计数量须为1至100");
        }
        List<NewsAiCallAudit> calls = jdbc.query("SELECT c.*,d.protocol,d.request_bytes,d.candidate_refs,d.unique_candidates FROM news_ai_call c LEFT JOIN news_ai_dispatch d ON d.call_id=c.id ORDER BY c.created_at DESC,c.id DESC LIMIT ?", (rs, n) -> {
            var call = new NewsAiCallAudit();
            call.setCallId(rs.getString("id"));
            call.setCreatedAt(rs.getString("created_at"));
            call.setModel(rs.getString("model"));
            call.setState(NewsAiCallState.valueOf(rs.getString("state")));
            call.setProtocol(rs.getString("protocol"));
            call.setInputTokens(nullableLong(rs, "input_tokens"));
            call.setOutputTokens(nullableLong(rs, "output_tokens"));
            call.setActualMicros(nullableLong(rs, "actual_micros"));
            call.setReservedMicros(rs.getLong("reserved_micros"));
            call.setRequestBytes(nullableLong(rs, "request_bytes"));
            call.setCandidateReferences(nullableInteger(rs, "candidate_refs"));
            call.setUniqueCandidateCards(nullableInteger(rs, "unique_candidates"));
            call.setErrorType(rs.getString("error_type"));
            call.setCancelledBeforeSend("CANCELLED_BEFORE_SEND".equals(call.getErrorType()));
            return call;
        }, limit);
        if (calls.isEmpty()) {
            return calls;
        }
        Map<String, NewsAiCallAudit> byId = new HashMap<>();
        calls.forEach(call -> byId.put(call.getCallId(), call));
        String placeholders = String.join(",", java.util.Collections.nCopies(calls.size(), "?"));
        jdbc.query("SELECT * FROM news_ai_dispatch_item WHERE call_id IN (" + placeholders + ") ORDER BY call_id,job_key", rs -> {
            var item = new NewsAiCallItemAudit();
            item.setReportId(rs.getString("report_id"));
            item.setOccurrenceKey(rs.getString("occurrence_key"));
            item.setMode(NewsAiInputMode.valueOf(rs.getString("mode")));
            item.setAttempt(rs.getInt("attempt"));
            item.setSourceChars(rs.getInt("source_chars"));
            item.setSentChars(rs.getInt("sent_chars"));
            byId.get(rs.getString("call_id")).getItems().add(item);
        }, calls.stream().map(NewsAiCallAudit::getCallId).toArray());
        return calls;
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
        return jdbc.queryForMap("SELECT COALESCE(SUM(CASE WHEN error_type='CANCELLED_BEFORE_SEND' THEN 0 ELSE 1 END),0) calls,COALESCE(SUM(input_tokens),0) input_tokens,COALESCE(SUM(output_tokens),0) output_tokens,"
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
        jdbc.update("DELETE FROM news_ai_content WHERE updated_at<? AND state NOT IN (?,?) AND NOT EXISTS(SELECT 1 FROM news_ai_report r WHERE r.content_key=news_ai_content.content_hash)",
                now.minusDays(30).toString(), NewsAiJobState.CLAIMED.name(), NewsAiJobState.UNKNOWN.name());
        jdbc.update("DELETE FROM news_ai_binding WHERE NOT EXISTS(SELECT 1 FROM news_ai_content c WHERE c.cache_key=news_ai_binding.cache_key)");
        jdbc.update("DELETE FROM news_ai_event WHERE updated_at<? AND NOT EXISTS(SELECT 1 FROM news_ai_report r WHERE r.event_key=news_ai_event.event_key)", now.minusDays(30).toString());
        jdbc.update("DELETE FROM news_ai_dispatch_item WHERE call_id IN (SELECT id FROM news_ai_call WHERE created_at<? AND actual_micros IS NOT NULL)", now.minusDays(90).toString());
        jdbc.update("DELETE FROM news_ai_dispatch WHERE call_id IN (SELECT id FROM news_ai_call WHERE created_at<? AND actual_micros IS NOT NULL)", now.minusDays(90).toString());
        jdbc.update("DELETE FROM news_ai_call WHERE created_at<? AND actual_micros IS NOT NULL", now.minusDays(90).toString());
        jdbc.update("DELETE FROM news_ai_occurrence_job WHERE NOT EXISTS(SELECT 1 FROM news_ai_content c WHERE c.cache_key=news_ai_occurrence_job.cache_key)");
        jdbc.update("DELETE FROM news_ai_understanding WHERE updated_at<? AND state NOT IN (?,?) AND NOT EXISTS(SELECT 1 FROM news_ai_content c WHERE c.content_hash>=news_ai_understanding.body_hash||'@' AND c.content_hash<news_ai_understanding.body_hash||'A')",
                now.minusDays(30).toString(), NewsAiJobState.CLAIMED.name(), NewsAiJobState.UNKNOWN.name());
        jdbc.update("DELETE FROM news_ai_efficiency_run WHERE created_at<?", now.minusDays(14).toString());
    }

    public Map<String, Long> jobCounts() {
        Map<String, Long> result = new HashMap<>();
        jdbc.query("SELECT state,COUNT(*) total FROM news_ai_content GROUP BY state", rs -> {
            result.put(rs.getString("state"), rs.getLong("total"));
        });
        return result;
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.longValue();
    }
    private Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.intValue();
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
