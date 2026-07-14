package com.finscope.rpc.marketdata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Provider 调用的标准化、不可变结果。 */
public final class ProviderResult<T> {
    private final T data;
    private final LocalDateTime retrievedAt;
    private final String payloadHash;
    private final List<String> warnings;

    private ProviderResult(T data, LocalDateTime retrievedAt, String payloadHash, List<String> warnings) {
        if (retrievedAt == null) {
            throw new IllegalArgumentException("retrievedAt must not be null");
        }
        this.data = data;
        this.retrievedAt = retrievedAt;
        this.payloadHash = payloadHash;
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(
                warnings == null ? Collections.<String>emptyList() : warnings));
    }

    public static <T> ProviderResult<T> of(T data, LocalDateTime retrievedAt,
                                            String payloadHash, List<String> warnings) {
        return new ProviderResult<T>(data, retrievedAt, payloadHash, warnings);
    }

    public static String hashOf(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK does not provide SHA-256", error);
        }
    }

    public T getData() { return data; }
    public LocalDateTime getRetrievedAt() { return retrievedAt; }
    public String getPayloadHash() { return payloadHash; }
    public List<String> getWarnings() { return warnings; }
}
