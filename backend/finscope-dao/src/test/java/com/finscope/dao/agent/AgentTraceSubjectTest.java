package com.finscope.dao.agent;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.agent.AgentRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentTraceSubjectTest {
    @TempDir Path tempDir;

    @Test
    void recordsAndQueriesGenericCapitalInterpretationSubject() throws Exception {
        SQLiteDataSource dataSource=new SQLiteDataSource();dataSource.setUrl("jdbc:sqlite:"+tempDir.resolve("trace.db"));
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);DatabaseInitializer initializer=new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer,"jdbcTemplate",jdbc);ReflectionTestUtils.setField(initializer,"dataRoot",tempDir.toString());initializer.afterPropertiesSet();
        AgentTraceSchemaMigrator migrator=new AgentTraceSchemaMigrator(jdbc,new DataSourceTransactionManager(dataSource));migrator.migrate();migrator.migrate();
        AgentRunRepository repository=new AgentRunRepository(jdbc);AgentRun run=new AgentRun();run.setSubjectType("CAPITAL_INTERPRETATION");run.setSubjectId(88L);
        run.setNodeName("capital-interpret");run.setStatus("SUCCESS");run.setInput("snapshot=10");run.setOutput("hypotheses=1");repository.record(run);
        List<AgentRun> values=repository.findBySubject("CAPITAL_INTERPRETATION",88L);
        assertEquals(1,values.size());assertEquals("capital-interpret",values.get(0).getNodeName());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM schema_migration WHERE version=101",Integer.class));
    }
}
