package com.finscope.service.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.instrument.SectorCategory;
import com.finscope.domain.instrument.SectorMarketEntry;
import com.finscope.domain.instrument.SectorMarketSnapshot;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataSnapshot;
import com.finscope.domain.marketintel.DragonTigerRecord;
import com.finscope.rpc.marketintel.DragonTigerData;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 带 Schema 版本和内容哈希校验的市场数据快照编解码器。 */
@Component
public class MarketDataSnapshotCodec {
    private static final int QUOTE_SCHEMA_VERSION = 1;
    private static final int SECTOR_CATALOG_SCHEMA_VERSION = 1;
    private static final int DRAGON_TIGER_SCHEMA_VERSION = 1;
    private final ObjectMapper mapper;

    public MarketDataSnapshotCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.mapper.addMixIn(DragonTigerRecord.class, DragonTigerRecordSnapshotMixin.class);
    }

    public int quoteSchemaVersion() { return QUOTE_SCHEMA_VERSION; }
    public int sectorCatalogSchemaVersion() { return SECTOR_CATALOG_SCHEMA_VERSION; }
    public int dragonTigerSchemaVersion() { return DRAGON_TIGER_SCHEMA_VERSION; }

    public String encodeQuote(Quote quote) {
        try {
            return mapper.writeValueAsString(quote);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("行情快照序列化失败", error);
        }
    }

    public Optional<Quote> decodeQuote(MarketDataSnapshot snapshot) {
        if (snapshot == null || snapshot.getSchemaVersion() != QUOTE_SCHEMA_VERSION) {
            return Optional.empty();
        }
        if (!sha256(snapshot.getPayloadJson()).equals(snapshot.getPayloadHash())) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(snapshot.getPayloadJson(), Quote.class));
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    public MarketDataSnapshot quoteSnapshot(MarketDataCapability capability, String scopeKey,
                                            String providerCode, String providerFamily, Quote quote,
                                            LocalDateTime retrievedAt, LocalDateTime updatedAt) {
        String payload = encodeQuote(quote);
        return new MarketDataSnapshot(capability, scopeKey, providerCode, providerFamily,
                quote.getAsOf(), retrievedAt, payload, sha256(payload), QUOTE_SCHEMA_VERSION, updatedAt);
    }

    public String encodeSectorCatalog(SectorMarketSnapshot snapshot) {
        try {
            return mapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("板块目录快照序列化失败", error);
        }
    }

    public Optional<SectorMarketSnapshot> decodeSectorCatalog(MarketDataSnapshot snapshot) {
        if (snapshot == null || snapshot.getSchemaVersion() != SECTOR_CATALOG_SCHEMA_VERSION
                || snapshot.getCapability() != MarketDataCapability.SECTOR_CATALOG
                || !sha256(snapshot.getPayloadJson()).equals(snapshot.getPayloadHash())) {
            return Optional.empty();
        }
        try {
            JsonNode root = mapper.readTree(snapshot.getPayloadJson());
            SectorCategory category = SectorCategory.valueOf(root.path("category").asText());
            SectorMarketEntry[] entries = mapper.treeToValue(root.path("entries"), SectorMarketEntry[].class);
            List<String> warnings = root.has("warnings")
                    ? Arrays.asList(mapper.treeToValue(root.path("warnings"), String[].class))
                    : Collections.<String>emptyList();
            return Optional.of(new SectorMarketSnapshot(category,
                    textOrDefault(root, "providerCode", snapshot.getProviderCode()),
                    mapper.treeToValue(root.path("retrievedAt"), LocalDateTime.class),
                    textOrDefault(root, "payloadFingerprint", snapshot.getPayloadHash()),
                    Arrays.asList(entries), warnings));
        } catch (Exception error) {
            return Optional.empty();
        }
    }

    public MarketDataSnapshot sectorCatalogSnapshot(String scopeKey, String providerCode,
                                                     String providerFamily, SectorMarketSnapshot snapshot,
                                                     LocalDateTime updatedAt) {
        String payload = encodeSectorCatalog(snapshot);
        return new MarketDataSnapshot(MarketDataCapability.SECTOR_CATALOG, scopeKey,
                providerCode, providerFamily, snapshot.getRetrievedAt(), snapshot.getRetrievedAt(),
                payload, sha256(payload), SECTOR_CATALOG_SCHEMA_VERSION, updatedAt);
    }

    public Optional<DragonTigerData> decodeDragonTiger(MarketDataSnapshot snapshot) {
        if (snapshot == null
                || snapshot.getCapability() != MarketDataCapability.DRAGON_TIGER
                || snapshot.getSchemaVersion() != DRAGON_TIGER_SCHEMA_VERSION
                || !sha256(snapshot.getPayloadJson()).equals(snapshot.getPayloadHash())) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(snapshot.getPayloadJson(), DragonTigerData.class));
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    public MarketDataSnapshot dragonTigerSnapshot(
            String scopeKey, String providerCode, String providerFamily,
            DragonTigerData data, LocalDateTime dataAsOf,
            LocalDateTime retrievedAt, LocalDateTime updatedAt) {
        try {
            String payload = mapper.writeValueAsString(data);
            return new MarketDataSnapshot(MarketDataCapability.DRAGON_TIGER, scopeKey,
                    providerCode, providerFamily, dataAsOf, retrievedAt,
                    payload, sha256(payload), DRAGON_TIGER_SCHEMA_VERSION, updatedAt);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("龙虎榜快照序列化失败", error);
        }
    }

    private String textOrDefault(JsonNode root, String field, String fallback) {
        String value = root.path(field).asText();
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item & 0xff));
            return hex.toString();
        } catch (Exception error) {
            throw new IllegalStateException("JDK does not provide SHA-256", error);
        }
    }

    private abstract static class DragonTigerRecordSnapshotMixin {
        @JsonIgnore abstract Object getBuySeats();
        @JsonIgnore abstract Object getSellSeats();
    }
}
