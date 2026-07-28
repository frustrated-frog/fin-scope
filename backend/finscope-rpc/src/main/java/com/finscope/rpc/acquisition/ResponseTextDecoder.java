package com.finscope.rpc.acquisition;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 文本响应解码器，综合 BOM、响应头和正文声明识别字符集。
 */
public final class ResponseTextDecoder {
    private static final int DECLARATION_SCAN_BYTES = 8192;
    private static final Pattern CONTENT_TYPE_CHARSET = Pattern.compile(
            "(?i)charset\\s*=\\s*['\\\"]?([a-zA-Z0-9_\\-]+)");
    private static final Pattern META_CHARSET = Pattern.compile(
            "(?i)<meta[^>]*charset\\s*=\\s*['\\\"]?([a-zA-Z0-9_\\-]+)");
    private static final Pattern META_HTTP_EQUIV_CHARSET = Pattern.compile(
            "(?i)<meta[^>]*http-equiv\\s*=\\s*['\\\"]content-type['\\\"][^>]*content\\s*=\\s*['\\\"][^>]*charset=([a-zA-Z0-9_\\-]+)");
    private static final Pattern XML_ENCODING = Pattern.compile(
            "(?i)<\\?xml[^>]*encoding\\s*=\\s*['\\\"]([a-zA-Z0-9_\\-]+)['\\\"]");

    private ResponseTextDecoder() {
    }

    public static DecodedText decode(byte[] bytes, String contentType, boolean inspectBodyDeclaration) {
        if (bytes == null || bytes.length == 0) {
            return new DecodedText("", StandardCharsets.UTF_8.name());
        }

        Bom bom = detectBom(bytes);
        byte[] payload = copyWithoutBom(bytes, bom.offset);
        Set<String> candidates = new LinkedHashSet<String>();
        addCandidate(candidates, bom.charsetName);
        addCandidate(candidates, findCharset(CONTENT_TYPE_CHARSET, contentType));
        if (inspectBodyDeclaration) {
            String declaration = new String(payload, 0,
                    Math.min(payload.length, DECLARATION_SCAN_BYTES), StandardCharsets.ISO_8859_1);
            addCandidate(candidates, findCharset(META_CHARSET, declaration));
            addCandidate(candidates, findCharset(META_HTTP_EQUIV_CHARSET, declaration));
            addCandidate(candidates, findCharset(XML_ENCODING, declaration));
        }
        addCandidate(candidates, StandardCharsets.UTF_8.name());
        addCandidate(candidates, "GB18030");
        addCandidate(candidates, "BIG5");

        DecodedText best = null;
        int bestReplacementCount = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            if (!Charset.isSupported(candidate)) {
                continue;
            }
            String text = new String(payload, Charset.forName(candidate));
            int replacementCount = replacementCount(text);
            if (replacementCount < bestReplacementCount) {
                best = new DecodedText(text, candidate);
                bestReplacementCount = replacementCount;
            }
            if (replacementCount == 0) {
                return best;
            }
        }
        return best == null
                ? new DecodedText(new String(payload, StandardCharsets.UTF_8), StandardCharsets.UTF_8.name())
                : best;
    }

    private static void addCandidate(Set<String> candidates, String value) {
        String normalized = normalize(value);
        if (!normalized.isEmpty()) {
            candidates.add(normalized);
        }
    }

    private static String findCharset(Pattern pattern, String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String charset = value.trim().toUpperCase();
        if ("GB2312".equals(charset) || "GBK".equals(charset) || "GB_2312".equals(charset)) {
            return "GB18030";
        }
        if ("UTF8".equals(charset)) {
            return StandardCharsets.UTF_8.name();
        }
        return charset;
    }

    private static int replacementCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\uFFFD') {
                count++;
            }
        }
        return count;
    }

    private static byte[] copyWithoutBom(byte[] bytes, int offset) {
        if (offset == 0) {
            return bytes;
        }
        byte[] result = new byte[bytes.length - offset];
        System.arraycopy(bytes, offset, result, 0, result.length);
        return result;
    }

    private static Bom detectBom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return new Bom(StandardCharsets.UTF_8.name(), 3);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new Bom("UTF-16BE", 2);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new Bom("UTF-16LE", 2);
        }
        return new Bom(null, 0);
    }

    private static final class Bom {
        private final String charsetName;
        private final int offset;

        private Bom(String charsetName, int offset) {
            this.charsetName = charsetName;
            this.offset = offset;
        }
    }

    public static final class DecodedText {
        private final String text;
        private final String charsetName;

        private DecodedText(String text, String charsetName) {
            this.text = text;
            this.charsetName = charsetName;
        }

        public String getText() {
            return text;
        }

        public String getCharsetName() {
            return charsetName;
        }
    }
}
