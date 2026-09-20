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
        int changed = jdbcTemplate.update("INSERT INTO investment_reaction_sample(major_event_id,instrument_code,"
                        + "state,snapshot_json,registered_at) VALUES(?,?,?,?,?) "
                        + "ON CONFLICT(major_event_id,instrument_code) DO NOTHING",
                sample.getMajorEventId(), sample.getInstrumentCode(), sample.getState().name(),
                write(sample), TimeUtil.text(sample.getRegisteredAt()));
        if (changed != 0 && changed != 1) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR);
        }
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE major_event_id=? AND instrument_code=?",
                mapper, sample.getMajorEventId(), sample.getInstrumentCode()).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR));
    }

    public Optional<ReactionSample> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE id=?", mapper, id)
                .stream().findFirst();
    }

    public Optional<ReactionSample> findBySource(long majorEventId, String instrumentCode) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE major_event_id=? AND instrument_code=?",
                mapper, majorEventId, instrumentCode).stream().findFirst();
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
                        + "WHERE existing.major_event_id=? AND existing.instrument_code=? AND existing.id<>?)",
                sample.getInstrumentCode(), ReactionSampleState.OBSERVING.name(), write(sample), sample.getId(),
                revision, ReactionSampleState.DRAFT.name(), sample.getMajorEventId(), sample.getInstrumentCode(), sample.getId()) == 1;
    }

    public List<ReactionSample> findDue(LocalDateTime before, int limit) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE state=? AND completed=0 "
                        + "AND (last_attempt_at IS NULL OR last_attempt_at<?) ORDER BY last_attempt_at,id LIMIT ?",
                mapper, ReactionSampleState.OBSERVING.name(), TimeUtil.text(before), Math.max(1, Math.min(20, limit)));
    }

    public boolean changeState(long id, int revision, ReactionSampleState state) {
        return jdbcTemplate.update("UPDATE investment_reaction_sample SET state=?,revision=revision+1 WHERE id=? AND revision=?",
                state.name(), id, revision) == 1;
    }

    public boolean saveCalculation(long id, int revision, ReactionCalculation calculation, LocalDateTime attemptedAt) {
        return jdbcTemplate.update("UPDATE investment_reaction_sample SET calculation_json=?,last_attempt_at=?,"
                        + "refresh_error=NULL,completed=?,revision=revision+1 WHERE id=? AND revision=? AND state=?",
                write(calculation), TimeUtil.text(attemptedAt), calculation.getWindows().stream()
                        .anyMatch(window -> window.getSessions() == 5 && window.getStatus() == ReactionWindowStatus.READY) ? 1 : 0,
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
