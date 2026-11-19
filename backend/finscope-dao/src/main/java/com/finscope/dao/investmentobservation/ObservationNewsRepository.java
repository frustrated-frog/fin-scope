package com.finscope.dao.investmentobservation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.fundamentals.CompanyBusinessProfile;
import com.finscope.domain.investmentobservation.*;
import com.finscope.domain.news.ai.NewsAiAnalysis;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class ObservationNewsRepository {
    public static final String METHOD = "OBSERVATION_ATTRIBUTION_V1";
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private ObjectMapper mapper;
    @Resource
    private ReactionSampleRepository samples;
    @Resource
    private com.finscope.dao.news.NewsAiRepository ledger;

    public List<ObservationNewsInput> inputs(LocalDateTime since, LocalDateTime now) {
        return jdbc.query("""
                WITH eligible AS (
                    SELECT n.id,n.content_version,a.event_key,n.title,n.content,n.url,n.published_at,n.first_seen_at,
                           json_extract(b.payload,'$.analysis') analysis,k.signature,
                           row_number() OVER (PARTITION BY a.event_key,(json_extract(b.payload,'$.analysis.eventType')='POLICY') ORDER BY
                               CASE a.relation WHEN 'PROGRESS' THEN 0 WHEN 'NEW_EVENT' THEN 1 WHEN 'DUPLICATE' THEN 3 ELSE 2 END,
                               CASE WHEN a.relation='PROGRESS' THEN n.published_at END DESC,n.published_at,n.id) position
                    FROM news_report n JOIN news_ai_report a ON a.report_id=n.id AND a.content_version=n.content_version
                    JOIN news_ai_occurrence_job j ON j.occurrence_key=a.content_key JOIN news_ai_binding b ON b.cache_key=j.cache_key AND json_extract(b.payload,'$.eventKey')=a.event_key
                    LEFT JOIN investment_news_checked k ON k.report_id=n.id
                    WHERE n.published_at BETWEEN ? AND ? AND (json_extract(b.payload,'$.analysis.eventType')='POLICY'
                         OR json_array_length(b.payload,'$.analysis.companyActions')>0 OR k.report_id IS NOT NULL
                         OR (json_type(b.payload,'$.analysis.companyActions') IS NULL
                             AND json_extract(b.payload,'$.analysis.eventType') IN ('TRANSACTION','EARNINGS','DISCLOSURE')
                             AND json_array_length(b.payload,'$.analysis.entities')>0)))
                SELECT * FROM eligible ORDER BY published_at DESC,id
                """, (rs,i) -> new ObservationNewsInput(rs.getString("id"),rs.getInt("content_version"),rs.getString("event_key"),
                rs.getString("title"),rs.getString("content"),rs.getString("url"),LocalDateTime.parse(rs.getString("published_at")),
                LocalDateTime.parse(rs.getString("first_seen_at")),read(rs.getString("analysis"), NewsAiAnalysis.class),
                rs.getString("signature"),rs.getInt("position")==1), since.toString(),now.toString());
    }

    public List<ObservationCompany> companies(LocalDateTime now, boolean dueOnly) {
        String targets = """
                WITH stocks AS (
                    SELECT i.code||'.'||i.market code,i.name,0 priority FROM watchlist_item w JOIN instrument i ON i.id=w.instrument_id
                    WHERE i.type='STOCK' AND i.market IN ('SH','SZ','BJ')
                    UNION ALL SELECT instrument_code,json_extract(snapshot_json,'$.instrumentName'),CASE WHEN followed=1 THEN 1 ELSE 2 END
                    FROM investment_reaction_sample WHERE excluded=0 AND (followed=1 OR state='OBSERVING') AND instrument_code<>''),
                targets AS (SELECT code,max(name) name,min(priority) priority FROM stocks GROUP BY code)
                SELECT t.code,t.name,i.id,coalesce(p.profile_json,c.profile_json) profile FROM targets t
                LEFT JOIN instrument i ON i.code=substr(t.code,1,6) AND i.market=substr(t.code,8) AND i.type='STOCK'
                LEFT JOIN company_business_profile p ON p.instrument_id=i.id LEFT JOIN investment_news_profile c ON c.code=t.code
                """;
        String filter = dueOnly ? " WHERE coalesce(c.next_at,'')<=? AND coalesce(p.updated_at,'')<? ORDER BY t.priority,coalesce(c.next_at,''),t.code LIMIT 2"
                : " WHERE coalesce(p.profile_json,c.profile_json) IS NOT NULL ORDER BY t.code";
        return jdbc.query(targets + filter, (rs,i) -> new ObservationCompany(rs.getObject("id") == null ? null : rs.getLong("id"),
                rs.getString("code"),rs.getString("name"),read(rs.getString("profile"), CompanyBusinessProfile.class)),
                dueOnly ? new Object[]{now.toString(), now.minusDays(30).toString()} : new Object[]{});
    }

    public void profileAttempt(String code, CompanyBusinessProfile profile, LocalDateTime next) {
        jdbc.update("""
                INSERT INTO investment_news_profile(code,profile_json,next_at) VALUES(?,?,?) ON CONFLICT(code)
                DO UPDATE SET profile_json=coalesce(excluded.profile_json,investment_news_profile.profile_json),next_at=excluded.next_at
                """, code,profile == null ? null : write(profile), next.toString());
    }

    public Map<String,String> verdicts(List<String> keys) {
        Map<String,String> result = new HashMap<>();
        for (int start = 0; start < keys.size(); start += 250) {
            var batch = keys.subList(start, Math.min(keys.size(), start + 250));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            jdbc.query("SELECT id,verdict FROM investment_news_verification WHERE id IN (" + placeholders + ")", rs -> {
                result.put(rs.getString(1), rs.getString(2));
            }, batch.toArray());
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean reserveVerification(List<String> keys, String callId, LocalDateTime now, String model, long upperMicros,
                                       Long dailyMicros, Integer dailyCalls) {
        for (String key : keys) {
            jdbc.update("INSERT INTO investment_news_verification(id,call_id,created_at) VALUES(?,?,?)",key,callId,now.toString());
        }
        if (ledger.reserve(callId,model,upperMicros,dailyMicros,dailyCalls,now)) {
            return true;
        }
        jdbc.update("DELETE FROM investment_news_verification WHERE call_id=?", callId);
        return false;
    }

    @Transactional(rollbackFor = Exception.class)
    public void finish(String callId, Map<String,String> decisions) {
        decisions.forEach((key,value) -> jdbc.update("UPDATE investment_news_verification SET verdict=? WHERE id=? AND call_id=?",
                value,key,callId));
    }

    @Transactional(rollbackFor = Exception.class)
    public int reconcile(ObservationNewsInput input, String signature, List<ReactionSample> matches) {
        int accepted = jdbc.update("""
                INSERT INTO investment_news_checked(report_id,signature) SELECT n.id,? FROM news_report n
                JOIN news_ai_report a ON a.report_id=n.id AND a.content_version=n.content_version
                WHERE n.id=? AND n.content_version=? AND a.event_key=? ON CONFLICT(report_id) DO UPDATE SET signature=excluded.signature
                WHERE investment_news_checked.signature<>excluded.signature
                """, signature,input.reportId(),input.version(),input.eventKey());
        if (accepted == 0) {
            return 0;
        }
        List<Long> previous = jdbc.queryForList("SELECT sample_id FROM investment_news_link WHERE report_id=?", Long.class,input.reportId());
        jdbc.update("DELETE FROM investment_news_link WHERE report_id=?",input.reportId());
        int added = 0;
        if (!matches.isEmpty()) {
            var first = matches.getFirst();
            // 原规则可能已经收录这次事件的另一份报道，优先沿用该观察身份。
            var known = jdbc.queryForList("""
                    SELECT o.event_key FROM news_ai_report a JOIN investment_reaction_source o
                    ON o.origin_type='NEWS_ITEM' AND o.origin_key=a.report_id
                    JOIN news_report n ON n.id=a.report_id AND n.content_version=a.content_version
                    WHERE a.event_key=? ORDER BY o.captured_at,o.origin_key LIMIT 1
                    """,String.class,input.eventKey());
            if (!known.isEmpty()) {
                first.setSourceIdentity(known.getFirst());
            }
            boolean created = samples.captureSource(first);
            Set<String> existing = new HashSet<>(jdbc.queryForList("SELECT instrument_code FROM investment_reaction_sample WHERE source_identity=?",
                    String.class, first.getSourceIdentity()));
            for (ReactionSample proposed : matches) {
                proposed.setSourceIdentity(first.getSourceIdentity());
                var sample = samples.create(proposed);
                if (!existing.contains(proposed.getInstrumentCode()) || created && proposed == first) {
                    added++;
                }
                jdbc.update("INSERT INTO investment_news_link(report_id,sample_id,version,note,evidence) VALUES(?,?,?,?,?)",
                        input.reportId(),sample.getId(),input.version(),proposed.getRelationNote(),proposed.getRuleEvidence());
                if (jdbc.update("DELETE FROM investment_news_withdrawn WHERE sample_id=?",sample.getId()) == 1) {
                    jdbc.update("UPDATE investment_reaction_sample SET excluded=0,revision=revision+1 WHERE id=?",sample.getId());
                }
                previous.remove(sample.getId());
                refreshEvidence(sample.getId());
            }
        }
        previous.forEach(this::refreshEvidence);
        return added;
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdrawRevisions() {
        // 修订一旦入库即撤销旧版本的推断；历史来源和用户操作仍保留。
        var affected = jdbc.queryForList("""
                DELETE FROM investment_news_link WHERE EXISTS(SELECT 1 FROM news_report n
                WHERE n.id=report_id AND n.content_version<>investment_news_link.version) RETURNING sample_id
                """,Long.class);
        affected.stream().distinct().forEach(this::refreshEvidence);
    }

    private void refreshEvidence(long id) {
        var evidence = jdbc.queryForList("""
                SELECT note,evidence FROM investment_news_link WHERE sample_id=? ORDER BY report_id LIMIT 1
                """,id);
        if (!evidence.isEmpty()) {
            var row = evidence.getFirst();
            jdbc.update("""
                    UPDATE investment_reaction_sample SET snapshot_json=json_set(snapshot_json,'$.relationNote',?,'$.ruleEvidence',?),revision=revision+1
                    WHERE id=? AND json_extract(snapshot_json,'$.ruleVersion')=? AND
                    (json_extract(snapshot_json,'$.relationNote') IS NOT ? OR json_extract(snapshot_json,'$.ruleEvidence') IS NOT ?)
                    """,row.get("note"),row.get("evidence"),id,METHOD,row.get("note"),row.get("evidence"));
        } else if (jdbc.update("UPDATE investment_reaction_sample SET excluded=1,revision=revision+1 WHERE id=? AND excluded=0 AND json_extract(snapshot_json,'$.ruleVersion')=?",
                id,METHOD) == 1) {
            jdbc.update("INSERT OR IGNORE INTO investment_news_withdrawn VALUES(?)",id);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return json == null ? null : mapper.readValue(json,type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("读取新闻关联资料失败",e);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("写入新闻关联资料失败",e);
        }
    }
}
