package com.finscope.dao.agent;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@DependsOn("databaseInitializer")
public class AgentTraceSchemaMigrator implements InitializingBean {
    private static final int VERSION=101;private final JdbcTemplate jdbc;private final TransactionTemplate transaction;
    public AgentTraceSchemaMigrator(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;this.transaction=new TransactionTemplate(manager);}
    @Override public void afterPropertiesSet(){migrate();}
    public void migrate(){jdbc.execute("CREATE TABLE IF NOT EXISTS schema_migration (version INTEGER PRIMARY KEY,description TEXT NOT NULL,applied_at TEXT NOT NULL)");
        transaction.executeWithoutResult(status->{Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM schema_migration WHERE version=?",Integer.class,VERSION);if(count!=null&&count>0)return;
            if(!hasColumn("agent_run","subject_type"))jdbc.execute("ALTER TABLE agent_run ADD COLUMN subject_type TEXT");
            if(!hasColumn("agent_run","subject_id"))jdbc.execute("ALTER TABLE agent_run ADD COLUMN subject_id INTEGER");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_agent_run_subject ON agent_run(subject_type,subject_id,id)");
            jdbc.update("INSERT INTO schema_migration(version,description,applied_at) VALUES(?,?,?)",VERSION,"generic agent trace subject",LocalDateTime.now().toString());});}
    private boolean hasColumn(String table,String column){List<Map<String,Object>> rows=jdbc.queryForList("PRAGMA table_info("+table+")");return rows.stream().anyMatch(v->column.equals(v.get("name")));}
}
