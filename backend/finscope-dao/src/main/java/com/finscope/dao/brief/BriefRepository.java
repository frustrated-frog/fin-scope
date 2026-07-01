package com.finscope.dao.brief;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.brief.Brief;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class BriefRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    private final RowMapper<Brief> mapper = (rs, rowNum) -> {
        Brief brief = new Brief();
        brief.setId(rs.getLong("id"));
        brief.setBriefDate(TimeUtil.localDate(rs, "brief_date"));
        brief.setTitle(rs.getString("title"));
        brief.setContent(rs.getString("content"));
        brief.setMarkdownPath(rs.getString("markdown_path"));
        brief.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return brief;
    };

    public Brief upsert(Brief brief) {
        brief.setCreatedAt(LocalDateTime.now());
        jdbcTemplate.update("INSERT OR REPLACE INTO brief(brief_date,title,content,markdown_path,created_at) VALUES(?,?,?,?,?)",
                TimeUtil.text(brief.getBriefDate()), brief.getTitle(), brief.getContent(), brief.getMarkdownPath(),
                TimeUtil.text(brief.getCreatedAt()));
        return findByDate(brief.getBriefDate()).orElse(brief);
    }

    public Optional<Brief> findByDate(LocalDate date) {
        List<Brief> briefs = jdbcTemplate.query("SELECT * FROM brief WHERE brief_date = ?", mapper, TimeUtil.text(date));
        return briefs.isEmpty() ? Optional.empty() : Optional.of(briefs.get(0));
    }

    public List<Brief> findAll() {
        return jdbcTemplate.query("SELECT * FROM brief ORDER BY brief_date DESC", mapper);
    }
}
