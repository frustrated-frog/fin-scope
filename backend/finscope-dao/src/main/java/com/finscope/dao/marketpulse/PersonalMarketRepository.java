package com.finscope.dao.marketpulse;

import com.finscope.domain.marketpulse.PersonalMarketChange;
import com.finscope.common.enums.marketpulse.PersonalChangeCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

/** 只组合已保存的自选事件、观察变化和归因，不触发采集或模型。 */
@Repository
public class PersonalMarketRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    public List<PersonalMarketChange> findChanges(LocalDate date) {
        String sql = "WITH watched AS (SELECT i.code,i.market,i.name,w.focus_reason,w.next_watch "
                + "FROM watchlist_item w JOIN instrument i ON i.id=w.instrument_id WHERE i.type='STOCK'), "
                + "events AS (SELECT s.*,w.code,w.name,w.focus_reason,w.next_watch FROM investment_reaction_sample s "
                + "JOIN watched w ON (s.instrument_code=w.code OR s.instrument_code=w.code||'.'||w.market) "
                + "WHERE s.excluded=0 AND s.state<>'DRAFT'), combined AS ("
                + "SELECT 'event:'||s.id AS id,'COMPANY' AS category,s.code,s.name,"
                + "json_extract(s.snapshot_json,'$.title') AS title,json_extract(s.snapshot_json,'$.summary') AS summary,"
                + "COALESCE(json_extract(s.snapshot_json,'$.publishedAt'),s.registered_at) AS occurred_at,"
                + "s.focus_reason,s.next_watch,s.source_identity AS event_key,s.id AS sample_id,NULL AS report_id FROM events s "
                + "UNION ALL SELECT 'change:'||c.id,'TRACKING',s.code,s.name,"
                + "json_extract(s.snapshot_json,'$.title'),c.summary,c.detected_at,s.focus_reason,s.next_watch,"
                + "s.source_identity,s.id,NULL FROM investment_reaction_change c JOIN events s ON c.sample_id=s.id "
                + "WHERE c.trade_date<=? AND c.id=(SELECT MAX(c2.id) FROM investment_reaction_change c2 "
                + "WHERE c2.sample_id=c.sample_id AND c2.trade_date<=? AND substr(c2.detected_at,1,10)<=?) "
                + "UNION ALL SELECT 'report:'||r.id,'COMPANY',w.code,w.name,'归因研究更新',r.summary,"
                + "r.report_date,w.focus_reason,w.next_watch,NULL,NULL,r.id FROM attribution_report r "
                + "JOIN watched w ON r.instrument_code=w.code WHERE r.instrument_type='STOCK' "
                + "AND r.summary IS NOT NULL AND r.status='COMPLETED' AND substr(r.created_at,1,10)<=? "
                + "AND r.id=(SELECT MAX(r2.id) FROM attribution_report r2 WHERE r2.instrument_code=r.instrument_code "
                + "AND r2.instrument_type='STOCK' AND r2.report_date=r.report_date AND r2.status='COMPLETED' "
                + "AND substr(r2.created_at,1,10)<=?)) "
                + "SELECT * FROM combined WHERE substr(occurred_at,1,10)>=? AND substr(occurred_at,1,10)<=? "
                + "ORDER BY occurred_at DESC,id DESC LIMIT 40";
        String end = date.toString();
        return jdbcTemplate.query(sql, (rs, row) -> {
            var value = new PersonalMarketChange();
            value.setId(rs.getString("id"));
            value.setCategory(PersonalChangeCategory.valueOf(rs.getString("category")));
            value.setCode(rs.getString("code"));
            value.setName(rs.getString("name"));
            value.setTitle(rs.getString("title"));
            value.setSummary(rs.getString("summary"));
            value.setOccurredAt(rs.getString("occurred_at"));
            value.setReason(rs.getString("focus_reason"));
            value.setNextWatch(rs.getString("next_watch"));
            value.setEventKey(rs.getString("event_key"));
            long sampleId = rs.getLong("sample_id");
            value.setSampleId(rs.wasNull() ? null : sampleId);
            long reportId = rs.getLong("report_id");
            value.setReportId(rs.wasNull() ? null : reportId);
            return value;
        }, end,end,end,end,end,date.minusDays(6).toString(),end);
    }
}
