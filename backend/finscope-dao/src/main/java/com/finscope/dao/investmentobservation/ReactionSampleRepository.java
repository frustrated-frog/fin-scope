package com.finscope.dao.investmentobservation;

import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.time.LocalDate;
import com.finscope.domain.investmentobservation.ReactionWindow;
import com.finscope.domain.investmentobservation.ReactionSource;
import com.finscope.domain.investmentobservation.ReactionPoint;
import com.finscope.domain.investmentobservation.ReactionChange;
import com.finscope.common.enums.investmentobservation.ReactionChangeType;
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
                        + "state,snapshot_json,registered_at,followed) VALUES(?,?,?,?,?,?,?) "
                        + "ON CONFLICT(source_identity,instrument_code) DO NOTHING",
                sample.getMajorEventId(), sample.getSourceIdentity(), sample.getInstrumentCode(), sample.getState().name(),
                write(sample), TimeUtil.text(sample.getRegisteredAt()), sample.isFollowed() ? 1 : 0);
        if (changed != 0 && changed != 1) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR);
        }
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE source_identity=? AND instrument_code=?",
                mapper, sample.getSourceIdentity(), sample.getInstrumentCode()).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean captureSource(ReactionSample proposed) {
        String origin = proposed.getSourceOriginType();
        String key = proposed.getSourceOriginKey();
        // 兼容旧版 NEWS 身份：新渠道的同日完整标题优先挂到已有事件，保留原 ID。
        if (proposed.getPublishedAt() != null) {
            List<String> existingKeys = jdbcTemplate.queryForList("SELECT source_identity FROM investment_reaction_sample "
                            + "WHERE replace(json_extract(snapshot_json,'$.title'),' ','')=? "
                            + "AND substr(json_extract(snapshot_json,'$.publishedAt'),1,10)=? ORDER BY id LIMIT 1",
                    String.class, proposed.getTitle().replaceAll("\\s", ""), proposed.getPublishedAt().toLocalDate().toString());
            if (!existingKeys.isEmpty()) {
                proposed.setSourceIdentity(existingKeys.get(0));
            }
        }
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

    public List<ReactionSource> sources(String eventKey) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_source WHERE event_key=? ORDER BY captured_at LIMIT 100", (rs, n) -> {
            var source = new ReactionSource();
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

    @Transactional(rollbackFor = Exception.class)
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

    @Transactional(rollbackFor = Exception.class)
    public boolean saveCalculation(long id, int revision, ReactionCalculation calculation, LocalDateTime attemptedAt) {
        ReactionSample previous = findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean complete = calculation.getPoints().size() == 11 && calculation.getPoints().stream()
                .allMatch(point -> point.getStatus() == ReactionWindowStatus.READY);
        LocalDate end = calculation.getWindows().stream().filter(window -> window.getSessions() == 5)
                .map(ReactionWindow::getEndDate).findFirst().orElse(null);
        boolean ended = end != null && !end.atTime(15, 0).isAfter(attemptedAt);
        boolean stop = ended && (complete || !attemptedAt.toLocalDate().isBefore(end.plusDays(7)));
        boolean updated = jdbcTemplate.update("UPDATE investment_reaction_sample SET calculation_json=?,last_attempt_at=?,"
                        + "refresh_error=NULL,completed=?,next_attempt_at=?,revision=revision+1 WHERE id=? AND revision=? AND state=?",
                write(calculation), TimeUtil.text(attemptedAt), stop ? 1 : 0,
                ended && !complete ? TimeUtil.text(attemptedAt.plusDays(1)) : null,
                id, revision, ReactionSampleState.OBSERVING.name()) == 1;
        if (updated) {
            recordChange(previous, calculation, attemptedAt, revision + 1);
        }
        return updated;
    }

    private void recordChange(ReactionSample previous, ReactionCalculation next, LocalDateTime now, int revision) {
        ReactionCalculation old = previous.getCalculation();
        var last = next.getPoints().stream().filter(point -> point.getSession() > 0 && point.getStatus() == ReactionWindowStatus.READY)
                .max(Comparator.comparingInt(ReactionPoint::getSession));
        if (last.isEmpty() || old != null && write(old.getPoints()).equals(write(next.getPoints()))) {
            return;
        }
        int oldSession = old == null ? 0 : old.getPoints().stream()
                .filter(point -> point.getSession() > 0 && point.getStatus() == ReactionWindowStatus.READY)
                .mapToInt(ReactionPoint::getSession).max().orElse(0);
        var type = ReactionChangeType.NEW_SESSION;
        if (oldSession >= last.get().getSession()) {
            type = ReactionChangeType.DATA_CORRECTION;
        } else if (last.get().getSession() == 5) {
            type = ReactionChangeType.WINDOW_COMPLETED;
        } else if (oldSession == 0) {
            type = ReactionChangeType.FIRST_REACTION;
        } else if (old != null && old.getPathType() != next.getPathType()) {
            type = ReactionChangeType.PATH_CHANGED;
        }
        String summary = next.getProfile() == null ? "新增第" + last.get().getSession() + "个交易日行情" : next.getProfile().getSummary();
        if (type == ReactionChangeType.DATA_CORRECTION) {
            summary = "已记录行情发生修正；" + summary;
        }
        jdbcTemplate.update("INSERT INTO investment_reaction_change(sample_id,revision,event_key,change_type,trade_date,detected_at,summary,snapshot_json) "
                        + "VALUES(?,?,?,?,?,?,?,?)", previous.getId(), revision, previous.getSourceIdentity(), type.name(),
                last.get().getTradeDate().toString(), now.toString(), summary, write(next));
    }

    public List<ReactionChange> changes(LocalDate date, long beforeId, int limit) {
        return jdbcTemplate.query("SELECT c.*,s.followed,s.snapshot_json AS sample_json FROM investment_reaction_change c "
                        + "JOIN investment_reaction_sample s ON s.id=c.sample_id WHERE c.detected_at>=? AND c.detected_at<? "
                        + "AND c.id<? AND s.state<>'ARCHIVED' ORDER BY c.id DESC LIMIT ?", (rs, n) -> {
            var change = new ReactionChange();
            var sample = read(rs.getString("sample_json"), ReactionSample.class);
            change.setId(rs.getLong("id"));
            change.setSampleId(rs.getLong("sample_id"));
            change.setEventKey(rs.getString("event_key"));
            change.setTitle(sample.getTitle());
            change.setInstrumentName(sample.getInstrumentName());
            change.setChangeType(ReactionChangeType.valueOf(rs.getString("change_type")));
            change.setTradeDate(LocalDate.parse(rs.getString("trade_date")));
            change.setDetectedAt(TimeUtil.localDateTime(rs, "detected_at"));
            change.setSummary(rs.getString("summary"));
            change.setFollowed(rs.getInt("followed") == 1);
            return change;
        }, date.atStartOfDay().toString(), date.plusDays(1).atStartOfDay().toString(), beforeId, Math.max(1, Math.min(100, limit)));
    }

    public List<ReactionSample> followed(long beforeId, int limit) {
        return jdbcTemplate.query("SELECT * FROM investment_reaction_sample WHERE followed=1 AND state<>'ARCHIVED' "
                + "AND id<? ORDER BY id DESC LIMIT ?", mapper, beforeId, Math.max(1, Math.min(100, limit)));
    }

    public boolean saveFailure(long id, int revision, String message, LocalDateTime attemptedAt) {
        ReactionSample sample = findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        LocalDate end = sample.getCalculation() == null ? null : sample.getCalculation().getWindows().stream()
                .filter(window -> window.getSessions() == 5).map(ReactionWindow::getEndDate).findFirst().orElse(null);
        boolean pastWindow = end != null && !attemptedAt.toLocalDate().isBefore(end);
        boolean stop = pastWindow && !attemptedAt.toLocalDate().isBefore(end.plusDays(7));
        if (end == null && sample.getPublishedAt() != null && !attemptedAt.isBefore(sample.getPublishedAt().plusDays(28))) {
            stop = true;
        }
        return jdbcTemplate.update("UPDATE investment_reaction_sample SET last_attempt_at=?,refresh_error=?,revision=revision+1,"
                        + "completed=?,next_attempt_at=? WHERE id=? AND revision=? AND state=?", TimeUtil.text(attemptedAt), message,
                stop ? 1 : 0, pastWindow ? TimeUtil.text(attemptedAt.plusDays(1)) : null,
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
