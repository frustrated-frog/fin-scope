package com.finscope.dao.investmentobservation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.common.enums.investmentobservation.ReactionWindowStatus;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.investmentobservation.ReactionCalculation;
import com.finscope.domain.investmentobservation.ReactionSample;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ReactionSampleRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ObjectMapper objectMapper;

    private final RowMapper<ReactionSample> mapper = (rs, rowNum) -> {
        ReactionSample sample = read(rs.getString("snapshot_json"), ReactionSample.class);
        sample.setId(rs.getLong("id"));
        sample.setFollowed(rs.getInt("followed") == 1);
        sample.setSourceIdentity(rs.getString("source_identity"));
        sample.setInstrumentCode(rs.getString("instrument_code"));
        sample.setState(ReactionSampleState.valueOf(rs.getString("state")));
        sample.setRevision(rs.getInt("revision"));
        sample.setLastAttemptAt(TimeUtil.localDateTime(rs, "last_attempt_at"));
        sample.setRefreshError(rs.getString("refresh_error"));
        String calculation = rs.getString("calculation_json");
        sample.setCalculation(calculation == null ? null : read(calculation, ReactionCalculation.class));
        return sample;
    };

    /** 数据库唯一键仲裁重试，不覆盖原始事件快照或用户确认。 */
    public ReactionSample create(ReactionSample sample) {
        if (sample.getSourceIdentity() == null) {
            sample.setSourceIdentity("MAJOR_EVENT:" + sample.getMajorEventId());
        }
        int changed = jdbcTemplate.update("INSERT INTO investment_reaction_sample(major_event_id,source_identity,instrument_code,"
                        + "state,snapshot_json,registered_at) VALUES(?,?,?,?,?,?) "
                        + "ON CONFLICT(source_identity,instrument_code) DO NOTHING",
                sample.getMajorEventId(), sample.getSourceIdentity(), sample.getInstrumentCode(), sample.getState().name(),
                write(sample), TimeUtil.text(sample.getRegisteredAt()));
        if (changed != 0 && changed != 1) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR);
        }
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE source_identity=? AND instrument_code=?",
                mapper, sample.getSourceIdentity(), sample.getInstrumentCode()).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR));
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public boolean captureSource(ReactionSample proposed) {
        String origin = proposed.getSourceOriginType();
        String key = proposed.getSourceOriginKey();
        jdbcTemplate.update("INSERT INTO investment_reaction_source(origin_type,origin_key,event_key,title,url,published_at,captured_at) "
                        + "VALUES(?,?,?,?,?,?,?) ON CONFLICT(origin_type,origin_key) DO NOTHING", origin, key,
                proposed.getSourceIdentity(), proposed.getTitle(), proposed.getSourceUrl(), TimeUtil.text(proposed.getPublishedAt()),
                TimeUtil.text(proposed.getFirstCapturedAt()));
        String identity = jdbcTemplate.queryForObject("SELECT event_key FROM investment_reaction_source WHERE origin_type=? AND origin_key=?",
                String.class, origin, key);
        proposed.setSourceIdentity(identity);
        if (proposed.getPublishedAt() != null) {
            jdbcTemplate.update("UPDATE investment_reaction_source SET published_at=COALESCE(published_at,?) WHERE origin_type=? AND origin_key=?",
                    TimeUtil.text(proposed.getPublishedAt()), origin, key);
        }
        List<ReactionSample> existing = findByIdentity(identity);
        if (existing.isEmpty()) {
            create(proposed);
            return true;
        }
        for (ReactionSample draft : existing) {
            if (draft.getState() == ReactionSampleState.DRAFT && draft.getPublishedAt() == null && proposed.getPublishedAt() != null) {
                draft.setPublishedAt(proposed.getPublishedAt());
                draft.setOccurredDate(proposed.getOccurredDate());
                draft.setEnrichmentAttemptAt(null);
                draft.setHistoricalBackfill(proposed.getPublishedAt().toLocalDate().isBefore(draft.getRegisteredAt().toLocalDate()));
                if (!saveDraft(draft)) {
                    throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT);
                }
            }
        }
        return false;
    }

    public List<com.finscope.domain.investmentobservation.ReactionSource> sources(String eventKey) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_source WHERE event_key=? ORDER BY captured_at LIMIT 100", (rs, n) -> {
            var source = new com.finscope.domain.investmentobservation.ReactionSource();
            source.setOriginType(rs.getString("origin_type"));
            source.setOriginKey(rs.getString("origin_key"));
            source.setEventKey(rs.getString("event_key"));
            source.setTitle(rs.getString("title"));
            source.setUrl(rs.getString("url"));
            source.setPublishedAt(TimeUtil.localDateTime(rs, "published_at"));
            source.setCapturedAt(TimeUtil.localDateTime(rs, "captured_at"));
            return source;
        }, eventKey);
    }

    public boolean followEvent(String eventKey, boolean followed) {
        return jdbcTemplate.update("UPDATE investment_reaction_sample SET followed=? WHERE source_identity=?",
                followed ? 1 : 0, eventKey) > 0;
    }

    public Optional<ReactionSample> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE id=?", mapper, id)
                .stream().findFirst();
    }

    public Optional<ReactionSample> findBySource(long majorEventId, String instrumentCode) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE major_event_id=? AND instrument_code=?",
                mapper, majorEventId, instrumentCode).stream().findFirst();
    }

    public List<ReactionSample> findByIdentity(String identity) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE source_identity=? ORDER BY id",
                mapper, identity);
    }

    public Optional<ReactionSample> findUnresolvedOrigin(String origin, String key) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE state='DRAFT' "
                        + "AND json_extract(snapshot_json,'$.sourceOriginType')=? "
                        + "AND json_extract(snapshot_json,'$.sourceOriginKey')=? ORDER BY id LIMIT 1",
                mapper, origin, key).stream().findFirst();
    }

    public List<ReactionSample> findUnresolved(LocalDateTime before, int limit) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE state='DRAFT' "
                        + "AND json_extract(snapshot_json,'$.automatic')=1 "
                        + "AND (enrichment_attempt_at IS NULL OR enrichment_attempt_at<?) "
                        + "ORDER BY COALESCE(enrichment_attempt_at,''),id LIMIT ?",
                mapper, before.toString(), Math.max(1, Math.min(10, limit)));
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public boolean promoteDraft(ReactionSample draft, List<ReactionSample> samples) {
        // 条件写获取数据库写锁，归档或人工确认先完成时不再自动创建样本。
        if (!saveDraft(draft)) {
            return false;
        }
        for (ReactionSample sample : samples) {
            create(sample);
        }
        int removed = jdbcTemplate.update("DELETE FROM investment_reaction_sample WHERE id=? AND state='DRAFT'",
                draft.getId());
        if (removed != 1) {
            throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT);
        }
        return true;
    }

    public boolean saveDraft(ReactionSample sample) {
        return jdbcTemplate.update("UPDATE investment_reaction_sample SET snapshot_json=?,enrichment_attempt_at=?,revision=revision+1 "
                        + "WHERE id=? AND revision=? AND state='DRAFT'", write(sample), TimeUtil.text(sample.getEnrichmentAttemptAt()), sample.getId(), sample.getRevision()) == 1;
    }

    public List<ReactionSample> recent(long beforeId, int limit) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE id<? ORDER BY id DESC LIMIT ?",
                mapper, beforeId, Math.max(1, Math.min(100, limit)));
    }

    public List<ReactionSample> list(ReactionSampleState state, long afterId, int limit) {
        int bounded = Math.max(1, Math.min(100, limit));
        if (state == null) {
            return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE id>? ORDER BY id LIMIT ?",
                    mapper, afterId, bounded);
        }
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE state=? AND id>? ORDER BY id LIMIT ?",
                mapper, state.name(), afterId, bounded);
    }

    public boolean confirm(ReactionSample sample, int revision) {
        return jdbcTemplate.update("UPDATE investment_reaction_sample SET instrument_code=?,state=?,snapshot_json=?,"
                        + "revision=revision+1 WHERE id=? AND revision=? AND state=? "
                        + "AND NOT EXISTS (SELECT 1 FROM investment_reaction_sample existing "
                        + "WHERE existing.source_identity=? AND existing.instrument_code=? AND existing.id<>?)",
                sample.getInstrumentCode(), ReactionSampleState.OBSERVING.name(), write(sample), sample.getId(),
                revision, ReactionSampleState.DRAFT.name(), sample.getSourceIdentity(), sample.getInstrumentCode(), sample.getId()) == 1;
    }

    public List<ReactionSample> findDue(LocalDateTime before, int limit) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE state=? AND completed=0 "
                        + "AND (last_attempt_at IS NULL OR last_attempt_at<?) AND (next_attempt_at IS NULL OR next_attempt_at<?) "
                        + "ORDER BY last_attempt_at,id LIMIT ?",
                mapper, ReactionSampleState.OBSERVING.name(), TimeUtil.text(before), TimeUtil.text(before.plusMinutes(20)), Math.max(1, Math.min(20, limit)));
    }

    public boolean changeState(long id, int revision, ReactionSampleState state) {
        return jdbcTemplate.update("UPDATE investment_reaction_sample SET state=?,revision=revision+1 WHERE id=? AND revision=?",
                state.name(), id, revision) == 1;
    }

    public boolean saveCalculation(long id, int revision, ReactionCalculation calculation, LocalDateTime attemptedAt) {
        boolean complete = calculation.getPoints().size() == 11 && calculation.getPoints().stream()
                .allMatch(point -> point.getStatus() == ReactionWindowStatus.READY);
        java.time.LocalDate end = calculation.getWindows().stream().filter(window -> window.getSessions() == 5)
                .map(com.finscope.domain.investmentobservation.ReactionWindow::getEndDate).findFirst().orElse(null);
        boolean ended = end != null && !end.atTime(15, 0).isAfter(attemptedAt);
        boolean stop = ended && (complete || !attemptedAt.toLocalDate().isBefore(end.plusDays(7)));
        return jdbcTemplate.update("UPDATE investment_reaction_sample SET calculation_json=?,last_attempt_at=?,"
                        + "refresh_error=NULL,completed=?,next_attempt_at=?,revision=revision+1 WHERE id=? AND revision=? AND state=?",
                write(calculation), TimeUtil.text(attemptedAt), stop ? 1 : 0,
                ended && !complete ? TimeUtil.text(attemptedAt.plusDays(1)) : null,
                id, revision, ReactionSampleState.OBSERVING.name()) == 1;
    }

    public boolean saveFailure(long id, int revision, String message, LocalDateTime attemptedAt) {
        return jdbcTemplate.update("UPDATE investment_reaction_sample SET last_attempt_at=?,refresh_error=?,revision=revision+1 "
                        + "WHERE id=? AND revision=? AND state=?", TimeUtil.text(attemptedAt), message,
                id, revision, ReactionSampleState.OBSERVING.name()) == 1;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR, "观察样本序列化失败", ex);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR, "观察样本读取失败", ex);
        }
    }
}
