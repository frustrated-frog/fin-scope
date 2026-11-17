package com.finscope.dao.news;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/** 公告正文失败的持久化重试时钟；列表内容变化时立即重新核验。 */
@Repository
@DependsOn("databaseInitializer")
public class AnnouncementEnrichmentRetryRepository implements InitializingBean {
    @Resource
    private JdbcTemplate jdbc;

    @Override
    public void afterPropertiesSet() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS announcement_enrichment_retry (external_id TEXT PRIMARY KEY,"
                + "content_identity TEXT NOT NULL,failure_count INTEGER NOT NULL,error_type TEXT NOT NULL,"
                + "next_retry_at TEXT NOT NULL,updated_at TEXT NOT NULL)");
    }

    public boolean canAttempt(String id, String identity, LocalDateTime now) {
        Integer waiting = jdbc.queryForObject("SELECT COUNT(*) FROM announcement_enrichment_retry "
                + "WHERE external_id=? AND content_identity=? AND next_retry_at>?", Integer.class, id, identity, now.toString());
        return waiting == null || waiting == 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(String id, String identity, String errorType, boolean retryable, LocalDateTime now) {
        Integer previous = jdbc.queryForObject("SELECT COALESCE(MAX(failure_count),0) FROM announcement_enrichment_retry "
                + "WHERE external_id=? AND content_identity=?", Integer.class, id, identity);
        int failures = Math.min(20, (previous == null ? 0 : previous) + 1);
        long delayMinutes = retryable ? Math.min(360L, 5L << Math.min(7, failures - 1)) : 360L;
        if (jdbc.update("INSERT INTO announcement_enrichment_retry VALUES(?,?,?,?,?,?) ON CONFLICT(external_id) "
                + "DO UPDATE SET content_identity=excluded.content_identity,failure_count=excluded.failure_count,"
                + "error_type=excluded.error_type,next_retry_at=excluded.next_retry_at,updated_at=excluded.updated_at",
                id, identity, failures, errorType, now.plusMinutes(delayMinutes).toString(), now.toString()) != 1) {
            throw new IllegalStateException("公告正文重试状态保存失败");
        }
        jdbc.update("DELETE FROM announcement_enrichment_retry WHERE updated_at<?", now.minusDays(8).toString());
    }

    public void markSuccess(String id) {
        jdbc.update("DELETE FROM announcement_enrichment_retry WHERE external_id=?", id);
    }
}
