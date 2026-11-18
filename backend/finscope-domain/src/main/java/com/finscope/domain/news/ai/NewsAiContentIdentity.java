package com.finscope.domain.news.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;

/** 内容身份保留数值、符号、否定词和大小写，不使用会删除小数点的标题归一化。 */
public final class NewsAiContentIdentity {
    private NewsAiContentIdentity() { }

    public static String hash(String title, String content) {
        return digest(normalize(title) + "\u0000" + normalize(content));
    }

    public static String occurrence(String title, String content, java.time.LocalDateTime at) {
        return hash(title, content) + "@" + (at == null ? "unknown" : at.toLocalDate());
    }

    public static String bodyHash(String occurrenceKey) {
        return occurrenceKey.split("@", 2)[0];
    }

    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("无法生成新闻内容身份", error);
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).replaceAll("\\s+", " ").trim();
    }
}
