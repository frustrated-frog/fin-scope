package com.finscope.dao.investmentobservation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.investmentobservation.InvestmentObservationChangeType;
import com.finscope.common.enums.investmentobservation.InvestmentObservationDisposition;
import com.finscope.common.enums.investmentobservation.InvestmentObservationSourceType;
import com.finscope.common.enums.investmentobservation.InvestmentObservationStage;
import com.finscope.common.enums.investmentobservation.InvestmentObservationSubjectType;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.investmentobservation.InvestmentObservation;
import com.finscope.domain.investmentobservation.InvestmentObservationScoreDimension;
import com.finscope.domain.investmentobservation.InvestmentObservationTransition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class InvestmentObservationRepository {
    private static final TypeReference<List<InvestmentObservationScoreDimension>> DIMENSIONS =
            new TypeReference<List<InvestmentObservationScoreDimension>>() { };

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ObjectMapper objectMapper;

    @Transactional
    public InvestmentObservation upsertGenerated(InvestmentObservation value, LocalDateTime now) {
        Optional<InvestmentObservation> existing = findBySource(value.getSourceType(), value.getSourceId());
        if (!existing.isPresent()) {
            insert(value, now);
            InvestmentObservation saved = findBySource(value.getSourceType(), value.getSourceId())
                    .orElseThrow(() -> new IllegalStateException("投资观察写入后不可读取"));
            saveTransition(saved.getId(), null, saved.getStage(), "自动进入研究观察池",
                    saved.getLastSourceFingerprint(), now);
            return saved;
        }
        InvestmentObservation current = existing.get();
        InvestmentObservationStage nextStage = current.getStage() == InvestmentObservationStage.ARCHIVED
                ? InvestmentObservationStage.ARCHIVED : value.getStage();
        boolean changed = !safe(current.getLastSourceFingerprint()).equals(safe(value.getLastSourceFingerprint()));
        LocalDateTime lastChangedAt = changed ? now : current.getLastChangedAt();
        jdbcTemplate.update("UPDATE investment_observation SET title=?,summary=?,subject_type=?,subject_name=?,stage=?,"
                        + "change_type=?,score=?,score_dimensions_json=?,why_it_matters=?,uncertainty=?,next_validation=?,"
                        + "supporting_evidence_count=?,opposing_evidence_count=?,independent_source_count=?,last_changed_at=?,"
                        + "last_source_fingerprint=?,evidence_insufficient=?,revision=revision+1,updated_at=? WHERE id=?",
                value.getTitle(), value.getSummary(), value.getSubjectType().name(), value.getSubjectName(), nextStage.name(),
                value.getChangeType().name(), value.getScore(), writeDimensions(value.getScoreDimensions()),
                value.getWhyItMatters(), value.getUncertainty(), value.getNextValidation(),
                value.getSupportingEvidenceCount(), value.getOpposingEvidenceCount(), value.getIndependentSourceCount(),
                TimeUtil.text(lastChangedAt), value.getLastSourceFingerprint(), value.isEvidenceInsufficient() ? 1 : 0,
                TimeUtil.text(now), current.getId());
        if (nextStage != current.getStage()) {
            saveTransition(current.getId(), current.getStage(), nextStage, "观察证据等级发生变化",
                    value.getLastSourceFingerprint(), now);
        }
        return findById(current.getId()).orElseThrow(() -> new IllegalStateException("投资观察更新后不可读取"));
    }

    public Optional<InvestmentObservation> findById(Long id) {
        List<InvestmentObservation> values = jdbcTemplate.query("SELECT * FROM investment_observation WHERE id=?",
                observationMapper(), id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public Optional<InvestmentObservation> findBySource(InvestmentObservationSourceType sourceType, Long sourceId) {
        List<InvestmentObservation> values = jdbcTemplate.query(
                "SELECT * FROM investment_observation WHERE source_type=? AND source_id=?", observationMapper(),
                sourceType.name(), sourceId);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<InvestmentObservation> findAll() {
        return jdbcTemplate.query("SELECT * FROM investment_observation ORDER BY "
                + "CASE stage WHEN 'FOCUS' THEN 0 WHEN 'TRACKING' THEN 1 WHEN 'LEARNING' THEN 2 ELSE 3 END,"
                + "score DESC,last_changed_at DESC,id DESC", observationMapper());
    }

    public List<InvestmentObservationTransition> findTransitions(Long observationId) {
        return jdbcTemplate.query("SELECT * FROM investment_observation_transition WHERE observation_id=? "
                + "ORDER BY occurred_at,id", transitionMapper(), observationId);
    }

    public List<InvestmentObservationTransition> findRecentTransitions(int limit) {
        return jdbcTemplate.query("SELECT * FROM investment_observation_transition ORDER BY occurred_at DESC,id DESC LIMIT ?",
                transitionMapper(), Math.max(1, Math.min(limit, 100)));
    }

    public boolean updateDisposition(Long id, InvestmentObservationDisposition disposition, int revision,
                                     LocalDateTime now) {
        return jdbcTemplate.update("UPDATE investment_observation SET user_disposition=?,revision=revision+1,updated_at=? "
                        + "WHERE id=? AND revision=?", disposition.name(), TimeUtil.text(now), id, revision) == 1;
    }

    @Transactional
    public boolean archive(Long id, int revision, String reason, LocalDateTime now) {
        Optional<InvestmentObservation> existing = findById(id);
        if (!existing.isPresent()) {
            return false;
        }
        InvestmentObservation current = existing.get();
        int updated = jdbcTemplate.update("UPDATE investment_observation SET stage='ARCHIVED',revision=revision+1,"
                + "updated_at=? WHERE id=? AND revision=?", TimeUtil.text(now), id, revision);
        if (updated == 1 && current.getStage() != InvestmentObservationStage.ARCHIVED) {
            saveTransition(id, current.getStage(), InvestmentObservationStage.ARCHIVED, reason,
                    current.getLastSourceFingerprint(), now);
        }
        return updated == 1;
    }

    private void insert(InvestmentObservation value, LocalDateTime now) {
        InvestmentObservationDisposition disposition = value.getDisposition() == null
                ? InvestmentObservationDisposition.ACTIVE : value.getDisposition();
        jdbcTemplate.update("INSERT INTO investment_observation(source_type,source_id,title,summary,subject_type,subject_name,"
                        + "stage,change_type,score,score_dimensions_json,why_it_matters,uncertainty,next_validation,"
                        + "supporting_evidence_count,opposing_evidence_count,independent_source_count,first_observed_at,"
                        + "last_changed_at,last_source_fingerprint,user_disposition,evidence_insufficient,revision,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?)",
                value.getSourceType().name(), value.getSourceId(), value.getTitle(), value.getSummary(),
                value.getSubjectType().name(), value.getSubjectName(), value.getStage().name(), value.getChangeType().name(),
                value.getScore(), writeDimensions(value.getScoreDimensions()), value.getWhyItMatters(), value.getUncertainty(),
                value.getNextValidation(), value.getSupportingEvidenceCount(), value.getOpposingEvidenceCount(),
                value.getIndependentSourceCount(), TimeUtil.text(now), TimeUtil.text(now), value.getLastSourceFingerprint(),
                disposition.name(), value.isEvidenceInsufficient() ? 1 : 0, TimeUtil.text(now), TimeUtil.text(now));
    }

    private void saveTransition(Long observationId, InvestmentObservationStage fromStage,
                                InvestmentObservationStage toStage, String reason, String fingerprint,
                                LocalDateTime now) {
        jdbcTemplate.update("INSERT INTO investment_observation_transition(observation_id,from_stage,to_stage,reason,"
                        + "source_fingerprint,occurred_at) VALUES(?,?,?,?,?,?)", observationId,
                fromStage == null ? null : fromStage.name(), toStage.name(), reason, fingerprint, TimeUtil.text(now));
    }

    private RowMapper<InvestmentObservation> observationMapper() {
        return (rs, rowNum) -> {
            InvestmentObservation value = new InvestmentObservation();
            value.setId(rs.getLong("id"));
            value.setSourceType(InvestmentObservationSourceType.valueOf(rs.getString("source_type")));
            value.setSourceId(rs.getLong("source_id"));
            value.setTitle(rs.getString("title"));
            value.setSummary(rs.getString("summary"));
            value.setSubjectType(InvestmentObservationSubjectType.valueOf(rs.getString("subject_type")));
            value.setSubjectName(rs.getString("subject_name"));
            value.setStage(InvestmentObservationStage.valueOf(rs.getString("stage")));
            value.setChangeType(InvestmentObservationChangeType.valueOf(rs.getString("change_type")));
            value.setScore(rs.getInt("score"));
            value.setScoreDimensions(readDimensions(rs.getString("score_dimensions_json")));
            value.setWhyItMatters(rs.getString("why_it_matters"));
            value.setUncertainty(rs.getString("uncertainty"));
            value.setNextValidation(rs.getString("next_validation"));
            value.setSupportingEvidenceCount(rs.getInt("supporting_evidence_count"));
            value.setOpposingEvidenceCount(rs.getInt("opposing_evidence_count"));
            value.setIndependentSourceCount(rs.getInt("independent_source_count"));
            value.setFirstObservedAt(TimeUtil.localDateTime(rs, "first_observed_at"));
            value.setLastChangedAt(TimeUtil.localDateTime(rs, "last_changed_at"));
            value.setLastSourceFingerprint(rs.getString("last_source_fingerprint"));
            value.setDisposition(InvestmentObservationDisposition.valueOf(rs.getString("user_disposition")));
            value.setEvidenceInsufficient(rs.getInt("evidence_insufficient") == 1);
            value.setRevision(rs.getInt("revision"));
            value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
            value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
            return value;
        };
    }

    private RowMapper<InvestmentObservationTransition> transitionMapper() {
        return (rs, rowNum) -> {
            InvestmentObservationTransition value = new InvestmentObservationTransition();
            value.setId(rs.getLong("id"));
            value.setObservationId(rs.getLong("observation_id"));
            String fromStage = rs.getString("from_stage");
            value.setFromStage(fromStage == null ? null : InvestmentObservationStage.valueOf(fromStage));
            value.setToStage(InvestmentObservationStage.valueOf(rs.getString("to_stage")));
            value.setReason(rs.getString("reason"));
            value.setSourceFingerprint(rs.getString("source_fingerprint"));
            value.setOccurredAt(TimeUtil.localDateTime(rs, "occurred_at"));
            return value;
        };
    }

    private String writeDimensions(List<InvestmentObservationScoreDimension> dimensions) {
        try {
            return objectMapper.writeValueAsString(dimensions);
        } catch (Exception error) {
            throw new IllegalStateException("投资观察评分序列化失败", error);
        }
    }

    private List<InvestmentObservationScoreDimension> readDimensions(String json) {
        try {
            return objectMapper.readValue(json, DIMENSIONS);
        } catch (Exception error) {
            throw new IllegalStateException("投资观察评分读取失败", error);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
