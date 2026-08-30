package com.finscope.dao.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.strategy.holding.HoldingStrategyDecision;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class HoldingStrategyDecisionRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    private final ObjectMapper json = new ObjectMapper();

    private final RowMapper<HoldingStrategyDecision> mapper = (rs, rowNum) -> {
        HoldingStrategyDecision value = new HoldingStrategyDecision();
        value.setId(rs.getLong("id"));
        value.setInstrumentCode(rs.getString("instrument_code"));
        value.setInstrumentName(rs.getString("instrument_name"));
        value.setDecisionDate(LocalDate.parse(rs.getString("decision_date")));
        long forecastRunId = rs.getLong("forecast_run_id");
        value.setForecastRunId(rs.wasNull() ? null : forecastRunId);
        value.setHorizonDays(rs.getInt("horizon_days"));
        value.setModelVersion(rs.getString("model_version"));
        value.setDataFingerprint(rs.getString("data_fingerprint"));
        value.setAction(rs.getString("action"));
        value.setSuggestedQuantity(rs.getInt("suggested_quantity"));
        value.setExpectedEdgeAfterCost(rs.getDouble("expected_edge_after_cost"));
        value.setP10RiskAmount(rs.getDouble("p10_risk_amount"));
        value.setP90UpsideAmount(rs.getDouble("p90_upside_amount"));
        value.setCurrentMarketValue(rs.getDouble("current_market_value"));
        value.setProjectedWeight(rs.getDouble("projected_weight"));
        value.setEvidence(readList(rs.getString("evidence_json")));
        value.setBlockers(readList(rs.getString("blockers_json")));
        value.setExplanation(rs.getString("explanation"));
        value.setBenchmark(rs.getString("benchmark"));
        value.setPolicyVersion(rs.getString("policy_version"));
        value.setValidationStatus(rs.getString("validation_status"));
        value.setMaturityDate(date(rs.getString("maturity_date")));
        value.setStrategyReturn(nullableDouble(rs, "strategy_return"));
        value.setHoldReturn(nullableDouble(rs, "hold_return"));
        value.setIncrementalReturn(nullableDouble(rs, "incremental_return"));
        value.setInputJson(rs.getString("input_json"));
        value.setOutputJson(rs.getString("output_json"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return value;
    };

    public HoldingStrategyDecision save(HoldingStrategyDecision value) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO holding_strategy_decision(instrument_code,instrument_name,decision_date,forecast_run_id,"
                            + "horizon_days,model_version,data_fingerprint,action,suggested_quantity,expected_edge_after_cost,"
                            + "p10_risk_amount,p90_upside_amount,current_market_value,projected_weight,evidence_json,blockers_json,"
                            + "explanation,benchmark,policy_version,validation_status,maturity_date,strategy_return,hold_return,"
                            + "incremental_return,input_json,output_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, value.getInstrumentCode());
            statement.setString(2, value.getInstrumentName());
            statement.setString(3, value.getDecisionDate().toString());
            setLong(statement, 4, value.getForecastRunId());
            statement.setInt(5, value.getHorizonDays());
            statement.setString(6, value.getModelVersion());
            statement.setString(7, value.getDataFingerprint());
            statement.setString(8, value.getAction());
            statement.setInt(9, value.getSuggestedQuantity());
            statement.setDouble(10, value.getExpectedEdgeAfterCost());
            statement.setDouble(11, value.getP10RiskAmount());
            statement.setDouble(12, value.getP90UpsideAmount());
            statement.setDouble(13, value.getCurrentMarketValue());
            statement.setDouble(14, value.getProjectedWeight());
            statement.setString(15, writeList(value.getEvidence()));
            statement.setString(16, writeList(value.getBlockers()));
            statement.setString(17, value.getExplanation());
            statement.setString(18, value.getBenchmark());
            statement.setString(19, value.getPolicyVersion());
            statement.setString(20, value.getValidationStatus());
            statement.setString(21, value.getMaturityDate() == null ? null : value.getMaturityDate().toString());
            setDouble(statement, 22, value.getStrategyReturn());
            setDouble(statement, 23, value.getHoldReturn());
            setDouble(statement, 24, value.getIncrementalReturn());
            statement.setString(25, value.getInputJson());
            statement.setString(26, value.getOutputJson());
            statement.setString(27, TimeUtil.text(now));
            return statement;
        }, keys);
        value.setId(keys.getKey().longValue());
        value.setCreatedAt(now);
        return findById(value.getId()).orElse(value);
    }

    public Optional<HoldingStrategyDecision> findById(Long id) {
        List<HoldingStrategyDecision> values = jdbcTemplate.query(
                "SELECT * FROM holding_strategy_decision WHERE id=?", mapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public Optional<HoldingStrategyDecision> findUnique(String instrumentCode,
                                                        LocalDate decisionDate,
                                                        String policyVersion) {
        List<HoldingStrategyDecision> values = jdbcTemplate.query(
                "SELECT * FROM holding_strategy_decision WHERE instrument_code=? AND decision_date=? AND policy_version=?",
                mapper, instrumentCode, decisionDate.toString(), policyVersion);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<HoldingStrategyDecision> findAll(int limit) {
        int bounded = Math.max(1, Math.min(limit, 500));
        return jdbcTemplate.query("SELECT * FROM holding_strategy_decision ORDER BY decision_date DESC,id DESC LIMIT ?",
                mapper, bounded);
    }

    public List<HoldingStrategyDecision> findPendingDue(LocalDate today, int limit) {
        return jdbcTemplate.query("SELECT * FROM holding_strategy_decision "
                        + "WHERE validation_status='PENDING' AND maturity_date IS NOT NULL "
                        + "AND maturity_date<=? ORDER BY maturity_date,id LIMIT ?",
                mapper, today.toString(), Math.max(1, Math.min(limit, 200)));
    }

    public boolean settle(Long id, double strategyReturn, double holdReturn,
                          double incrementalReturn) {
        return jdbcTemplate.update("UPDATE holding_strategy_decision SET validation_status='MATURED',"
                        + "strategy_return=?,hold_return=?,incremental_return=? "
                        + "WHERE id=? AND validation_status='PENDING'",
                strategyReturn, holdReturn, incrementalReturn, id) == 1;
    }

    public boolean markUnavailable(Long id) {
        return jdbcTemplate.update("UPDATE holding_strategy_decision SET validation_status='UNAVAILABLE' "
                        + "WHERE id=? AND validation_status='PENDING'", id) == 1;
    }

    private List<String> readList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return json.readValue(value, new TypeReference<List<String>>() { });
        } catch (Exception error) {
            throw new IllegalStateException("持仓策略证据 JSON 已损坏", error);
        }
    }

    private String writeList(List<String> value) {
        try {
            return json.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (Exception error) {
            throw new IllegalStateException("无法序列化持仓策略证据", error);
        }
    }

    private void setLong(PreparedStatement statement, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value);
        }
    }

    private void setDouble(PreparedStatement statement, int index, Double value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.REAL);
        } else {
            statement.setDouble(index, value);
        }
    }

    private LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private Double nullableDouble(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }
}
