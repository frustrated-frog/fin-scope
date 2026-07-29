package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.ResearchSearchEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ResearchSearchEvidenceRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    public ResearchSearchEvidenceRepository() {
    }

    public ResearchSearchEvidenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ResearchSearchEvidence> mapper = (rs, rowNum) -> {
        ResearchSearchEvidence value = new ResearchSearchEvidence();
        value.setId(rs.getLong("id"));
        value.setResearchRunId(rs.getLong("research_run_id"));
        value.setDecisionId(rs.getLong("decision_id"));
        value.setProvider(rs.getString("provider"));
        value.setQueryText(rs.getString("query_text"));
        value.setIntent(rs.getString("intent"));
        value.setTitle(rs.getString("title"));
        value.setUrl(rs.getString("url"));
        value.setContent(rs.getString("content"));
        value.setSourceDomain(rs.getString("source_domain"));
        value.setSourceTier(rs.getString("source_tier"));
        value.setRelevanceScore(rs.getDouble("relevance_score"));
        value.setPublishedAt(rs.getString("published_at"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return value;
    };

    public ResearchSearchEvidence save(ResearchSearchEvidence value) {
        if (value.getCreatedAt() == null) value.setCreatedAt(LocalDateTime.now());
        jdbcTemplate.update("INSERT OR IGNORE INTO research_search_evidence("
                        + "research_run_id,decision_id,provider,query_text,intent,title,url,content,source_domain,"
                        + "source_tier,relevance_score,published_at,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                value.getResearchRunId(), value.getDecisionId(), value.getProvider(), value.getQueryText(),
                value.getIntent(), value.getTitle(), value.getUrl(), value.getContent(), value.getSourceDomain(),
                value.getSourceTier(), value.getRelevanceScore() == null ? 0D : value.getRelevanceScore(),
                value.getPublishedAt(), TimeUtil.text(value.getCreatedAt()));
        return findByRunIdAndUrl(value.getResearchRunId(), value.getUrl()).orElse(value);
    }

    public Optional<ResearchSearchEvidence> findByRunIdAndUrl(Long runId, String url) {
        List<ResearchSearchEvidence> values = jdbcTemplate.query(
                "SELECT * FROM research_search_evidence WHERE research_run_id=? AND url=?", mapper, runId, url);
        return values.isEmpty() ? Optional.<ResearchSearchEvidence>empty() : Optional.of(values.get(0));
    }

    public List<ResearchSearchEvidence> findByRunId(Long runId) {
        return jdbcTemplate.query("SELECT * FROM research_search_evidence WHERE research_run_id=? "
                + "ORDER BY relevance_score DESC,id ASC", mapper, runId);
    }

    public int countByRunId(Long runId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM research_search_evidence WHERE research_run_id=?", Integer.class, runId);
        return count == null ? 0 : count;
    }
}
