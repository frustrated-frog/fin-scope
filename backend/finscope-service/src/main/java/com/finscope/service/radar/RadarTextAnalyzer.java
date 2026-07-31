package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RadarTextAnalyzer {
    private static final List<String> SUBJECTS = Arrays.asList(
            "宁德时代", "比亚迪", "小米", "阿里巴巴", "腾讯", "百度", "京东", "特斯拉",
            "英伟达", "微软", "苹果", "美联储", "中国央行", "人民银行", "证监会", "国务院");
    private static final List<String> ACTIONS = Arrays.asList(
            "正式发布", "发布", "公告", "上涨", "下跌", "走强", "回落", "收购", "签约", "增持", "减持",
            "降息", "加息", "维持", "开展", "处罚", "批准", "推出", "上线");
    private static final List<String> VARIABLES = Arrays.asList(
            "电池", "芯片", "汽车", "利率", "逆回购", "黄金", "财报", "订单", "融资", "并购", "产能", "价格");

    private final FingerprintService fingerprints;

    public RadarTextAnalyzer(FingerprintService fingerprints) { this.fingerprints = fingerprints; }

    public SignalFeatures analyze(RadarSignal signal) {
        String title = safe(signal.getTitle());
        String text = title + " " + safe(signal.getContent());
        return new SignalFeatures(normalize(title), normalizedCategory(signal.getCategoryCode()),
                matches(text, SUBJECTS), matches(text, ACTIONS), matches(text, VARIABLES));
    }

    public double similarity(SignalFeatures left, SignalFeatures right) {
        if (!left.category.equals(right.category) && Collections.disjoint(left.subjects, right.subjects)) return 0.0;
        double score = 0.55 * fingerprints.titleSimilarity(left.normalizedTitle, right.normalizedTitle);
        if (!left.subjects.isEmpty() && !Collections.disjoint(left.subjects, right.subjects)) score += 0.20;
        if (!left.actions.isEmpty() && !Collections.disjoint(left.actions, right.actions)) score += 0.10;
        if (!left.variables.isEmpty() && !Collections.disjoint(left.variables, right.variables)) score += 0.10;
        if (left.category.equals(right.category)) score += 0.05;
        return Math.min(1.0, score);
    }

    public boolean hasSubjectConflict(SignalFeatures left, SignalFeatures right) {
        return !left.subjects.isEmpty() && !right.subjects.isEmpty()
                && Collections.disjoint(left.subjects, right.subjects);
    }

    public String eventKey(SignalFeatures features) {
        String subject = first(features.subjects);
        String action = first(features.actions);
        String variable = first(features.variables);
        String fallback = features.normalizedTitle.length() <= 32
                ? features.normalizedTitle : features.normalizedTitle.substring(0, 32);
        return features.category + ":" + nonBlank(subject, variable, fallback) + ":"
                + nonBlank(action, "事件") + ":" + nonBlank(variable, "信息");
    }

    public String normalize(String value) { return fingerprints.normalizeText(value); }

    private Set<String> matches(String text, List<String> candidates) {
        Set<String> values = new LinkedHashSet<String>();
        String normalized = safe(text).toLowerCase(Locale.ROOT);
        for (String candidate : candidates) if (normalized.contains(candidate.toLowerCase(Locale.ROOT))) values.add(candidate);
        return values;
    }

    private String normalizedCategory(String value) {
        return value == null || value.trim().isEmpty() ? "UNCLASSIFIED" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String first(Set<String> values) { return values.isEmpty() ? "" : values.iterator().next(); }
    private String nonBlank(String... values) { for (String value : values) if (value != null && !value.isEmpty()) return value; return "信息"; }
    private String safe(String value) { return value == null ? "" : value; }

    public static final class SignalFeatures {
        private final String normalizedTitle;
        private final String category;
        private final Set<String> subjects;
        private final Set<String> actions;
        private final Set<String> variables;

        SignalFeatures(String normalizedTitle, String category, Set<String> subjects,
                       Set<String> actions, Set<String> variables) {
            this.normalizedTitle = normalizedTitle; this.category = category;
            this.subjects = subjects; this.actions = actions; this.variables = variables;
        }

        public String getNormalizedTitle() { return normalizedTitle; }
        public String getCategory() { return category; }
        public Set<String> getSubjects() { return subjects; }
        public Set<String> getActions() { return actions; }
        public Set<String> getVariables() { return variables; }
    }
}
