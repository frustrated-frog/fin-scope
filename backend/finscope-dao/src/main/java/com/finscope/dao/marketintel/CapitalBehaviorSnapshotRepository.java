package com.finscope.dao.marketintel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;

@Repository
public class CapitalBehaviorSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public CapitalBehaviorSnapshotRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
    }

    public CapitalBehaviorSnapshot save(CapitalBehaviorSnapshot snapshot) {
        try {
            final String factsJson = mapper.writeValueAsString(snapshot.getFacts());
            final String signalsJson = mapper.writeValueAsString(snapshot.getSignals());
            final String warningsJson = mapper.writeValueAsString(snapshot.getWarnings());
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO " +
                        "market_capital_behavior_snapshot(instrument_id,as_of,fingerprint,quality_status," +
                        "facts_json,signals_json,warnings_json,created_at) VALUES(?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, snapshot.getInstrumentId()); ps.setString(2, snapshot.getAsOf().toString());
                ps.setString(3, snapshot.getFingerprint()); ps.setString(4, snapshot.getQualityStatus());
                ps.setString(5, factsJson);
                ps.setString(6, signalsJson);
                ps.setString(7, warningsJson);
                ps.setString(8, snapshot.getCreatedAt().toString()); return ps;
            }, keys);
            Long id = keys.getKey() == null ? jdbc.queryForObject("SELECT id FROM market_capital_behavior_snapshot " +
                            "WHERE instrument_id=? AND as_of=? AND fingerprint=?", Long.class,
                    snapshot.getInstrumentId(), snapshot.getAsOf().toString(), snapshot.getFingerprint())
                    : keys.getKey().longValue();
            snapshot.setId(id); return snapshot;
        } catch (Exception e) { throw new IllegalStateException("cannot persist capital snapshot", e); }
    }

    public Optional<CapitalBehaviorSnapshot> findLatest(Long instrumentId) {
        List<CapitalBehaviorSnapshot> values = jdbc.query("SELECT * FROM market_capital_behavior_snapshot " +
                "WHERE instrument_id=? ORDER BY created_at DESC,id DESC LIMIT 1", mapper(), instrumentId);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public Optional<CapitalBehaviorSnapshot> findById(Long id) {
        List<CapitalBehaviorSnapshot> values = jdbc.query(
                "SELECT * FROM market_capital_behavior_snapshot WHERE id=?", mapper(), id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private RowMapper<CapitalBehaviorSnapshot> mapper() {
        return (rs, row) -> {
            try {
                CapitalBehaviorSnapshot s = new CapitalBehaviorSnapshot(); s.setId(rs.getLong("id"));
                s.setInstrumentId(rs.getLong("instrument_id")); s.setAsOf(LocalDateTime.parse(rs.getString("as_of")));
                s.setFingerprint(rs.getString("fingerprint")); s.setQualityStatus(rs.getString("quality_status"));
                s.setFacts(mapper.readValue(rs.getString("facts_json"), new TypeReference<List<CapitalFlowPoint>>() {}));
                s.setSignals(mapper.readValue(rs.getString("signals_json"), new TypeReference<List<CapitalBehaviorSignal>>() {}));
                s.setWarnings(mapper.readValue(rs.getString("warnings_json"), new TypeReference<List<String>>() {}));
                s.setCreatedAt(LocalDateTime.parse(rs.getString("created_at"))); return s;
            } catch (Exception e) { throw new IllegalStateException("cannot read capital snapshot id=" + rs.getLong("id"), e); }
        };
    }

    public void updateWarnings(Long snapshotId, String qualityStatus, List<String> warnings) {
        try {
            jdbc.update("UPDATE market_capital_behavior_snapshot SET quality_status=?,warnings_json=? WHERE id=?",
                    qualityStatus, mapper.writeValueAsString(warnings), snapshotId);
        } catch (Exception error) {
            throw new IllegalStateException("cannot update capital snapshot warnings id=" + snapshotId, error);
        }
    }
}
