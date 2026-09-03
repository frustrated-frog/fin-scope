package com.finscope.dao.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Component
public class EphemeralContentDataMigration {
    static final int VERSION = 402;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public void migrate() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS schema_migration ("
                + "version INTEGER PRIMARY KEY,description TEXT NOT NULL,applied_at TEXT NOT NULL)");
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=?", Integer.class, VERSION);
        if (applied != null && applied > 0) {
            return;
        }

        jdbcTemplate.update("DELETE FROM industry_chain_event_path WHERE impact_id IN "
                + "(SELECT id FROM industry_chain_event_impact)");
        jdbcTemplate.update("DELETE FROM industry_chain_event_impact");
        jdbcTemplate.update("DELETE FROM investment_observation_transition WHERE observation_id IN "
                + "(SELECT id FROM investment_observation WHERE source_type='RADAR_EVENT')");
        jdbcTemplate.update("DELETE FROM investment_observation WHERE source_type='RADAR_EVENT'");
        jdbcTemplate.update("DELETE FROM radar_event_notification");
        jdbcTemplate.update("DELETE FROM radar_event_research_link");
        jdbcTemplate.update("DELETE FROM radar_event_timeline");
        jdbcTemplate.update("DELETE FROM radar_event_observation");
        jdbcTemplate.update("DELETE FROM radar_event_user_state");
        jdbcTemplate.update("DELETE FROM radar_event_interpretation");
        jdbcTemplate.update("DELETE FROM radar_evidence");
        jdbcTemplate.update("DELETE FROM radar_event_signal");
        jdbcTemplate.update("DELETE FROM radar_event_snapshot");
        jdbcTemplate.update("DELETE FROM radar_event");
        jdbcTemplate.update("DELETE FROM radar_signal");
        jdbcTemplate.update("DELETE FROM radar_refresh_step");
        jdbcTemplate.update("DELETE FROM radar_refresh_run");
        jdbcTemplate.update("DELETE FROM radar_pair_decision");
        jdbcTemplate.update("DELETE FROM news_item_classification");
        jdbcTemplate.update("DELETE FROM agent_run WHERE subject_type IN ('RADAR_EVENT','RADAR_CLUSTER') "
                + "OR node_name LIKE 'radar-%'");
        jdbcTemplate.update("INSERT INTO schema_migration(version,description,applied_at) "
                + "VALUES(?,?,CURRENT_TIMESTAMP)", VERSION, "ephemeral news and radar cache cutover");
    }
}
