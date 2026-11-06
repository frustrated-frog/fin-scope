package com.finscope.service.research.quality;

import com.finscope.domain.research.quality.ResearchEvidenceDocument;
import com.finscope.domain.research.quality.ResearchEvidenceReview;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative connected components: domain, explicit attribution, exact or near identical article text. */
@Component
public class ResearchSourceLineage {
    private static final Pattern ORIGIN = Pattern.compile("(?:来源|转载自|转自|原文来自)\\s*[:：]?\\s*([\\p{IsHan}A-Za-z0-9·._-]{2,24})");

    public int assign(List<ResearchEvidenceDocument> documents, List<ResearchEvidenceReview> reviews) {
        int[] roots = new int[documents.size()];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = i;
        }
        for (int i = 0; i < documents.size(); i++) {
            for (int j = 0; j < i; j++) {
                String reason = sharedOrigin(documents.get(i), documents.get(j));
                if (reason != null) {
                    roots[root(roots, i)] = root(roots, j);
                    reviews.get(i).setLineageReason(reason);
                }
            }
        }
        Set<Integer> accepted = new HashSet<>();
        for (int i = 0; i < documents.size(); i++) {
            reviews.get(i).setSourceGroup("G" + (root(roots, i) + 1));
            if (reviews.get(i).getLineageReason() == null) {
                reviews.get(i).setLineageReason("未发现转载关系；独立性仍受可见正文限制");
            }
            if (reviews.get(i).isAccepted() && !domain(documents.get(i).getUrl()).isBlank()) {
                accepted.add(root(roots, i));
            }
        }
        return accepted.size();
    }

    private String sharedOrigin(ResearchEvidenceDocument first, ResearchEvidenceDocument second) {
        String domain = domain(first.getUrl());
        if (!domain.isBlank() && domain.equals(domain(second.getUrl()))) {
            return "同站点来源";
        }
        String leftOrigin = origin(first);
        String rightOrigin = origin(second);
        if (!leftOrigin.isBlank() && (leftOrigin.equals(rightOrigin) || leftOrigin.equals(clean(second.getSource())))) {
            return "正文标注相同原始来源：" + leftOrigin;
        }
        if (!rightOrigin.isBlank() && rightOrigin.equals(clean(first.getSource()))) {
            return "正文标注转载自：" + rightOrigin;
        }
        String left = clean(first.getText());
        String right = clean(second.getText());
        if (left.length() >= 60 && left.equals(right)) {
            return "正文相同，合并转载来源";
        }
        if (left.length() >= 100 && right.length() >= 100 && overlap(left, right) >= 0.8D) {
            return "正文片段高度重合，保守合并来源";
        }
        return null;
    }

    private double overlap(String left, String right) {
        Set<String> first = shingles(left);
        Set<String> second = shingles(right);
        Set<String> shared = new HashSet<>(first);
        shared.retainAll(second);
        return (double) shared.size() / Math.max(1, Math.min(first.size(), second.size()));
    }

    private Set<String> shingles(String value) {
        Set<String> result = new HashSet<>();
        String bounded = value.substring(0, Math.min(12_000, value.length()));
        for (int index = 0; index + 12 <= bounded.length(); index += 6) {
            result.add(bounded.substring(index, index + 12));
        }
        return result;
    }

    private String origin(ResearchEvidenceDocument document) {
        Matcher matcher = ORIGIN.matcher(text(document.getText()));
        return matcher.find() ? clean(matcher.group(1)) : "";
    }

    private int root(int[] roots, int index) {
        while (roots[index] != index) {
            index = roots[index];
        }
        return index;
    }

    private String domain(String url) {
        try {
            String host = URI.create(text(url)).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException invalidUrl) {
            return "";
        }
    }

    private String clean(String value) {
        return text(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsHan}a-z0-9]", "");
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
