package com.finscope.dao.fetch;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.fetch.FetchRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class FetchRunRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    private final RowMapper<FetchRun> mapper = (rs, rowNum) -> {
        FetchRun run = new FetchRun();
        run.setId(rs.getLong("id"));
        run.setSourceId(rs.getLong("source_id"));
        run.setSourceName(rs.getString("source_name"));
        run.setStatus(rs.getString("status"));
        run.setStartedAt(TimeUtil.localDateTime(rs, "started_at"));
        run.setEndedAt(TimeUtil.localDateTime(rs, "ended_at"));
        run.setSuccessCount(rs.getInt("success_count"));
        run.setDuplicateCount(rs.getInt("duplicate_count"));
        run.setErrorMessage(rs.getString("error_message"));
        return run;
    };

    public FetchRun start(Long sourceId, String sourceName) {
        FetchRun run = new FetchRun();
        run.setSourceId(sourceId);
        run.setSourceName(sourceName);
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO fetch_run(source_id,source_name,status,started_at,success_count,duplicate_count) "
                            + "VALUES(?,?,?,?,0,0)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, sourceId);
            ps.setString(2, sourceName);
            ps.setString(3, run.getStatus());
            ps.setString(4, TimeUtil.text(run.getStartedAt()));
            return ps;
        }, keyHolder);
        run.setId(keyHolder.getKey().longValue());
        return run;
    }

    public FetchRun finish(FetchRun run, String status, int successCount, int duplicateCount, String errorMessage) {
        run.setStatus(status);
        run.setSuccessCount(successCount);
        run.setDuplicateCount(duplicateCount);
        run.setErrorMessage(errorMessage);
        run.setEndedAt(LocalDateTime.now());
        jdbcTemplate.update("UPDATE fetch_run SET status=?, ended_at=?, success_count=?, duplicate_count=?, error_message=? WHERE id=?",
                run.getStatus(), TimeUtil.text(run.getEndedAt()), successCount, duplicateCount, errorMessage, run.getId());
        return run;
    }

    public List<FetchRun> latest(int limit) {
        return jdbcTemplate.query("SELECT * FROM fetch_run ORDER BY id DESC LIMIT ?", mapper, limit);
    }
}
