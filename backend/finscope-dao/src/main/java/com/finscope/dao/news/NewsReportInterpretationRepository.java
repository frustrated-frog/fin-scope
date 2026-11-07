package com.finscope.dao.news;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.news.NewsInterpretationStatus;
import com.finscope.domain.news.NewsReportInterpretation;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Optional;

@Repository
@DependsOn("newsWindowSchemaMigrator")
public class NewsReportInterpretationRepository {
    private static final long JOB_LEASE_MS = Duration.ofMinutes(2).toMillis();
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private ObjectMapper mapper;

    /** 唯一键与条件更新保证同版本只有一个有效任务。 */
    public boolean tryQueue(String id, int version, String token, long nowMs) {
        return jdbc.update("INSERT INTO news_report_interpretation(report_id,content_version,status,claim_token,queued_at_ms) "
                        + "VALUES(?,?,'QUEUED',?,?) ON CONFLICT(report_id,content_version) DO UPDATE SET "
                        + "status='QUEUED',claim_token=excluded.claim_token,queued_at_ms=excluded.queued_at_ms,"
                        + "result_json=NULL,failure_message=NULL WHERE status='FAILED' "
                        + "OR (status IN ('QUEUED','RUNNING') AND queued_at_ms<=?)",
                id, version, token, nowMs, nowMs - JOB_LEASE_MS) == 1;
    }

    public boolean start(String id, int version, String token) {
        return jdbc.update("UPDATE news_report_interpretation SET status='RUNNING' "
                + "WHERE report_id=? AND content_version=? AND claim_token=? AND status='QUEUED'", id, version, token) == 1;
    }

    public void finish(String id, int version, String token, NewsReportInterpretation.Result result, String failure) {
        try {
            NewsInterpretationStatus status = result == null ? NewsInterpretationStatus.FAILED : NewsInterpretationStatus.SUCCESS;
            jdbc.update("UPDATE news_report_interpretation SET status=?,result_json=?,failure_message=? "
                            + "WHERE report_id=? AND content_version=? AND claim_token=? AND status IN ('QUEUED','RUNNING')",
                    status.name(), result == null ? null : mapper.writeValueAsString(result), failure, id, version, token);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("新闻解读结果保存失败", error);
        }
    }

    public Optional<NewsReportInterpretation> find(String id, int version, long nowMs) {
        return jdbc.query("SELECT * FROM news_report_interpretation WHERE report_id=? AND content_version=?",
                row(nowMs), id, version).stream().findFirst();
    }

    public Optional<NewsReportInterpretation> latestSuccessBefore(String id, int version) {
        return jdbc.query("SELECT * FROM news_report_interpretation WHERE report_id=? AND content_version<? "
                + "AND status='SUCCESS' ORDER BY content_version DESC LIMIT 1", row(0), id, version).stream().findFirst();
    }

    private RowMapper<NewsReportInterpretation> row(long nowMs) {
        return (rs, index) -> {
            NewsReportInterpretation value = new NewsReportInterpretation();
            value.setContentVersion(rs.getInt("content_version"));
            value.setStatus(NewsInterpretationStatus.valueOf(rs.getString("status")));
            value.setFailureMessage(rs.getString("failure_message"));
            if ((value.getStatus() == NewsInterpretationStatus.QUEUED || value.getStatus() == NewsInterpretationStatus.RUNNING)
                    && rs.getLong("queued_at_ms") <= nowMs - JOB_LEASE_MS) {
                value.setStatus(NewsInterpretationStatus.FAILED);
                value.setFailureMessage("解读任务超时，可以重试");
            }
            String result = rs.getString("result_json");
            if (result != null) {
                try {
                    value.setResult(mapper.readValue(result, NewsReportInterpretation.Result.class));
                } catch (JsonProcessingException error) {
                    throw new IllegalStateException("新闻解读结果读取失败", error);
                }
            }
            return value;
        };
    }
}
