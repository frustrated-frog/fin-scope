package com.finscope.dao.marketdata;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataProviderAttempt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MarketDataProviderAttemptRepository {
    private final JdbcTemplate jdbc;

    public MarketDataProviderAttemptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(MarketDataProviderAttempt value) {
        jdbc.update("INSERT INTO market_data_provider_attempt("
                        + "refresh_run_id,capability,provider_code,provider_family,status,error_type,"
                        + "retry_count,latency_ms,requested_count,accepted_count,started_at,finished_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                value.getRefreshRunId(), value.getCapability().name(), value.getProviderCode(),
                value.getProviderFamily(), value.getStatus(), value.getErrorType(),
                value.getRetryCount(), value.getLatencyMs(), value.getRequestedCount(),
                value.getAcceptedCount(), value.getStartedAt().toString(),
                value.getFinishedAt().toString());
    }

    public List<MarketDataProviderAttempt> findByRun(long refreshRunId) {
        return jdbc.query("SELECT * FROM market_data_provider_attempt "
                        + "WHERE refresh_run_id=? ORDER BY id", (rs, rowNum) ->
                new MarketDataProviderAttempt(rs.getLong("id"), rs.getLong("refresh_run_id"),
                        MarketDataCapability.valueOf(rs.getString("capability")),
                        rs.getString("provider_code"), rs.getString("provider_family"),
                        rs.getString("status"), rs.getString("error_type"),
                        rs.getInt("retry_count"), rs.getLong("latency_ms"),
                        rs.getInt("requested_count"), rs.getInt("accepted_count"),
                        LocalDateTime.parse(rs.getString("started_at")),
                        LocalDateTime.parse(rs.getString("finished_at"))), refreshRunId);
    }
}
