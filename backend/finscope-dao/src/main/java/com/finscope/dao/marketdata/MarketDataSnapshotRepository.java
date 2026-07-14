package com.finscope.dao.marketdata;

import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MarketDataSnapshotRepository {
    private final JdbcTemplate jdbc;

    public MarketDataSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void upsert(MarketDataSnapshot value) {
        jdbc.update("INSERT INTO market_data_snapshot(capability,scope_key,provider_code,provider_family,"
                        + "as_of,retrieved_at,payload_json,payload_hash,schema_version,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(capability,scope_key) DO UPDATE SET "
                        + "provider_code=excluded.provider_code,provider_family=excluded.provider_family,"
                        + "as_of=excluded.as_of,retrieved_at=excluded.retrieved_at,payload_json=excluded.payload_json,"
                        + "payload_hash=excluded.payload_hash,schema_version=excluded.schema_version,"
                        + "updated_at=excluded.updated_at",
                value.getCapability().name(), value.getScopeKey(), value.getProviderCode(),
                value.getProviderFamily(), text(value.getAsOf()), text(value.getRetrievedAt()),
                value.getPayloadJson(), value.getPayloadHash(), value.getSchemaVersion(),
                text(value.getUpdatedAt()));
    }

    public Optional<MarketDataSnapshot> find(MarketDataCapability capability, String scopeKey) {
        return jdbc.query("SELECT * FROM market_data_snapshot WHERE capability=? AND scope_key=?",
                (rs, rowNum) -> new MarketDataSnapshot(
                        MarketDataCapability.valueOf(rs.getString("capability")),
                        rs.getString("scope_key"), rs.getString("provider_code"),
                        rs.getString("provider_family"), parse(rs.getString("as_of")),
                        parse(rs.getString("retrieved_at")), rs.getString("payload_json"),
                        rs.getString("payload_hash"), rs.getInt("schema_version"),
                        parse(rs.getString("updated_at"))),
                capability.name(), scopeKey).stream().findFirst();
    }

    private static String text(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private static LocalDateTime parse(String value) {
        return value == null ? null : LocalDateTime.parse(value);
    }
}
