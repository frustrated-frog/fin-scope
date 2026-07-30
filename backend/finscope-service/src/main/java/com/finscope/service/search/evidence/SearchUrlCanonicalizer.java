package com.finscope.service.search.evidence;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchUrlCanonicalizer {
    public String canonicalize(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) return "";
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) return "";
            host = host.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) port = -1;
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            String query = filterQuery(uri.getRawQuery());
            return new URI(scheme, uri.getRawUserInfo(), host, port, path,
                    query.isEmpty() ? null : query, null).toASCIIString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String filterQuery(String rawQuery) throws Exception {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        List<String> kept = new ArrayList<String>();
        for (String part : rawQuery.split("&")) {
            String rawName = part.contains("=") ? part.substring(0, part.indexOf('=')) : part;
            String name = URLDecoder.decode(rawName, "UTF-8").toLowerCase(Locale.ROOT);
            if (name.startsWith("utm_") || "fbclid".equals(name) || "gclid".equals(name)) continue;
            kept.add(part);
        }
        return String.join("&", kept);
    }
}
