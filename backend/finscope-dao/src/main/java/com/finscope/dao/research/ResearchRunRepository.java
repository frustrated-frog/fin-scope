package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.ResearchRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class ResearchRunRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ResearchRun> mapper = (rs, rowNum) -> {
        ResearchRun run = new ResearchRun();
        run.setId(rs.getLong("id"));
        run.setRunDate(LocalDate.parse(rs.getString("run_date")));
        run.setThemeCodes(parseThemeCodes(rs.getString("theme_codes")));
        run.setSourceCount(rs.getInt("source_count"));
        run.setStatus(rs.getString("status"));
        run.setSummary(rs.getString("summary"));
        run.setErrorMessage(rs.getString("error_message"));
        run.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        run.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return run;
    };

    public ResearchRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ResearchRun save(ResearchRun run) {
        LocalDateTime now = LocalDateTime.now();
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO research_run(run_date,theme_codes,source_count,status,summary,error_message,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, run.getRunDate().toString());
            ps.setString(2, joinThemeCodes(run.getThemeCodes()));
            ps.setInt(3, run.getSourceCount() == null ? 0 : run.getSourceCount());
            ps.setString(4, run.getStatus());
            ps.setString(5, run.getSummary());
            ps.setString(6, run.getErrorMessage());
            ps.setString(7, TimeUtil.text(run.getCreatedAt()));
            ps.setString(8, TimeUtil.text(run.getUpdatedAt()));
            return ps;
        }, keyHolder);
        run.setId(keyHolder.getKey().longValue());
        return run;
    }

    public ResearchRun updateStatus(Long id, String status, String summary, String errorMessage) {
        LocalDateTime updatedAt = LocalDateTime.now();
        jdbcTemplate.update("UPDATE research_run SET status = ?, summary = ?, error_message = ?, updated_at = ? WHERE id = ?",
                status, summary, errorMessage, TimeUtil.text(updatedAt), id);
        return findById(id).orElseThrow(() -> new IllegalArgumentException("Research run not found: " + id));
    }

    public List<ResearchRun> findAll() {
        return jdbcTemplate.query("SELECT * FROM research_run ORDER BY created_at DESC, id DESC", mapper);
    }

    public Optional<ResearchRun> findById(Long id) {
        List<ResearchRun> runs = jdbcTemplate.query("SELECT * FROM research_run WHERE id = ?", mapper, id);
        return runs.isEmpty() ? Optional.empty() : Optional.of(runs.get(0));
    }

    private static String joinThemeCodes(List<String> themeCodes) {
        return themeCodes == null || themeCodes.isEmpty() ? "" : String.join(",", themeCodes);
    }

    private static List<String> parseThemeCodes(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> themeCodes = new ArrayList<String>();
        for (String item : value.split(",")) {
            String themeCode = item.trim();
            if (!themeCode.isEmpty()) {
                themeCodes.add(themeCode);
            }
        }
        return themeCodes;
    }
}
