package com.finscope.service.fetch;

import com.finscope.domain.fetch.RawItem;
import com.finscope.domain.source.Source;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class RawItemSignalScorer {
    private static final int MIN_SELECTABLE_SCORE = 55;
    private static final int MIN_SELECTABLE_CONTENT_LENGTH = 40;

    public RawItemSignal score(Source source, RawItem item) {
        if (item == null) {
            return new RawItemSignal(0, false, "empty item");
        }

        List<String> reasons = new ArrayList<>();
        int score = clamp(item.getQualityScore(), 0, 100);
        reasons.add("quality=" + score);

        int credibilityBonus = credibilityBonus(source);
        score += credibilityBonus;
        reasons.add("credibility=" + signed(credibilityBonus));

        int richnessBonus = richnessBonus(item);
        score += richnessBonus;
        reasons.add("richness=" + signed(richnessBonus));

        int bodyBonus = bodyBonus(item.getBody());
        score += bodyBonus;
        reasons.add("body=" + signed(bodyBonus));

        int extractionBonus = extractionBonus(item.getExtractionMethod());
        score += extractionBonus;
        reasons.add("extraction=" + signed(extractionBonus));

        int recencyBonus = recencyBonus(item.getPublishedAt());
        score += recencyBonus;
        reasons.add("recency=" + signed(recencyBonus));

        int completenessPenalty = completenessPenalty(item);
        score += completenessPenalty;
        if (completenessPenalty != 0) {
            reasons.add("completeness=" + signed(completenessPenalty));
        }

        int finalScore = clamp(score, 0, 100);
        boolean selectable = isSelectable(item, finalScore);
        reasons.add("selectable=" + selectable);
        return new RawItemSignal(finalScore, selectable, String.join(", ", reasons));
    }

    private int credibilityBonus(Source source) {
        if (source == null) {
            return 0;
        }
        int credibility = clamp(source.getCredibility(), 1, 5);
        return (credibility - 3) * 5;
    }

    private int richnessBonus(RawItem item) {
        int length = length(item.getTitle()) + length(item.getSummary()) + length(item.getBody());
        if (length >= 1000) {
            return 8;
        }
        if (length >= 500) {
            return 6;
        }
        if (length >= 200) {
            return 4;
        }
        if (length >= 80) {
            return 2;
        }
        if (length < 20) {
            return -20;
        }
        if (length < MIN_SELECTABLE_CONTENT_LENGTH) {
            return -10;
        }
        return 0;
    }

    private int bodyBonus(String body) {
        int length = length(body);
        if (length >= 400) {
            return 6;
        }
        if (length >= 150) {
            return 4;
        }
        if (length >= 60) {
            return 2;
        }
        if (length < 20) {
            return -12;
        }
        return 0;
    }

    private int extractionBonus(String extractionMethod) {
        String method = normalize(extractionMethod);
        if (method.startsWith("web:profile:")) {
            return 6;
        }
        if (method.startsWith("rss:")) {
            return 4;
        }
        if (method.startsWith("web:generic")) {
            return 1;
        }
        if (method.isEmpty() || "unknown".equals(method)) {
            return -4;
        }
        return 0;
    }

    private int recencyBonus(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return 0;
        }
        long hours = Duration.between(publishedAt, LocalDateTime.now()).toHours();
        if (hours < 0) {
            return 0;
        }
        if (hours <= 24) {
            return 5;
        }
        if (hours <= 72) {
            return 3;
        }
        if (hours <= 168) {
            return 1;
        }
        if (hours <= 336) {
            return -4;
        }
        return -8;
    }

    private int completenessPenalty(RawItem item) {
        int penalty = 0;
        if (isBlank(item.getTitle())) {
            penalty -= 20;
        }
        if (isBlank(item.getUrl())) {
            penalty -= 30;
        }
        if (length(item.getSummary()) + length(item.getBody()) == 0) {
            penalty -= 20;
        }
        return penalty;
    }

    private boolean isSelectable(RawItem item, int score) {
        return score >= MIN_SELECTABLE_SCORE
                && !isBlank(item.getTitle())
                && !isBlank(item.getUrl())
                && length(item.getSummary()) + length(item.getBody()) >= MIN_SELECTABLE_CONTENT_LENGTH;
    }

    private int length(String value) {
        return value == null ? 0 : value.trim().length();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }
}
