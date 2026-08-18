package com.finscope.dao.marketdata;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/** 同花顺板块切源迁移：删除会干扰新目录覆盖率基线的旧来源快照。 */
@Component
@DependsOn("databaseInitializer")
public class TonghuashunSectorSnapshotCleanupMigrator implements InitializingBean {
    private static final int VERSION = 401;

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
        jdbcTemplate.update("DELETE FROM market_data_snapshot WHERE capability='SECTOR_CATALOG' "
                + "AND UPPER(provider_family)<>'TONGHUASHUN'");
        jdbcTemplate.update("INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",
                VERSION, "remove legacy sector catalog snapshots", LocalDateTime.now().toString());
    }
}
