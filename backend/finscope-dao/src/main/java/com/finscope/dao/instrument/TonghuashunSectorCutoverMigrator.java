package com.finscope.dao.instrument;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/** 同花顺板块身份切换的一次性迁移：删除不再兼容的 BK 板块关注关系。 */
@Component
@DependsOn("databaseInitializer")
public class TonghuashunSectorCutoverMigrator implements InitializingBean {
    private static final int VERSION = 400;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS schema_migration ("
                + "version INTEGER PRIMARY KEY,description TEXT NOT NULL,applied_at TEXT NOT NULL)");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> migrate());
    }

    private void migrate() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=?", Integer.class, VERSION);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("DELETE FROM watchlist_item WHERE instrument_id IN ("
                + "SELECT id FROM instrument WHERE type='SECTOR' AND UPPER(code) LIKE 'BK%')");
        jdbcTemplate.update("INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                VERSION, "tonghuashun sector identity cutover", LocalDateTime.now().toString());
    }
}
