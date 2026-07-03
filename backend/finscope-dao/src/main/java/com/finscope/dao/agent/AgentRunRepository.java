package com.finscope.dao.agent;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.agent.AgentRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AgentRunRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<AgentRun> mapper = (rs, rowNum) -> {
        AgentRun run = new AgentRun();
        run.setId(rs.getLong("id"));
        run.setResearchRunId(readLong(rs, "research_run_id"));
        run.setEventId(readLong(rs, "event_id"));
        run.setArticleId(readLong(rs, "article_id"));
        run.setNodeName(rs.getString("node_name"));
        run.setStatus(rs.getString("status"));
        run.setInput(rs.getString("input"));
        run.setOutput(rs.getString("output"));
        run.setErrorMessage(rs.getString("error_message"));
        run.setDurationMs(rs.getLong("duration_ms"));
        run.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return run;
    };

    public void record(String nodeName, String status, String input, String output, String errorMessage, long durationMs) {
        record(null, null, null, nodeName, status, input, output, errorMessage, durationMs);
    }

    public void record(Long researchRunId,
                       Long eventId,
                       Long articleId,
                       String nodeName,
                       String status,
                       String input,
                       String output,
                       String errorMessage,
                       long durationMs) {
        jdbcTemplate.update("INSERT INTO agent_run(research_run_id,event_id,article_id,node_name,status,input,output,"
                        + "error_message,duration_ms,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                researchRunId, eventId, articleId, nodeName, status, input, output, errorMessage,
                durationMs, TimeUtil.text(LocalDateTime.now()));
    }

    public List<AgentRun> latest(int limit) {
        return jdbcTemplate.query("SELECT * FROM agent_run ORDER BY id DESC LIMIT ?", mapper, limit);
    }

    public List<AgentRun> findByResearchRunId(Long researchRunId) {
        return jdbcTemplate.query("SELECT * FROM agent_run WHERE research_run_id = ? ORDER BY id ASC",
                mapper, researchRunId);
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
