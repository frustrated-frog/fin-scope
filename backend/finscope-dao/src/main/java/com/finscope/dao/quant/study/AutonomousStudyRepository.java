package com.finscope.dao.quant.study;

import com.finscope.common.enums.quant.StudyState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Repository
public class AutonomousStudyRepository {
    private static final String SUMMARY = "id,trigger_key,state,created,updated,attempts,plan,report,note";
    @Resource
    private JdbcTemplate jdbc;

    public long enqueue(String key, long now) {
        jdbc.update("INSERT INTO quant_autonomous_study(trigger_key,state,created,updated) VALUES(?,?,?,?) "
                + "ON CONFLICT(trigger_key) DO NOTHING", key, StudyState.QUEUED.name(), now, now);
        return jdbc.queryForObject("SELECT id FROM quant_autonomous_study WHERE trigger_key=?", Long.class, key);
    }

    public Long claim(String lease, long now, long until) {
        int count = jdbc.update("""
                UPDATE quant_autonomous_study SET lease=?,lease_until=?,updated=?,
                state=CASE WHEN evidence IS NOT NULL THEN 'WRITING' WHEN plan IS NOT NULL THEN 'COMPUTING' ELSE 'PLANNING' END
                WHERE id=(SELECT id FROM quant_autonomous_study WHERE state IN ('QUEUED','PLANNING','COMPUTING','WRITING')
                AND due<=? AND lease_until<=? ORDER BY id LIMIT 1)
                """, lease, until, now, now, now);
        if (count == 0) {
            return null;
        }
        return jdbc.queryForObject("SELECT id FROM quant_autonomous_study WHERE lease=?", Long.class, lease);
    }

    public List<Map<String,Object>> list(int offset, int limit) {
        return jdbc.queryForList("SELECT " + SUMMARY + " FROM quant_autonomous_study ORDER BY id DESC LIMIT ? OFFSET ?", limit, offset);
    }

    public List<Map<String,Object>> memory() {
        return jdbc.queryForList("SELECT id,plan,report,state,note FROM quant_autonomous_study WHERE plan IS NOT NULL "
                + "AND state IN ('PUBLISHED','FAILED') ORDER BY id DESC LIMIT 16");
    }

    public List<Map<String,Object>> highlights() {
        return jdbc.queryForList("""
                WITH rated AS (
                    SELECT id, plan, report, created, updated, state, trigger_key, note,
                      json_extract(plan,'$.material.tool') AS tool,
                      COALESCE(json_extract(report,'$.merits.学习价值'),0)*3
                        + COALESCE(json_extract(report,'$.merits.方法价值'),0)
                        + COALESCE(json_extract(report,'$.merits.研究价值'),0) AS rating
                    FROM quant_autonomous_study WHERE state='PUBLISHED'
                ), ranked AS (
                    SELECT *,row_number() OVER (PARTITION BY tool ORDER BY rating DESC,id DESC) AS position FROM rated
                ) SELECT id,plan,report,created,updated,state,trigger_key,note FROM ranked
                WHERE position=1 ORDER BY rating DESC,id DESC LIMIT 3
                """);
    }

    public List<String> usedSources() {
        return jdbc.queryForList("SELECT source_key FROM quant_autonomous_study WHERE source_key IS NOT NULL", String.class);
    }

    public Map<String,Object> find(long id) {
        return jdbc.queryForList("SELECT " + SUMMARY + ",evidence FROM quant_autonomous_study WHERE id=?", id)
                .stream().findFirst().orElse(null);
    }

    public boolean plan(long id, String lease, String source, String plan, long now) {
        return jdbc.update("UPDATE OR IGNORE quant_autonomous_study SET source_key=?,plan=?,state='COMPUTING',attempts=0,updated=?,note=NULL "
                + "WHERE id=? AND lease=?", source, plan, now, id, lease) == 1;
    }

    public boolean evidence(long id, String lease, String evidence, long now) {
        return jdbc.update("UPDATE quant_autonomous_study SET evidence=?,state='WRITING',updated=?,attempts=0,note=NULL WHERE id=? AND lease=?",
                evidence, now, id, lease) == 1;
    }

    public void finish(long id, String lease, StudyState state, String report, String note, long now) {
        jdbc.update("UPDATE quant_autonomous_study SET state=?,report=?,note=?,updated=?,lease=NULL,lease_until=0 WHERE id=? AND lease=?",
                state.name(), report, note, now, id, lease);
    }

    public void fail(long id, String lease, String note, long now) {
        jdbc.update("""
                UPDATE quant_autonomous_study SET attempts=attempts+1,state=CASE WHEN attempts>=2 THEN 'FAILED' ELSE 'QUEUED' END,
                note=?,updated=?,due=?,lease=NULL,lease_until=0 WHERE id=? AND lease=?
                """, note, now, now+120, id, lease);
    }

    public void retry(long id, long now) {
        jdbc.update("UPDATE quant_autonomous_study SET state='QUEUED',due=0,attempts=0,note=NULL,updated=? "
                + "WHERE id=? AND state IN ('FAILED','WAITING_DATA')", now,id);
    }
}
