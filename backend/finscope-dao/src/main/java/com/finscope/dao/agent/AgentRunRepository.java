package com.finscope.dao.agent;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.agent.AgentRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AgentRunRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AgentRun> mapper = (rs, rowNum) -> {
        AgentRun run = new AgentRun();
        run.setId(rs.getLong("id"));
        run.setNodeName(rs.getString("node_name"));
        run.setStatus(rs.getString("status"));
        run.setInput(rs.getString("input"));
        run.setOutput(rs.getString("output"));
        run.setErrorMessage(rs.getString("error_message"));
        run.setDurationMs(rs.getLong("duration_ms"));
        run.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return run;
    };

    public AgentRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String nodeName, String status, String input, String output, String errorMessage, long durationMs) {
        jdbcTemplate.update("INSERT INTO agent_run(node_name,status,input,output,error_message,duration_ms,created_at) VALUES(?,?,?,?,?,?,?)",
                nodeName, status, input, output, errorMessage, durationMs, TimeUtil.text(LocalDateTime.now()));
    }

    public List<AgentRun> latest(int limit) {
        return jdbcTemplate.query("SELECT * FROM agent_run ORDER BY id DESC LIMIT ?", mapper, limit);
    }
}
