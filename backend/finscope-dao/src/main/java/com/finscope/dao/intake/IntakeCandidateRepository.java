package com.finscope.dao.intake;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.intake.IntakeCandidate;
import com.finscope.domain.intake.IntakeEnums;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class IntakeCandidateRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<IntakeCandidate> mapper = (rs, rowNum) -> {
        IntakeCandidate candidate = new IntakeCandidate();
        candidate.setId(rs.getLong("id"));
        candidate.setBatchId(rs.getLong("batch_id"));
        candidate.setSourceId(readLong(rs, "source_id"));
        candidate.setSourceName(rs.getString("source_name"));
        candidate.setSourceType(rs.getString("source_type"));
        candidate.setOriginalTitle(rs.getString("original_title"));
        candidate.setOriginalUrl(rs.getString("original_url"));
        candidate.setOriginalSummary(rs.getString("original_summary"));
        candidate.setOriginalBody(rs.getString("original_body"));
        candidate.setContentType(rs.getString("content_type"));
        candidate.setExtractionMethod(rs.getString("extraction_method"));
        candidate.setExtractionQualityScore(rs.getInt("extraction_quality_score"));
        candidate.setPublishedAt(TimeUtil.localDateTime(rs, "published_at"));
        candidate.setFetchedAt(TimeUtil.localDateTime(rs, "fetched_at"));
        candidate.setChineseTitle(rs.getString("chinese_title"));
        candidate.setDecisionSummary(rs.getString("decision_summary"));
        candidate.setKeyFactsJson(rs.getString("key_facts_json"));
        candidate.setWhyItMatters(rs.getString("why_it_matters"));
        candidate.setNoveltyJudgment(rs.getString("novelty_judgment"));
        candidate.setRiskFlagsJson(rs.getString("risk_flags_json"));
        candidate.setAgentScore(rs.getInt("agent_score"));
        candidate.setAgentRecommendation(rs.getString("agent_recommendation"));
        candidate.setAgentReason(rs.getString("agent_reason"));
        candidate.setAgentModel(rs.getString("agent_model"));
        candidate.setAgentStatus(rs.getString("agent_status"));
        candidate.setAgentErrorMessage(rs.getString("agent_error_message"));
        candidate.setAgentReviewJson(rs.getString("agent_review_json"));
        candidate.setHumanStatus(rs.getString("human_status"));
        candidate.setHumanNote(rs.getString("human_note"));
        candidate.setPromotedArticleId(readLong(rs, "promoted_article_id"));
        candidate.setPromotedAt(TimeUtil.localDateTime(rs, "promoted_at"));
        candidate.setDuplicateOfCandidateId(readLong(rs, "duplicate_of_candidate_id"));
        candidate.setDuplicateOfArticleId(readLong(rs, "duplicate_of_article_id"));
        candidate.setUrlFingerprint(rs.getString("url_fingerprint"));
        candidate.setTitleFingerprint(rs.getString("title_fingerprint"));
        candidate.setBodyFingerprint(rs.getString("body_fingerprint"));
        candidate.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        candidate.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return candidate;
    };

    public IntakeCandidate save(IntakeCandidate candidate) {
        LocalDateTime now = LocalDateTime.now();
        candidate.setCreatedAt(now);
        candidate.setUpdatedAt(now);
        if (candidate.getFetchedAt() == null) {
            candidate.setFetchedAt(now);
        }
        if (isBlank(candidate.getAgentStatus())) {
            candidate.setAgentStatus(IntakeEnums.AGENT_PENDING);
        }
        if (isBlank(candidate.getHumanStatus())) {
            candidate.setHumanStatus(IntakeEnums.HUMAN_PENDING);
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO intake_candidate(batch_id,source_id,source_name,source_type,original_title,"
                            + "original_url,original_summary,original_body,content_type,extraction_method,"
                            + "extraction_quality_score,published_at,fetched_at,chinese_title,decision_summary,"
                            + "key_facts_json,why_it_matters,novelty_judgment,risk_flags_json,agent_score,"
                            + "agent_recommendation,agent_reason,agent_model,agent_status,agent_error_message,"
                            + "agent_review_json,human_status,human_note,promoted_article_id,promoted_at,"
                            + "duplicate_of_candidate_id,duplicate_of_article_id,url_fingerprint,title_fingerprint,"
                            + "body_fingerprint,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            bind(ps, candidate);
            return ps;
        }, keyHolder);
        candidate.setId(keyHolder.getKey().longValue());
        return findById(candidate.getId()).orElse(candidate);
    }

    public Optional<IntakeCandidate> findById(Long id) {
        List<IntakeCandidate> candidates = jdbcTemplate.query("SELECT * FROM intake_candidate WHERE id = ?", mapper, id);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }

    public List<IntakeCandidate> findByBatchId(Long batchId) {
        return jdbcTemplate.query("SELECT * FROM intake_candidate WHERE batch_id = ? ORDER BY agent_score DESC, id ASC",
                mapper, batchId);
    }

    public List<IntakeCandidate> findByStatus(String humanStatus, Long batchId, Long sourceId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM intake_candidate WHERE human_status = ?");
        List<Object> params = new ArrayList<Object>();
        params.add(humanStatus);
        if (batchId != null) {
            sql.append(" AND batch_id = ?");
            params.add(batchId);
        }
        if (sourceId != null) {
            sql.append(" AND source_id = ?");
            params.add(sourceId);
        }
        sql.append(" ORDER BY agent_score DESC, id ASC");
        return jdbcTemplate.query(sql.toString(), mapper, params.toArray());
    }

    public List<IntakeCandidate> findRecent(int limit) {
        return jdbcTemplate.query("SELECT * FROM intake_candidate ORDER BY id DESC LIMIT ?", mapper, limit);
    }

    public Optional<IntakeCandidate> findByUrlFingerprint(String urlFingerprint) {
        List<IntakeCandidate> candidates = jdbcTemplate.query(
                "SELECT * FROM intake_candidate WHERE url_fingerprint = ? ORDER BY id DESC LIMIT 1",
                mapper, urlFingerprint);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }

    public void updateHumanStatus(Long id, String humanStatus, String humanNote) {
        jdbcTemplate.update("UPDATE intake_candidate SET human_status=?, human_note=?, updated_at=? WHERE id=?",
                humanStatus, humanNote, TimeUtil.text(LocalDateTime.now()), id);
    }

    public void markPromoted(Long id, Long articleId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("UPDATE intake_candidate SET human_status=?, promoted_article_id=?, promoted_at=?, updated_at=? WHERE id=?",
                IntakeEnums.HUMAN_PROMOTED, articleId, TimeUtil.text(now), TimeUtil.text(now), id);
    }

    public void updateReview(IntakeCandidate candidate) {
        jdbcTemplate.update("UPDATE intake_candidate SET chinese_title=?, decision_summary=?, key_facts_json=?, "
                        + "why_it_matters=?, novelty_judgment=?, risk_flags_json=?, agent_score=?, "
                        + "agent_recommendation=?, agent_reason=?, agent_model=?, agent_status=?, "
                        + "agent_error_message=?, agent_review_json=?, updated_at=? WHERE id=?",
                candidate.getChineseTitle(), candidate.getDecisionSummary(), candidate.getKeyFactsJson(),
                candidate.getWhyItMatters(), candidate.getNoveltyJudgment(), candidate.getRiskFlagsJson(),
                candidate.getAgentScore(), candidate.getAgentRecommendation(), candidate.getAgentReason(),
                candidate.getAgentModel(), candidate.getAgentStatus(), candidate.getAgentErrorMessage(),
                candidate.getAgentReviewJson(), TimeUtil.text(LocalDateTime.now()), candidate.getId());
    }

    private void bind(PreparedStatement ps, IntakeCandidate candidate) throws java.sql.SQLException {
        ps.setLong(1, candidate.getBatchId());
        setLong(ps, 2, candidate.getSourceId());
        ps.setString(3, candidate.getSourceName());
        ps.setString(4, candidate.getSourceType());
        ps.setString(5, candidate.getOriginalTitle());
        ps.setString(6, candidate.getOriginalUrl());
        ps.setString(7, candidate.getOriginalSummary());
        ps.setString(8, candidate.getOriginalBody());
        ps.setString(9, candidate.getContentType());
        ps.setString(10, candidate.getExtractionMethod());
        ps.setInt(11, candidate.getExtractionQualityScore());
        ps.setString(12, TimeUtil.text(candidate.getPublishedAt()));
        ps.setString(13, TimeUtil.text(candidate.getFetchedAt()));
        ps.setString(14, candidate.getChineseTitle());
        ps.setString(15, candidate.getDecisionSummary());
        ps.setString(16, candidate.getKeyFactsJson());
        ps.setString(17, candidate.getWhyItMatters());
        ps.setString(18, candidate.getNoveltyJudgment());
        ps.setString(19, candidate.getRiskFlagsJson());
        ps.setInt(20, candidate.getAgentScore());
        ps.setString(21, candidate.getAgentRecommendation());
        ps.setString(22, candidate.getAgentReason());
        ps.setString(23, candidate.getAgentModel());
        ps.setString(24, candidate.getAgentStatus());
        ps.setString(25, candidate.getAgentErrorMessage());
        ps.setString(26, candidate.getAgentReviewJson());
        ps.setString(27, candidate.getHumanStatus());
        ps.setString(28, candidate.getHumanNote());
        setLong(ps, 29, candidate.getPromotedArticleId());
        ps.setString(30, TimeUtil.text(candidate.getPromotedAt()));
        setLong(ps, 31, candidate.getDuplicateOfCandidateId());
        setLong(ps, 32, candidate.getDuplicateOfArticleId());
        ps.setString(33, candidate.getUrlFingerprint());
        ps.setString(34, candidate.getTitleFingerprint());
        ps.setString(35, candidate.getBodyFingerprint());
        ps.setString(36, TimeUtil.text(candidate.getCreatedAt()));
        ps.setString(37, TimeUtil.text(candidate.getUpdatedAt()));
    }

    private void setLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }

    private Long readLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
