package com.finscope.service.radar;

import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

public class RadarTextAnalyzer {
    private static final Pattern SECURITY_CODE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");
    private static final Pattern NUMERIC_FACT = Pattern.compile(
            "(?<![\\d.])(\\d+(?:\\.\\d+)?)\\s*(GWH|MWH|KWH|GW|MW|亿元|万元|元|%|％)",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> SUBJECTS = Arrays.asList(
            "宁德时代", "比亚迪", "小米", "阿里巴巴", "腾讯", "百度", "京东", "特斯拉",
            "英伟达", "微软", "苹果", "美联储", "中国央行", "人民银行", "证监会", "国务院");
    private static final List<String> ACTIONS = Arrays.asList(
            "正式发布", "发布", "公告", "上涨", "下跌", "走强", "回落", "收购", "签约", "增持", "减持",
            "降息", "加息", "维持", "开展", "处罚", "批准", "推出", "上线");
    private static final List<String> VARIABLES = Arrays.asList(
            "电池", "芯片", "汽车", "利率", "逆回购", "黄金", "财报", "订单", "融资", "并购", "产能", "价格");
    private static final List<String> DIRECTIONS = Arrays.asList(
            "上涨", "走强", "增持", "扩产", "提升", "下跌", "走弱", "回落", "减持", "缩产", "下降");

    private final FingerprintService fingerprints;

    public RadarTextAnalyzer(FingerprintService fingerprints) { this.fingerprints = fingerprints; }

    public SignalFeatures analyze(RadarSignal signal) {
        RadarSignalFeatures features = extract(signal);
        return new SignalFeatures(features.getNormalizedTitle(), features.getCategory(),
                features.getSubjects(), features.getActions(), features.getVariables(), features.getEntities());
    }

    public RadarSignalFeatures extract(RadarSignal signal) {
        String title = safe(signal == null ? null : signal.getTitle());
        String content = safe(signal == null ? null : signal.getContent());
        String text = title + " " + content;
        return new RadarSignalFeatures(normalize(title), normalize(content),
                normalizedCategory(signal == null ? null : signal.getCategoryCode()),
                matches(text, SUBJECTS), matches(text, ACTIONS), matches(text, VARIABLES),
                matches(text, DIRECTIONS), identifiers(text), numericFacts(text), signal == null ? null
                : signal.getPublishedAt() == null ? signal.getFirstSeenAt() : signal.getPublishedAt());
    }

    public double similarity(SignalFeatures left, SignalFeatures right) {
        if (!left.category.equals(right.category) && Collections.disjoint(left.subjects, right.subjects)) return 0.0;
        double score = 0.55 * fingerprints.titleSimilarity(left.normalizedTitle, right.normalizedTitle);
        if (!left.subjects.isEmpty() && !Collections.disjoint(left.subjects, right.subjects)) score += 0.20;
        if (!left.entities.isEmpty() && !Collections.disjoint(left.entities, right.entities)) score += 0.25;
        if (!left.actions.isEmpty() && !Collections.disjoint(left.actions, right.actions)) score += 0.10;
        if (!left.variables.isEmpty() && !Collections.disjoint(left.variables, right.variables)) score += 0.10;
        if (left.category.equals(right.category)) score += 0.05;
        return Math.min(1.0, score);
    }

    public boolean hasSubjectConflict(SignalFeatures left, SignalFeatures right) {
        return !left.subjects.isEmpty() && !right.subjects.isEmpty()
                && Collections.disjoint(left.subjects, right.subjects);
    }

    public boolean hasFactConflict(RadarSignalFeatures left, RadarSignalFeatures right) {
        if (!left.getEntities().isEmpty() && !right.getEntities().isEmpty()
                && Collections.disjoint(left.getEntities(), right.getEntities())) return true;
        if (!left.getSubjects().isEmpty() && !right.getSubjects().isEmpty()
                && Collections.disjoint(left.getSubjects(), right.getSubjects())) return true;
        return opposite(left.getDirections(), right.getDirections())
                || conflictingActions(left.getActions(), right.getActions())
                || conflictingNumericFacts(left.getNumericFacts(), right.getNumericFacts());
    }

    private boolean conflictingNumericFacts(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        Map<String, Set<String>> leftByUnit = factsByUnit(left);
        Map<String, Set<String>> rightByUnit = factsByUnit(right);
        for (Map.Entry<String, Set<String>> entry : leftByUnit.entrySet()) {
            Set<String> rightValues = rightByUnit.get(entry.getKey());
            if (rightValues != null && Collections.disjoint(entry.getValue(), rightValues)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Set<String>> factsByUnit(Set<String> facts) {
        Map<String, Set<String>> values = new LinkedHashMap<String, Set<String>>();
        for (String fact : facts) {
            values.computeIfAbsent(factUnit(fact), ignored -> new LinkedHashSet<String>()).add(fact);
        }
        return values;
    }

    private boolean opposite(Set<String> left, Set<String> right) {
        return hasAny(left, "上涨", "走强", "增持", "扩产", "提升")
                && hasAny(right, "下跌", "走弱", "回落", "减持", "缩产", "下降")
                || hasAny(right, "上涨", "走强", "增持", "扩产", "提升")
                && hasAny(left, "下跌", "走弱", "回落", "减持", "缩产", "下降");
    }

    private boolean conflictingActions(Set<String> left, Set<String> right) {
        return containsPair(left, right, "增持", "减持") || containsPair(left, right, "加息", "降息");
    }

    private boolean containsPair(Set<String> left, Set<String> right, String first, String second) {
        return left.contains(first) && right.contains(second) || left.contains(second) && right.contains(first);
    }

    private boolean hasAny(Set<String> values, String... candidates) {
        for (String candidate : candidates) if (values.contains(candidate)) return true;
        return false;
    }

    public String eventKey(SignalFeatures features) {
        String subject = first(features.subjects);
        if (subject.isEmpty()) subject = first(features.entities);
        String action = first(features.actions);
        String variable = first(features.variables);
        String fallback = features.normalizedTitle.length() <= 32
                ? features.normalizedTitle : features.normalizedTitle.substring(0, 32);
        // 事件身份由事实标题、主体、动作和变量决定，不能随上游分类结果漂移。
        // 同一条资讯在分类补全前后可能从 UNCLASSIFIED 变成 MARKET_MOVE，分类码不应制造第二个事件。
        return nonBlank(subject, fallback) + ":" + nonBlank(action, "事件") + ":"
                + nonBlank(variable, "信息");
    }

    public String normalize(String value) { return fingerprints.normalizeText(value); }
    public double textSimilarity(String left, String right) { return fingerprints.titleSimilarity(left, right); }

    private Set<String> matches(String text, List<String> candidates) {
        Set<String> values = new LinkedHashSet<String>();
        String normalized = safe(text).toLowerCase(Locale.ROOT);
        for (String candidate : candidates) if (normalized.contains(candidate.toLowerCase(Locale.ROOT))) values.add(candidate);
        return values;
    }

    private Set<String> identifiers(String text) {
        Set<String> values = new LinkedHashSet<String>();
        Matcher matcher = SECURITY_CODE.matcher(safe(text));
        while (matcher.find()) values.add(matcher.group());
        return values;
    }

    private Set<String> numericFacts(String text) {
        Set<String> values = new LinkedHashSet<String>();
        Matcher matcher = NUMERIC_FACT.matcher(safe(text));
        while (matcher.find()) {
            values.add(matcher.group(1) + matcher.group(2).toUpperCase(Locale.ROOT));
        }
        return values;
    }

    private String factUnit(String value) {
        return value == null ? "" : value.replaceFirst("^[0-9.]+", "");
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
        private final Set<String> entities;

        SignalFeatures(String normalizedTitle, String category, Set<String> subjects,
                       Set<String> actions, Set<String> variables, Set<String> entities) {
            this.normalizedTitle = normalizedTitle; this.category = category;
            this.subjects = subjects; this.actions = actions; this.variables = variables; this.entities = entities;
        }

        public String getNormalizedTitle() { return normalizedTitle; }
        public String getCategory() { return category; }
        public Set<String> getSubjects() { return subjects; }
        public Set<String> getActions() { return actions; }
        public Set<String> getVariables() { return variables; }
        public Set<String> getEntities() { return entities; }
    }
}
