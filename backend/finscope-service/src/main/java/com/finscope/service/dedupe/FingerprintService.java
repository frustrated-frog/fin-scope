package com.finscope.service.dedupe;

import java.net.URI;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class FingerprintService {
    private static final Set<String> TRACKING_PARAMS = Arrays.stream(new String[]{
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "spm", "from"
    }).collect(Collectors.toSet());

    public String urlFingerprint(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "";
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String query = normalizeQuery(uri.getRawQuery());
            URI normalized = new URI(
                    lower(uri.getScheme()),
                    lower(uri.getAuthority()),
                    normalizePath(uri.getPath()),
                    query.isEmpty() ? null : query,
                    null
            );
            return normalized.toString();
        } catch (Exception ignored) {
            return rawUrl.trim().toLowerCase(Locale.ROOT);
        }
    }

    public double titleSimilarity(String left, String right) {
        Map<String, Integer> a = tokenCounts(normalizeText(left));
        Map<String, Integer> b = tokenCounts(normalizeText(right));
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        int leftSize = 0;
        int rightSize = 0;
        Map<String, Integer> all = new LinkedHashMap<String, Integer>();
        all.putAll(a);
        all.putAll(b);
        for (String token : all.keySet()) {
            int av = a.containsKey(token) ? a.get(token) : 0;
            int bv = b.containsKey(token) ? b.get(token) : 0;
            intersection += Math.min(av, bv);
            leftSize += av;
            rightSize += bv;
        }
        return leftSize + rightSize == 0 ? 0.0 : (2.0 * intersection) / (double) (leftSize + rightSize);
    }

    public long bodySimhash(String text) {
        int[] vector = new int[64];
        for (Map.Entry<String, Integer> entry : tokenCounts(normalizeText(text)).entrySet()) {
            long hash = fnv1a64(entry.getKey());
            int weight = entry.getValue();
            for (int i = 0; i < 64; i++) {
                if (((hash >>> i) & 1L) == 1L) {
                    vector[i] += weight;
                } else {
                    vector[i] -= weight;
                }
            }
        }
        long result = 0L;
        for (int i = 0; i < 64; i++) {
            if (vector[i] >= 0) {
                result |= (1L << i);
            }
        }
        return result;
    }

    public int hammingDistance(long left, long right) {
        return Long.bitCount(left ^ right);
    }

    public String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", "")
                .trim();
    }

    private Map<String, Integer> tokenCounts(String normalized) {
        Map<String, Integer> counts = new TreeMap<String, Integer>();
        if (normalized == null || normalized.isEmpty()) {
            return counts;
        }
        for (int i = 0; i < normalized.length(); i++) {
            String one = normalized.substring(i, i + 1);
            add(counts, one);
            if (i + 2 <= normalized.length()) {
                add(counts, normalized.substring(i, i + 2));
            }
        }
        return counts;
    }

    private void add(Map<String, Integer> counts, String token) {
        counts.put(token, counts.containsKey(token) ? counts.get(token) + 1 : 1);
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        Map<String, String> params = new TreeMap<String, String>();
        for (String pair : rawQuery.split("&")) {
            int split = pair.indexOf('=');
            String key = split >= 0 ? pair.substring(0, split) : pair;
            if (!TRACKING_PARAMS.contains(key.toLowerCase(Locale.ROOT))) {
                params.put(key, split >= 0 ? pair.substring(split + 1) : "");
            }
        }
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + (entry.getValue().isEmpty() ? "" : "=" + entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
