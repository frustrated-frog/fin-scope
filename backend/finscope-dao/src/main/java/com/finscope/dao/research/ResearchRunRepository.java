package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.SourceProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
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
    @Resource
    private JdbcTemplate jdbcTemplate;
    private final RowMapper<ResearchRun> mapper = (rs, rowNum) -> {
        ResearchRun run = new ResearchRun();
        run.setId(rs.getLong("id"));
        long thesisId = rs.getLong("thesis_id");
        run.setThesisId(rs.wasNull() ? null : thesisId);
        run.setRunDate(LocalDate.parse(rs.getString("run_date")));
        run.setThemeCodes(parseThemeCodes(rs.getString("theme_codes")));
        run.setSourceCount(rs.getInt("source_count"));
        run.setFetchedSourceCount(rs.getInt("fetched_source_count"));
        run.setArticleCount(rs.getInt("article_count"));
        run.setEventCount(rs.getInt("event_count"));
        run.setEvidenceCount(rs.getInt("evidence_count"));
        run.setLearningTaskCount(rs.getInt("learning_task_count"));
        run.setContentIdeaCount(rs.getInt("content_idea_count"));
        run.setBriefDate(parseDate(rs.getString("brief_date")));
        run.setStatus(rs.getString("status"));
        run.setSummary(rs.getString("summary"));
        run.setErrorMessage(rs.getString("error_message"));
        run.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        run.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return run;
    };

    public ResearchRun save(ResearchRun run) {
        LocalDateTime now = LocalDateTime.now();
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO research_run(run_date,theme_codes,source_count,fetched_source_count,article_count,"
                            + "event_count,evidence_count,learning_task_count,content_idea_count,brief_date,status,"
                            + "summary,error_message,thesis_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, run.getRunDate().toString());
            ps.setString(2, joinThemeCodes(run.getThemeCodes()));
            ps.setInt(3, run.getSourceCount() == null ? 0 : run.getSourceCount());
            ps.setInt(4, value(run.getFetchedSourceCount()));
            ps.setInt(5, value(run.getArticleCount()));
            ps.setInt(6, value(run.getEventCount()));
            ps.setInt(7, value(run.getEvidenceCount()));
            ps.setInt(8, value(run.getLearningTaskCount()));
            ps.setInt(9, value(run.getContentIdeaCount()));
            ps.setString(10, run.getBriefDate() == null ? null : run.getBriefDate().toString());
            ps.setString(11, run.getStatus());
            ps.setString(12, run.getSummary());
            ps.setString(13, run.getErrorMessage());
            if (run.getThesisId() == null) {
                ps.setObject(14, null);
            } else {
                ps.setLong(14, run.getThesisId());
            }
            ps.setString(15, TimeUtil.text(run.getCreatedAt()));
            ps.setString(16, TimeUtil.text(run.getUpdatedAt()));
            return ps;
        }, keyHolder);
        run.setId(keyHolder.getKey().longValue());
        return run;
    }

    public void replaceSources(Long runId, List<SourceProfile> sources) {
        jdbcTemplate.update("DELETE FROM research_run_source WHERE run_id = ?", runId);
        if (runId == null || sources == null || sources.isEmpty()) {
            return;
        }
        int position = 0;
        for (SourceProfile source : sources) {
            jdbcTemplate.update("INSERT INTO research_run_source(run_id,source_id,source_name,source_tier,theme_codes,"
                            + "credibility,enabled,position) VALUES(?,?,?,?,?,?,?,?)",
                    runId,
                    source.getSourceId(),
                    source.getSourceName(),
                    source.getSourceTier(),
                    joinThemeCodes(source.getThemeCodes()),
                    source.getCredibility(),
                    source.isEnabled() ? 1 : 0,
                    position);
            position++;
        }
    }

    public ResearchRun updateStatus(Long id, String status, String summary, String errorMessage) {
        LocalDateTime updatedAt = LocalDateTime.now();
        jdbcTemplate.update("UPDATE research_run SET status = ?, summary = ?, error_message = ?, updated_at = ? WHERE id = ?",
                status, summary, errorMessage, TimeUtil.text(updatedAt), id);
        return findById(id).orElseThrow(() -> new IllegalArgumentException("Research run not found: " + id));
    }

    public int failRunningRuns(String errorMessage) {
        LocalDateTime updatedAt = LocalDateTime.now();
        return jdbcTemplate.update("UPDATE research_run SET status = 'FAILED', "
                        + "summary = 'Run interrupted before completion', error_message = ?, updated_at = ? "
                        + "WHERE status = 'RUNNING'",
                errorMessage, TimeUtil.text(updatedAt));
    }

    public ResearchRun updateResult(ResearchRun run) {
        run.setUpdatedAt(LocalDateTime.now());
        jdbcTemplate.update("UPDATE research_run SET fetched_source_count=?, article_count=?, event_count=?, "
                        + "evidence_count=?, learning_task_count=?, content_idea_count=?, brief_date=?, status=?, "
                        + "summary=?, error_message=?, updated_at=? WHERE id=?",
                value(run.getFetchedSourceCount()), value(run.getArticleCount()), value(run.getEventCount()),
                value(run.getEvidenceCount()), value(run.getLearningTaskCount()), value(run.getContentIdeaCount()),
                run.getBriefDate() == null ? null : run.getBriefDate().toString(), run.getStatus(), run.getSummary(),
                run.getErrorMessage(), TimeUtil.text(run.getUpdatedAt()), run.getId());
        return findById(run.getId()).orElseThrow(() -> new IllegalArgumentException("Research run not found: " + run.getId()));
    }

    public List<ResearchRun> findAll() {
        return jdbcTemplate.query("SELECT * FROM research_run ORDER BY created_at DESC, id DESC", mapper);
    }

    public List<ResearchRun> findByThesisId(Long thesisId) {
        return jdbcTemplate.query("SELECT * FROM research_run WHERE thesis_id = ? ORDER BY created_at DESC, id DESC", mapper, thesisId);
    }

    public Optional<ResearchRun> findById(Long id) {
        List<ResearchRun> runs = jdbcTemplate.query("SELECT * FROM research_run WHERE id = ?", mapper, id);
        return runs.isEmpty() ? Optional.empty() : Optional.of(runs.get(0));
    }

    public List<SourceProfile> findSourcesByRunId(Long runId) {
        return jdbcTemplate.query("SELECT * FROM research_run_source WHERE run_id = ? ORDER BY position ASC",
                (rs, rowNum) -> {
                    SourceProfile source = new SourceProfile();
                    long sourceId = rs.getLong("source_id");
                    source.setSourceId(rs.wasNull() ? null : sourceId);
                    source.setSourceName(rs.getString("source_name"));
                    source.setSourceTier(rs.getString("source_tier"));
                    source.setThemeCodes(parseThemeCodes(rs.getString("theme_codes")));
                    int credibility = rs.getInt("credibility");
                    source.setCredibility(rs.wasNull() ? null : credibility);
                    source.setEnabled(rs.getInt("enabled") == 1);
                    return source;
                },
                runId);
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

    private static LocalDate parseDate(String value) {
        return value == null || value.trim().isEmpty() ? null : LocalDate.parse(value);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
