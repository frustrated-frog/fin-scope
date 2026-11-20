package com.finscope.dao.majorevent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.news.NewsInterpretationStatus;
import com.finscope.domain.news.NewsReportInterpretation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Optional;

@Repository
public class MajorEventReadingRepository {
    private static final long LEASE_MS = Duration.ofMinutes(2).toMillis();
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private ObjectMapper mapper;

    public Optional<NewsReportInterpretation> find(long id, long now) {
        return jdbc.query("SELECT * FROM major_event_reading WHERE event_id=?", (rs, row) -> {
            var reading = new NewsReportInterpretation();
            reading.setContentVersion(1);
            String json = rs.getString("result_json");
            if (json != null) {
                try {
                    reading.setResult(mapper.readValue(json, NewsReportInterpretation.Result.class));
                } catch (JsonProcessingException error) {
                    throw new IllegalStateException("归档解读读取失败", error);
                }
                reading.setStatus(NewsInterpretationStatus.SUCCESS);
            } else if (rs.getString("failure_message") != null || rs.getLong("claimed_at_ms") <= now - LEASE_MS) {
                reading.setStatus(NewsInterpretationStatus.FAILED);
                reading.setFailureMessage("上次解读未完成，可以重试");
            } else {
                reading.setStatus(NewsInterpretationStatus.RUNNING);
            }
            return reading;
        }, id).stream().findFirst();
    }

    public boolean claim(long id, String token, long now) {
        return jdbc.update("""
                INSERT INTO major_event_reading(event_id,claim_token,claimed_at_ms) VALUES(?,?,?)
                ON CONFLICT(event_id) DO UPDATE SET claim_token=excluded.claim_token,
                    claimed_at_ms=excluded.claimed_at_ms,failure_message=NULL
                WHERE result_json IS NULL AND (failure_message IS NOT NULL OR claimed_at_ms<=?)
                """, id, token, now, now - LEASE_MS) == 1;
    }

    public boolean start(long id, String token, long now) {
        return jdbc.update("UPDATE major_event_reading SET claimed_at_ms=? WHERE event_id=? AND claim_token=?",
                now, id, token) == 1;
    }

    public void finish(long id, String token, NewsReportInterpretation.Result result) {
        jdbc.update("UPDATE major_event_reading SET result_json=?,failure_message=?,claim_token=NULL "
                        + "WHERE event_id=? AND claim_token=?", result == null ? null : json(result),
                result == null ? "生成失败" : null, id, token);
    }

    /** 只为已经归档的雷达事件保留结果；与归档或生成并发时保留第一份成功解读。 */
    public void retainRadar(String originKey, long eventId, NewsReportInterpretation.Result result) {
        jdbc.update("""
                INSERT INTO major_event_reading(event_id,result_json)
                SELECT id,? FROM major_event WHERE origin_type='RADAR_EVENT' AND origin_key IN (?,?)
                ON CONFLICT(event_id) DO UPDATE SET result_json=excluded.result_json,claim_token=NULL,failure_message=NULL
                WHERE major_event_reading.result_json IS NULL
                """, json(result), originKey, Long.toString(eventId));
    }

    private String json(NewsReportInterpretation.Result result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("归档解读保存失败", error);
        }
    }
}
