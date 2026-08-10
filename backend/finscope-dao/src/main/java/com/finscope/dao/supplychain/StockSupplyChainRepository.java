package com.finscope.dao.supplychain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.supplychain.StockSupplyChainRefreshRun;
import com.finscope.domain.supplychain.StockSupplyChainSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 股票产业链当前快照与刷新运行存储。 */
@Repository
public class StockSupplyChainRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ObjectMapper objectMapper;

    private final RowMapper<StockSupplyChainRefreshRun> runMapper = (rs, row) -> {
        StockSupplyChainRefreshRun value = new StockSupplyChainRefreshRun();
        value.setId(rs.getLong("id"));
        value.setInstrumentId(rs.getLong("instrument_id"));
        value.setStatus(rs.getString("status"));
        value.setStage(rs.getString("stage"));
        value.setMessage(rs.getString("message"));
        value.setErrorCode(rs.getString("error_code"));
        value.setErrorMessage(rs.getString("error_message"));
        value.setRetryable(rs.getInt("retryable") == 1);
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setCompletedAt(TimeUtil.localDateTime(rs, "completed_at"));
        return value;
    };

    public Optional<StockSupplyChainSnapshot> findSnapshot(Long instrumentId) {
        List<StockSupplyChainSnapshot> values = jdbcTemplate.query(
                "SELECT * FROM stock_supply_chain_snapshot WHERE instrument_id=?",
                (rs, row) -> readSnapshot(rs.getLong("id"), rs.getLong("instrument_id"),
                        rs.getString("payload_json"), TimeUtil.localDateTime(rs, "updated_at")),
                instrumentId);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public StockSupplyChainRefreshRun createRun(Long instrumentId) {
        LocalDateTime now = LocalDateTime.now();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO stock_supply_chain_refresh_run(instrument_id,status,stage,message,retryable,created_at) "
                            + "VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, instrumentId);
            statement.setString(2, "RUNNING");
            statement.setString(3, "QUEUED");
            statement.setString(4, "产业链证据刷新已进入队列");
            statement.setInt(5, 0);
            statement.setString(6, TimeUtil.text(now));
            return statement;
        }, keys);
        return latestRun(instrumentId).orElseThrow(() -> new IllegalStateException("产业链刷新运行保存失败"));
    }

    public Optional<StockSupplyChainRefreshRun> latestRun(Long instrumentId) {
        List<StockSupplyChainRefreshRun> values = jdbcTemplate.query(
                "SELECT * FROM stock_supply_chain_refresh_run WHERE instrument_id=? ORDER BY id DESC LIMIT 1",
                runMapper, instrumentId);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public Optional<StockSupplyChainRefreshRun> activeRun(Long instrumentId) {
        List<StockSupplyChainRefreshRun> values = jdbcTemplate.query(
                "SELECT * FROM stock_supply_chain_refresh_run WHERE instrument_id=? AND status='RUNNING' "
                        + "ORDER BY id DESC LIMIT 1", runMapper, instrumentId);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public StockSupplyChainRefreshRun updateRun(StockSupplyChainRefreshRun run) {
        LocalDateTime completedAt = "RUNNING".equals(run.getStatus()) ? null : LocalDateTime.now();
        jdbcTemplate.update("UPDATE stock_supply_chain_refresh_run SET status=?,stage=?,message=?,error_code=?,"
                        + "error_message=?,retryable=?,completed_at=? WHERE id=?",
                run.getStatus(), run.getStage(), run.getMessage(), run.getErrorCode(), run.getErrorMessage(),
                run.isRetryable() ? 1 : 0, TimeUtil.text(completedAt), run.getId());
        return latestRun(run.getInstrumentId())
                .orElseThrow(() -> new IllegalStateException("产业链刷新运行不存在"));
    }

    @Transactional
    public StockSupplyChainSnapshot replaceSnapshotAndComplete(
            StockSupplyChainSnapshot snapshot, StockSupplyChainRefreshRun run) {
        LocalDateTime now = LocalDateTime.now();
        snapshot.setUpdatedAt(now);
        try {
            String payload = objectMapper.writeValueAsString(snapshot);
            jdbcTemplate.update("INSERT INTO stock_supply_chain_snapshot(instrument_id,payload_json,schema_version,"
                            + "model,evidence_as_of,generated_at,updated_at) VALUES(?,?,?,?,?,?,?) "
                            + "ON CONFLICT(instrument_id) DO UPDATE SET payload_json=excluded.payload_json,"
                            + "schema_version=excluded.schema_version,model=excluded.model,evidence_as_of=excluded.evidence_as_of,"
                            + "generated_at=excluded.generated_at,updated_at=excluded.updated_at",
                    snapshot.getInstrumentId(), payload, snapshot.getSchemaVersion(), snapshot.getModel(),
                    snapshot.getEvidenceAsOf() == null ? null : snapshot.getEvidenceAsOf().toString(),
                    TimeUtil.text(snapshot.getGeneratedAt()), TimeUtil.text(now));
        } catch (Exception error) {
            throw new IllegalStateException("产业链快照序列化失败", error);
        }
        run.setStatus("READY");
        run.setStage("COMPLETED");
        run.setMessage("产业链证据图谱已更新");
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.setRetryable(false);
        updateRun(run);
        return findSnapshot(snapshot.getInstrumentId())
                .orElseThrow(() -> new IllegalStateException("产业链快照保存失败"));
    }

    private StockSupplyChainSnapshot readSnapshot(Long id, Long instrumentId,
                                                   String payload, LocalDateTime updatedAt) {
        try {
            StockSupplyChainSnapshot value = objectMapper.readValue(payload, StockSupplyChainSnapshot.class);
            value.setId(id);
            value.setInstrumentId(instrumentId);
            value.setUpdatedAt(updatedAt);
            return value;
        } catch (Exception error) {
            throw new IllegalStateException("产业链快照解析失败", error);
        }
    }
}
