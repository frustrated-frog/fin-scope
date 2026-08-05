package com.finscope.dao.marketintel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.marketintel.CapitalBehaviorEvaluation;
import com.finscope.domain.marketintel.CapitalSignalEvaluation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class CapitalBehaviorEvaluationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CapitalBehaviorEvaluationRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public CapitalBehaviorEvaluation save(CapitalBehaviorEvaluation value) {
        try {
            String signalsJson = mapper.writeValueAsString(value.getSignals());
            String dataGapsJson = mapper.writeValueAsString(value.getDataGaps());
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO " +
                                "market_capital_behavior_evaluation(instrument_id,snapshot_id,as_of,data_from,data_to," +
                                "evaluation_version,factor_version,signal_version,input_fingerprint,status," +
                                "daily_sample_count,evaluable_event_count,coverage_rate,missing_loss_rate," +
                                "history_quality_status,price_coverage_rate,amount_coverage_rate," +
                                "signals_json,data_gaps_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, value.getInstrumentId());
                statement.setLong(2, value.getSnapshotId());
                statement.setString(3, text(value.getAsOf()));
                statement.setString(4, text(value.getDataFrom()));
                statement.setString(5, text(value.getDataTo()));
                statement.setString(6, value.getEvaluationVersion());
                statement.setString(7, value.getFactorVersion());
                statement.setString(8, value.getSignalVersion());
                statement.setString(9, value.getInputFingerprint());
                statement.setString(10, value.getStatus());
                statement.setInt(11, value.getDailySampleCount());
                statement.setInt(12, value.getEvaluableEventCount());
                statement.setString(13, text(value.getCoverageRate()));
                statement.setString(14, text(value.getMissingLossRate()));
                statement.setString(15, value.getHistoryQualityStatus());
                statement.setString(16, text(value.getPriceCoverageRate()));
                statement.setString(17, text(value.getAmountCoverageRate()));
                statement.setString(18, signalsJson);
                statement.setString(19, dataGapsJson);
                statement.setString(20, text(value.getCreatedAt()));
                return statement;
            }, keys);
            Long id = keys.getKey() == null
                    ? jdbc.queryForObject("SELECT id FROM market_capital_behavior_evaluation WHERE " +
                                    "snapshot_id=? AND evaluation_version=? AND input_fingerprint=?",
                            Long.class, value.getSnapshotId(), value.getEvaluationVersion(), value.getInputFingerprint())
                    : keys.getKey().longValue();
            value.setId(id);
            return value;
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "cannot persist capital behavior evaluation", error);
        }
    }

    public Optional<CapitalBehaviorEvaluation> findBySnapshotId(Long snapshotId) {
        List<CapitalBehaviorEvaluation> values = jdbc.query(
                "SELECT * FROM market_capital_behavior_evaluation WHERE snapshot_id=? ORDER BY id DESC LIMIT 1",
                (rs, row) -> {
                    try {
                        CapitalBehaviorEvaluation value = new CapitalBehaviorEvaluation();
                        value.setId(rs.getLong("id"));
                        value.setInstrumentId(rs.getLong("instrument_id"));
                        value.setSnapshotId(rs.getLong("snapshot_id"));
                        value.setAsOf(LocalDateTime.parse(rs.getString("as_of")));
                        value.setDataFrom(date(rs.getString("data_from")));
                        value.setDataTo(date(rs.getString("data_to")));
                        value.setEvaluationVersion(rs.getString("evaluation_version"));
                        value.setFactorVersion(rs.getString("factor_version"));
                        value.setSignalVersion(rs.getString("signal_version"));
                        value.setInputFingerprint(rs.getString("input_fingerprint"));
                        value.setStatus(rs.getString("status"));
                        value.setDailySampleCount(rs.getInt("daily_sample_count"));
                        value.setEvaluableEventCount(rs.getInt("evaluable_event_count"));
                        value.setCoverageRate(decimal(rs.getString("coverage_rate")));
                        value.setMissingLossRate(decimal(rs.getString("missing_loss_rate")));
                        value.setHistoryQualityStatus(rs.getString("history_quality_status"));
                        value.setPriceCoverageRate(decimal(rs.getString("price_coverage_rate")));
                        value.setAmountCoverageRate(decimal(rs.getString("amount_coverage_rate")));
                        value.setSignals(mapper.readValue(rs.getString("signals_json"),
                                new TypeReference<List<CapitalSignalEvaluation>>() { }));
                        value.setDataGaps(mapper.readValue(rs.getString("data_gaps_json"),
                                new TypeReference<List<String>>() { }));
                        value.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
                        return value;
                    } catch (Exception error) {
                        throw new BusinessException(ErrorCode.DATABASE_ERROR, "cannot read capital behavior evaluation for snapshot="
                                + snapshotId, error);
                    }
                }, snapshotId);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
