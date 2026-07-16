package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.ContentIdea;
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
import java.util.Optional;

@Repository
public class ContentIdeaRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    private final RowMapper<ContentIdea> mapper = (rs, rowNum) -> {
        ContentIdea idea = new ContentIdea();
        idea.setId(rs.getLong("id"));
        idea.setEventId(rs.getLong("event_id"));
        idea.setThemeCode(rs.getString("theme_code"));
        idea.setTitle(rs.getString("title"));
        idea.setAngle(rs.getString("angle"));
        idea.setFormat(rs.getString("format"));
        idea.setAudience(rs.getString("audience"));
        idea.setScore(rs.getInt("score"));
        idea.setScoreReason(rs.getString("score_reason"));
        idea.setOutline(rs.getString("outline"));
        idea.setStatus(rs.getString("status"));
        idea.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        idea.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return idea;
    };

    public ContentIdea save(ContentIdea idea) {
        LocalDateTime now = LocalDateTime.now();
        idea.setCreatedAt(now);
        idea.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO content_idea(event_id,theme_code,title,angle,format,audience,score,score_reason,outline,status,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            if (idea.getEventId() == null) {
                ps.setObject(1, null);
            } else {
                ps.setLong(1, idea.getEventId());
            }
            ps.setString(2, idea.getThemeCode());
            ps.setString(3, idea.getTitle());
            ps.setString(4, idea.getAngle());
            ps.setString(5, idea.getFormat());
            ps.setString(6, idea.getAudience());
            ps.setInt(7, idea.getScore() == null ? 0 : idea.getScore());
            ps.setString(8, idea.getScoreReason());
            ps.setString(9, idea.getOutline());
            ps.setString(10, idea.getStatus());
            ps.setString(11, TimeUtil.text(idea.getCreatedAt()));
            ps.setString(12, TimeUtil.text(idea.getUpdatedAt()));
            return ps;
        }, keyHolder);
        idea.setId(keyHolder.getKey().longValue());
        return idea;
    }

    public List<ContentIdea> findAll() {
        return jdbcTemplate.query("SELECT * FROM content_idea ORDER BY score DESC, id DESC", mapper);
    }

    public List<ContentIdea> findAllPaged(int page, int pageSize) {
        int offset = page * pageSize;
        return jdbcTemplate.query("SELECT * FROM content_idea ORDER BY score DESC, id DESC LIMIT ? OFFSET ?",
                mapper, pageSize, offset);
    }

    public List<ContentIdea> findByEventId(Long eventId) {
        return jdbcTemplate.query("SELECT * FROM content_idea WHERE event_id = ? ORDER BY score DESC, id DESC",
                mapper, eventId);
    }

    public Optional<ContentIdea> findById(Long id) {
        List<ContentIdea> ideas = jdbcTemplate.query("SELECT * FROM content_idea WHERE id = ?", mapper, id);
        return ideas.isEmpty() ? Optional.<ContentIdea>empty() : Optional.of(ideas.get(0));
    }

    public int countByEventId(Long eventId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM content_idea WHERE event_id = ?",
                Integer.class, eventId);
        return count == null ? 0 : count;
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM content_idea", Integer.class);
        return count == null ? 0 : count;
    }

    public ContentIdea updateStatus(Long id, String status) {
        jdbcTemplate.update("UPDATE content_idea SET status = ?, updated_at = ? WHERE id = ?",
                status, TimeUtil.text(LocalDateTime.now()), id);
        return findById(id).orElseThrow(() -> new IllegalStateException("更新后的内容选题不存在：" + id));
    }

    public int moveByEventId(Long sourceEventId, Long targetEventId) {
        return jdbcTemplate.update("UPDATE content_idea SET event_id = ?, updated_at = ? WHERE event_id = ?",
                targetEventId, TimeUtil.text(LocalDateTime.now()), sourceEventId);
    }
}
