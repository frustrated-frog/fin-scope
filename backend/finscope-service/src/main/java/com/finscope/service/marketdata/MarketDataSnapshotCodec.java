package com.finscope.service.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.instrument.Quote;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketdata.MarketDataSnapshot;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

/** 带 Schema 版本和内容哈希校验的市场数据快照编解码器。 */
@Component
public class MarketDataSnapshotCodec {
    private static final int QUOTE_SCHEMA_VERSION = 1;
    private final ObjectMapper mapper;

    public MarketDataSnapshotCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().findAndRegisterModules();
    }

    public int quoteSchemaVersion() { return QUOTE_SCHEMA_VERSION; }

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
}
