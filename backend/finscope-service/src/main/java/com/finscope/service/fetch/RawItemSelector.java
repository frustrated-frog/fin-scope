package com.finscope.service.fetch;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RawItemSelector {
    private final RawItemSignalScorer signalScorer;

    public RawItemSelector(RawItemSignalScorer signalScorer) {
        this.signalScorer = signalScorer;
    }

    public List<RawItem> select(Source source, Collection<RawItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, RawItem> bestByUrl = new LinkedHashMap<>();
        for (RawItem item : items) {
            RawItemSignal signal = signalScorer.score(source, item);
            applySignal(item, signal);
            if (item == null || !signal.isSelectable()) {
                continue;
            }
            String key = canonicalUrl(item.getUrl());
            RawItem existing = bestByUrl.get(key);
            if (existing == null || compareSignal(item, existing) > 0) {
                bestByUrl.put(key, item);
            }
        }

        List<RawItem> selected = new ArrayList<>(bestByUrl.values());
        selected.sort(Comparator
                .comparingInt(RawItem::getSourceSignalScore)
                .reversed()
                .thenComparing(Comparator.comparingInt(this::contentLength).reversed())
                .thenComparing(RawItem::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int maxItems = source == null ? 0 : source.getMaxItemsPerRun();
        if (maxItems > 0 && selected.size() > maxItems) {
            selected = new ArrayList<>(selected.subList(0, maxItems));
        }

        for (int i = 0; i < selected.size(); i++) {
            selected.get(i).setSourceRank(i + 1);
        }
        return selected;
    }

    private void applySignal(RawItem item, RawItemSignal signal) {
        if (item == null) {
            return;
        }
        item.setSourceSignalScore(signal.getScore());
        item.setSourceSignalReason(signal.getReason());
        item.setSourceRank(0);
    }

    private int compareSignal(RawItem left, RawItem right) {
        int scoreCompare = Integer.compare(left.getSourceSignalScore(), right.getSourceSignalScore());
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        int lengthCompare = Integer.compare(contentLength(left), contentLength(right));
        if (lengthCompare != 0) {
            return lengthCompare;
        }
        if (left.getPublishedAt() == null && right.getPublishedAt() == null) {
            return 0;
        }
        if (left.getPublishedAt() == null) {
            return -1;
        }
        if (right.getPublishedAt() == null) {
            return 1;
        }
        return left.getPublishedAt().compareTo(right.getPublishedAt());
    }

    private String canonicalUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        String trimmed = url.trim();
        try {
            URI uri = new URI(trimmed);
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                return fallbackCanonicalUrl(trimmed);
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            while (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            if ("/".equals(path)) {
                path = "";
            }
            int port = uri.getPort();
            String portPart = port > 0 ? ":" + port : "";
            return host + portPart + path;
        } catch (URISyntaxException ex) {
            return fallbackCanonicalUrl(trimmed);
        }
    }

    private String fallbackCanonicalUrl(String url) {
        String normalized = url.toLowerCase(Locale.ROOT);
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private int contentLength(RawItem item) {
        if (item == null) {
            return 0;
        }
        return length(item.getSummary()) + length(item.getBody());
    }

    private int length(String value) {
        return value == null ? 0 : value.trim().length();
    }
}
