package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.ThesisFinding;
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
public class ResearchThesisRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    public ResearchThesisRepository() {
    }

    ResearchThesisRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ResearchThesis> thesisMapper = (rs, rowNum) -> {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setId(rs.getLong("id"));
        thesis.setQuestion(rs.getString("question"));
        thesis.setSubjectType(rs.getString("subject_type"));
        thesis.setSubjectName(rs.getString("subject_name"));
        thesis.setSubjectCode(rs.getString("subject_code"));
        thesis.setStatus(rs.getString("status"));
        thesis.setConclusion(rs.getString("conclusion"));
        thesis.setConfidence(rs.getString("confidence"));
        thesis.setNextValidation(rs.getString("next_validation"));
        thesis.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        thesis.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return thesis;
    };

    private final RowMapper<ThesisFinding> findingMapper = (rs, rowNum) -> {
        ThesisFinding finding = new ThesisFinding();
        finding.setId(rs.getLong("id"));
        finding.setThesisId(rs.getLong("thesis_id"));
        finding.setStance(rs.getString("stance"));
        finding.setSummary(rs.getString("summary"));
        long evidenceId = rs.getLong("evidence_id");
        finding.setEvidenceId(rs.wasNull() ? null : evidenceId);
        finding.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        finding.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return finding;
    };

    public ResearchThesis save(ResearchThesis thesis) {
        LocalDateTime now = LocalDateTime.now();
        thesis.setCreatedAt(now);
        thesis.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO research_thesis(question,subject_type,subject_name,subject_code,status,conclusion,"
                            + "confidence,next_validation,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, thesis.getQuestion());
            ps.setString(2, thesis.getSubjectType());
            ps.setString(3, thesis.getSubjectName());
            ps.setString(4, thesis.getSubjectCode());
            ps.setString(5, thesis.getStatus());
            ps.setString(6, thesis.getConclusion());
            ps.setString(7, thesis.getConfidence());
            ps.setString(8, thesis.getNextValidation());
            ps.setString(9, TimeUtil.text(thesis.getCreatedAt()));
            ps.setString(10, TimeUtil.text(thesis.getUpdatedAt()));
            return ps;
        }, keyHolder);
        thesis.setId(keyHolder.getKey().longValue());
        return thesis;
    }

    public ResearchThesis update(ResearchThesis thesis) {
        thesis.setUpdatedAt(LocalDateTime.now());
        jdbcTemplate.update("UPDATE research_thesis SET question=?, subject_type=?, subject_name=?, subject_code=?, "
                        + "status=?, conclusion=?, confidence=?, next_validation=?, updated_at=? WHERE id=?",
                thesis.getQuestion(), thesis.getSubjectType(), thesis.getSubjectName(), thesis.getSubjectCode(),
                thesis.getStatus(), thesis.getConclusion(), thesis.getConfidence(), thesis.getNextValidation(),
                TimeUtil.text(thesis.getUpdatedAt()), thesis.getId());
        return findById(thesis.getId())
                .orElseThrow(() -> new IllegalArgumentException("Research thesis not found: " + thesis.getId()));
    }

    public List<ResearchThesis> findAll() {
        return jdbcTemplate.query("SELECT * FROM research_thesis ORDER BY updated_at DESC, id DESC", thesisMapper);
    }

    public Optional<ResearchThesis> findById(Long id) {
        List<ResearchThesis> theses = jdbcTemplate.query("SELECT * FROM research_thesis WHERE id = ?", thesisMapper, id);
        return theses.isEmpty() ? Optional.<ResearchThesis>empty() : Optional.of(theses.get(0));
    }

    public ThesisFinding saveFinding(ThesisFinding finding) {
        LocalDateTime now = LocalDateTime.now();
        finding.setCreatedAt(now);
        finding.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO thesis_finding(thesis_id,stance,summary,evidence_id,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, finding.getThesisId());
            ps.setString(2, finding.getStance());
            ps.setString(3, finding.getSummary());
            if (finding.getEvidenceId() == null) {
                ps.setObject(4, null);
            } else {
                ps.setLong(4, finding.getEvidenceId());
            }
            ps.setString(5, TimeUtil.text(finding.getCreatedAt()));
            ps.setString(6, TimeUtil.text(finding.getUpdatedAt()));
            return ps;
        }, keyHolder);
        finding.setId(keyHolder.getKey().longValue());
        return finding;
    }

    public List<ThesisFinding> findFindingsByThesisId(Long thesisId) {
        return jdbcTemplate.query("SELECT * FROM thesis_finding WHERE thesis_id = ? ORDER BY created_at ASC, id ASC",
                findingMapper, thesisId);
    }
}
