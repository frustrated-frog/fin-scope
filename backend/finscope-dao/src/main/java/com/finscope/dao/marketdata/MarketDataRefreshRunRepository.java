package com.finscope.dao.marketdata;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataRefreshRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MarketDataRefreshRunRepository {
    private final JdbcTemplate jdbc;

    public MarketDataRefreshRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long create(MarketDataCapability capability, String scopeSummary,
                       String triggerType, LocalDateTime startedAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO market_data_refresh_run(capability,scope_summary,trigger_type,status,started_at) "
                            + "VALUES(?,?,?,'RUNNING',?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, capability.name());
            statement.setString(2, scopeSummary);
            statement.setString(3, triggerType);
            statement.setString(4, startedAt.toString());
            return statement;
        }, keys);
        if (keys.getKey() == null) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR, "market data refresh run id was not generated");
        }
        return keys.getKey().longValue();
    }

    public void finish(long id, String status, int requestedCount, int freshCount,
                       int staleCount, int failedCount, String selectedSources,
                       String warningMessage, LocalDateTime finishedAt) {
        int updated = jdbc.update("UPDATE market_data_refresh_run SET status=?,finished_at=?,"
                        + "requested_count=?,fresh_count=?,stale_count=?,failed_count=?,"
                        + "selected_sources=?,warning_message=? WHERE id=?",
                status, finishedAt.toString(), requestedCount, freshCount, staleCount, failedCount,
                selectedSources, warningMessage, id);
        if (updated != 1) {
            throw new BusinessException(BizErrorCode.MARKET_DATA_REFRESH_RUN_NOT_FOUND,
                    BizErrorCode.MARKET_DATA_REFRESH_RUN_NOT_FOUND.format(id), null);
        }
    }

    public Optional<MarketDataRefreshRun> find(long id) {
        return jdbc.query("SELECT * FROM market_data_refresh_run WHERE id=?", (rs, rowNum) -> {
            String finishedAt = rs.getString("finished_at");
            return new MarketDataRefreshRun(rs.getLong("id"),
                    MarketDataCapability.valueOf(rs.getString("capability")),
                    rs.getString("scope_summary"), rs.getString("trigger_type"),
                    rs.getString("status"), LocalDateTime.parse(rs.getString("started_at")),
                    finishedAt == null ? null : LocalDateTime.parse(finishedAt),
                    rs.getInt("requested_count"), rs.getInt("fresh_count"),
                    rs.getInt("stale_count"), rs.getInt("failed_count"),
                    rs.getString("selected_sources"), rs.getString("warning_message"));
        }, id).stream().findFirst();
    }

    public int deleteFinishedBefore(LocalDateTime cutoff) {
        return jdbc.update("DELETE FROM market_data_refresh_run WHERE finished_at IS NOT NULL AND finished_at < ?",
                cutoff.toString());
    }
}
