package com.finscope.service.research;

import com.finscope.common.util.StringUtils;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.service.dedupe.FingerprintService;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class EventClassifier {

    private static final Pattern NUMBER_PATTERN = Pattern.compile(".*(\\d|%|％|亿美元|亿元|万亿|million|billion|trillion).*",
            Pattern.CASE_INSENSITIVE);

    @Resource
    private FingerprintService fingerprintService;

    public EventSignature signature(Article article) {
        EventSignal signal = signal(article);
        String eventKey = signal.getThemeCode() + ":" + String.join(":", keyTerms(signal));
        int importance = importance(signal.getText(), signal.getThemeCode());
        return new EventSignature(signal.getThemeCode(), eventKey, importance);
    }

    public MatchDecision decide(Article article, EventSignature signature, List<EventCluster> candidates) {
        EventCluster best = null;
        double bestScore = 0.0;
        MatchEvidence bestEvidence = null;
        EventSignal articleSignal = signal(article);
        for (EventCluster candidate : candidates) {
            MatchEvidence evidence = matchEvidence(article, signature, articleSignal, candidate);
            if (evidence.getScore() > bestScore) {
                bestScore = evidence.getScore();
                best = candidate;
                bestEvidence = evidence;
            }
        }
        if (best == null || bestScore < 0.60) {
            return new MatchDecision(null, 0.0, ResearchEnums.NOVELTY_NEW, "首次进入事件研究台");
        }
        if (hasNewVariable(articleSignal, bestEvidence.getCandidateSignal())) {
            return new MatchDecision(best, bestScore, ResearchEnums.NOVELTY_FOLLOW_UP,
                    noveltyReason(bestEvidence, true));
        }
        return new MatchDecision(best, bestScore, ResearchEnums.NOVELTY_RECAP,
                noveltyReason(bestEvidence, false));
    }

    private MatchEvidence matchEvidence(Article article,
                                        EventSignature signature,
                                        EventSignal articleSignal,
                                        EventCluster candidate) {
        EventSignal candidateSignal = signal(candidate);
        if (!signature.getThemeCode().equals(candidate.getThemeCode())) {
            return new MatchEvidence(0.0, articleSignal, candidateSignal,
                    emptySet(), emptySet(), emptySet());
        }
        Set<String> sharedSubjects = intersection(articleSignal.getSubjects(), candidateSignal.getSubjects());
        Set<String> sharedVariables = intersection(articleSignal.getVariables(), candidateSignal.getVariables());
        Set<String> sharedActions = intersection(articleSignal.getActions(), candidateSignal.getActions());
        if (hasConflictingPrimarySubjects(articleSignal, candidateSignal, sharedSubjects)) {
            return new MatchEvidence(0.0, articleSignal, candidateSignal,
                    sharedSubjects, sharedVariables, sharedActions);
        }
        if (requiresSubjectOverlap(articleSignal.getThemeCode())
                && !articleSignal.getSubjects().isEmpty()
                && !candidateSignal.getSubjects().isEmpty()
                && sharedSubjects.isEmpty()) {
            return new MatchEvidence(0.0, articleSignal, candidateSignal,
                    sharedSubjects, sharedVariables, sharedActions);
        }
        if (sharedSubjects.isEmpty() && sharedVariables.isEmpty() && sharedActions.isEmpty()) {
            return new MatchEvidence(0.0, articleSignal, candidateSignal,
                    sharedSubjects, sharedVariables, sharedActions);
        }
        boolean sameKey = signature.getCanonicalEventKey().equals(candidate.getCanonicalEventKey());
        double keyScore = sameKey ? 1.0 : 0.0;
        double titleScore = fingerprintService.titleSimilarity(article.getTitle(), candidate.getCanonicalTitle());
        double contextScore = fingerprintService.titleSimilarity(
                StringUtils.firstNonBlank(article.getSummary(), article.getBody(), article.getTitle()),
                StringUtils.firstNonBlank(candidate.getSummary(), candidate.getCanonicalTitle(), ""));
        double textScore = 0.60 * titleScore + 0.40 * contextScore;
        double score = 0.15 * keyScore
                + 0.25
                + (sharedSubjects.isEmpty() ? 0.0 : 0.25)
                + (sharedVariables.isEmpty() ? 0.0 : 0.20)
                + (sharedActions.isEmpty() ? 0.0 : 0.10)
                + 0.15 * textScore;
        if (sameKey) {
            score = Math.max(score, 0.76);
        }
        return new MatchEvidence(Math.min(score, 1.0), articleSignal, candidateSignal,
                sharedSubjects, sharedVariables, sharedActions);
    }

    private String resolveTheme(Article article, String text) {
        String category = StringUtils.firstNonBlank(article.getCategory(), "");
        if ("宏观".equals(category) || containsAny(text, "美联储", "fed", "央行", "pce", "cpi", "通胀", "降息", "加息", "利率")) {
            return ResearchEnums.THEME_CHINA_MACRO;
        }
        if ("公司".equals(category) || containsAny(text, "ipo", "招股", "财报", "营收", "利润", "guidance", "sec", "hkex")) {
            return ResearchEnums.THEME_COMPANY_IPO;
        }
        if (containsAny(text, "openai", "anthropic", "google", "github", "ai", "模型", "agent", "runway", "创业")) {
            return ResearchEnums.THEME_AI_STARTUP;
        }
        return ResearchEnums.THEME_MARKET;
    }

    private List<String> keyTerms(EventSignal signal) {
        Set<String> terms = new LinkedHashSet<String>();
        terms.addAll(signal.getSubjects());
        terms.addAll(signal.getVariables());
        terms.addAll(signal.getActions());
        if (terms.isEmpty()) {
            terms.addAll(fallbackTokens(signal.getText()));
        }
        return new ArrayList<String>(terms).stream().limit(6).collect(Collectors.toList());
    }

    private List<String> fallbackTokens(String text) {
        List<String> tokens = new ArrayList<String>();
        for (String token : text.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.length() >= 2 && tokens.size() < 4) {
                tokens.add(normalized);
            }
        }
        if (tokens.isEmpty()) {
            tokens.add("general");
        }
        return tokens;
    }

    private boolean hasNewVariable(EventSignal articleSignal, EventSignal candidateSignal) {
        if (!articleSignal.getDataPoints().isEmpty()) {
            return true;
        }
        if (!difference(articleSignal.getVariables(), candidateSignal.getVariables()).isEmpty()) {
            return true;
        }
        return !difference(articleSignal.getActions(), candidateSignal.getActions()).isEmpty()
                && containsAny(articleSignal.getText(), "提交", "批准", "通过", "聆讯", "发布", "完成", "上调", "下调", "市场反应");
    }

    private int importance(String text, String themeCode) {
        int score = ResearchEnums.THEME_MARKET.equals(themeCode) ? 55 : 70;
        if (containsAny(text, "官方", "公告", "美联储", "央行", "sec", "hkex", "财报", "ipo")) {
            score += 10;
        }
        if (NUMBER_PATTERN.matcher(text).matches()) {
            score += 8;
        }
        return Math.min(score, 95);
    }

    private boolean containsAny(String text, String... keywords) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return Arrays.stream(keywords).anyMatch(keyword -> value.contains(keyword.toLowerCase(Locale.ROOT)));
    }

    private void addIf(Set<String> terms, String term, boolean condition) {
        if (condition) {
            terms.add(term);
        }
    }

    private EventSignal signal(Article article) {
        String text = searchable(article);
        String themeCode = resolveTheme(article, text);
        return signal(text, themeCode);
    }

    private EventSignal signal(EventCluster event) {
        String text = (StringUtils.firstNonBlank(event.getCanonicalTitle(), "") + " "
                + StringUtils.firstNonBlank(event.getSummary(), "") + " "
                + StringUtils.firstNonBlank(event.getCanonicalEventKey(), "")).toLowerCase(Locale.ROOT);
        return signal(text, event.getThemeCode());
    }

    private EventSignal signal(String text, String themeCode) {
        Set<String> subjects = new LinkedHashSet<String>();
        Set<String> variables = new LinkedHashSet<String>();
        Set<String> actions = new LinkedHashSet<String>();
        Set<String> dataPoints = dataPoints(text);

        if (ResearchEnums.THEME_CHINA_MACRO.equals(themeCode)) {
            addIf(subjects, "fed", containsAny(text, "美联储", "fed", "fomc"));
            addIf(subjects, "pboc", containsAny(text, "央行", "pbo c", "pboc", "人民银行", "中国人民银行"));
            addIf(variables, "rate", containsAny(text, "降息", "加息", "利率", "lpr", "rate"));
            addIf(variables, "gold", containsAny(text, "黄金", "gold", "贵金属"));
            addIf(variables, "inflation", containsAny(text, "通胀", "cpi", "pce"));
            addIf(variables, "liquidity", containsAny(text, "mlf", "逆回购", "中期借贷便利", "流动性", "公开市场"));
            addIf(variables, "etf_flow", containsAny(text, "etf", "流入", "流出"));
            addIf(actions, "rate_cut", containsAny(text, "降息", "下调利率", "利率下调", "下调10个基点"));
            addIf(actions, "rate_hike", containsAny(text, "加息", "上调利率", "利率上调"));
            addIf(actions, "liquidity_operation", containsAny(text, "开展", "mlf", "逆回购", "投放"));
        } else if (ResearchEnums.THEME_COMPANY_IPO.equals(themeCode)) {
            subjects.addAll(companySubjects(text));
            addIf(variables, "ipo", containsAny(text, "ipo", "招股", "上市", "聆讯"));
            addIf(variables, "earnings", containsAny(text, "财报", "营收", "利润", "指引"));
            addIf(actions, "ipo", containsAny(text, "ipo", "招股", "上市", "聆讯"));
            addIf(actions, "ipo_filing", containsAny(text, "提交", "递交", "申请"));
            addIf(actions, "ipo_hearing", containsAny(text, "聆讯", "通过聆讯"));
            addIf(actions, "earnings", containsAny(text, "财报", "营收", "利润", "指引"));
        } else if (ResearchEnums.THEME_AI_STARTUP.equals(themeCode)) {
            subjects.addAll(aiSubjects(text));
            addIf(variables, "agent", containsAny(text, "agent", "智能体"));
            addIf(variables, "model", containsAny(text, "模型", "model", "llm"));
            addIf(variables, "funding", containsAny(text, "融资", "投资方", "估值"));
            addIf(actions, "product_launch", containsAny(text, "发布", "推出", "上线", "release", "launch"));
            addIf(actions, "funding", containsAny(text, "融资", "投资方", "估值"));
        } else {
            addIf(variables, "market_reaction", containsAny(text, "市场反应", "上涨", "下跌", "新高", "回落"));
            addIf(actions, "market_move", containsAny(text, "上涨", "下跌", "新高", "回落"));
        }

        return new EventSignal(themeCode, text, subjects, variables, actions, dataPoints);
    }

    private Set<String> companySubjects(String text) {
        Set<String> subjects = new LinkedHashSet<String>();
        addKnownSubject(subjects, text, "宁德时代", "catl");
        addKnownSubject(subjects, text, "比亚迪", "byd");
        addKnownSubject(subjects, text, "阿里巴巴", "alibaba");
        addKnownSubject(subjects, text, "腾讯", "tencent");
        addKnownSubject(subjects, text, "百度", "baidu");
        addKnownSubject(subjects, text, "京东", "jd");
        addKnownSubject(subjects, text, "小米", "xiaomi");
        String extracted = subjectBeforeMarker(text);
        if (!StringUtils.isBlank(extracted)) {
            subjects.add(extracted);
        }
        return subjects;
    }

    private Set<String> aiSubjects(String text) {
        Set<String> subjects = new LinkedHashSet<String>();
        addKnownSubject(subjects, text, "openai", "openai");
        addKnownSubject(subjects, text, "anthropic", "anthropic");
        addKnownSubject(subjects, text, "google", "google");
        addKnownSubject(subjects, text, "github", "github");
        addKnownSubject(subjects, text, "cloudflare", "cloudflare");
        addKnownSubject(subjects, text, "runway", "runway");
        return subjects;
    }

    private void addKnownSubject(Set<String> subjects, String text, String keyword, String normalized) {
        if (containsAny(text, keyword)) {
            subjects.add(normalized);
        }
    }

    private String subjectBeforeMarker(String text) {
        String value = text == null ? "" : text;
        String[] markers = {"提交", "递交", "通过", "发布", "宣布", "完成", "考虑", "计划", "申请", "ipo", "上市", "财报", "融资"};
        int markerIndex = -1;
        for (String marker : markers) {
            int index = value.indexOf(marker);
            if (index > 0 && (markerIndex < 0 || index < markerIndex)) {
                markerIndex = index;
            }
        }
        if (markerIndex <= 0) {
            return "";
        }
        String prefix = value.substring(0, markerIndex)
                .replaceAll("(港股|a股|美股|科创板|创业板|主板|赴港|在港|正式|正在|考虑|其)$", "")
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "")
                .trim();
        if (prefix.length() < 2 || prefix.length() > 12) {
            return "";
        }
        return prefix.toLowerCase(Locale.ROOT);
    }

    private Set<String> dataPoints(String text) {
        Set<String> points = new LinkedHashSet<String>();
        if (text == null) {
            return points;
        }
        java.util.regex.Matcher matcher = Pattern.compile("\\d+(?:\\.\\d+)?(?:%|％|亿美元|亿元|万亿|个基点|bp|bps)?",
                Pattern.CASE_INSENSITIVE).matcher(text);
        while (matcher.find() && points.size() < 3) {
            points.add(matcher.group());
        }
        return points;
    }

    private boolean hasConflictingPrimarySubjects(EventSignal articleSignal,
                                                 EventSignal candidateSignal,
                                                 Set<String> sharedSubjects) {
        if (!ResearchEnums.THEME_CHINA_MACRO.equals(articleSignal.getThemeCode())) {
            return false;
        }
        Set<String> centralBanks = new LinkedHashSet<String>(Arrays.asList("fed", "pboc"));
        Set<String> articleCentralBanks = intersection(articleSignal.getSubjects(), centralBanks);
        Set<String> candidateCentralBanks = intersection(candidateSignal.getSubjects(), centralBanks);
        return !articleCentralBanks.isEmpty() && !candidateCentralBanks.isEmpty() && sharedSubjects.isEmpty();
    }

    private boolean requiresSubjectOverlap(String themeCode) {
        return ResearchEnums.THEME_COMPANY_IPO.equals(themeCode) || ResearchEnums.THEME_AI_STARTUP.equals(themeCode);
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<String>();
        for (String item : left) {
            if (right.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<String>();
        for (String item : left) {
            if (!right.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private Set<String> emptySet() {
        return new LinkedHashSet<String>();
    }

    private String noveltyReason(MatchEvidence evidence, boolean meaningfulUpdate) {
        List<String> parts = new ArrayList<String>();
        if (!evidence.getSharedSubjects().isEmpty()) {
            parts.add("同主体：" + labels(evidence.getSharedSubjects()));
        }
        if (!evidence.getSharedVariables().isEmpty()) {
            parts.add("同变量：" + labels(evidence.getSharedVariables()));
        }
        if (!evidence.getSharedActions().isEmpty()) {
            parts.add("同动作：" + labels(evidence.getSharedActions()));
        }
        if (meaningfulUpdate) {
            Set<String> addedVariables = difference(evidence.getArticleSignal().getVariables(),
                    evidence.getCandidateSignal().getVariables());
            Set<String> addedActions = difference(evidence.getArticleSignal().getActions(),
                    evidence.getCandidateSignal().getActions());
            String added = !evidence.getArticleSignal().getDataPoints().isEmpty()
                    ? String.join("、", evidence.getArticleSignal().getDataPoints())
                    : labels(!addedVariables.isEmpty() ? addedVariables : addedActions);
            parts.add("新增变量：" + StringUtils.firstNonBlank(added, "新的数据、时间线或市场反应"));
        } else {
            parts.add("未发现明显新增变量");
        }
        return String.join("；", parts);
    }

    private String labels(Set<String> values) {
        List<String> labels = new ArrayList<String>();
        for (String value : values) {
            labels.add(label(value));
        }
        return String.join("、", labels);
    }

    private String label(String value) {
        if ("fed".equals(value)) {
            return "美联储";
        }
        if ("pboc".equals(value)) {
            return "中国人民银行";
        }
        if ("rate".equals(value)) {
            return "利率/降息";
        }
        if ("gold".equals(value)) {
            return "黄金";
        }
        if ("inflation".equals(value)) {
            return "通胀";
        }
        if ("liquidity".equals(value)) {
            return "流动性工具";
        }
        if ("etf_flow".equals(value)) {
            return "ETF资金流";
        }
        if ("ipo".equals(value)) {
            return "IPO/上市";
        }
        if ("ipo_filing".equals(value)) {
            return "递表";
        }
        if ("ipo_hearing".equals(value)) {
            return "聆讯";
        }
        if ("product_launch".equals(value)) {
            return "产品发布";
        }
        if ("funding".equals(value)) {
            return "融资";
        }
        if ("agent".equals(value)) {
            return "Agent";
        }
        if ("model".equals(value)) {
            return "模型";
        }
        return value;
    }

    private String searchable(Article article) {
        return (StringUtils.firstNonBlank(article.getTitle(), "") + " "
                + StringUtils.firstNonBlank(article.getSummary(), "") + " "
                + StringUtils.firstNonBlank(article.getBody(), "")).toLowerCase(Locale.ROOT);
    }

    @Data
    private static class EventSignal {
        private final String themeCode;
        private final String text;
        private final Set<String> subjects;
        private final Set<String> variables;
        private final Set<String> actions;
        private final Set<String> dataPoints;
    }

    @Data
    private static class MatchEvidence {
        private final double score;
        private final EventSignal articleSignal;
        private final EventSignal candidateSignal;
        private final Set<String> sharedSubjects;
        private final Set<String> sharedVariables;
        private final Set<String> sharedActions;
    }

    @Data
    public static class EventSignature {
        private final String themeCode;
        private final String canonicalEventKey;
        private final int importanceScore;
    }

    public static class MatchDecision {
        private final EventCluster event;
        private final double matchScore;
        private final String noveltyType;
        private final String noveltyReason;

        public MatchDecision(EventCluster event, double matchScore, String noveltyType, String noveltyReason) {
            this.event = event;
            this.matchScore = matchScore;
            this.noveltyType = noveltyType;
            this.noveltyReason = noveltyReason;
        }

        public EventCluster getEvent() {
            return event;
        }

        public double getMatchScore() {
            return matchScore;
        }

        public String getNoveltyType() {
            return noveltyType;
        }

        public String getNoveltyReason() {
            return noveltyReason;
        }
    }

    public static void main(String[] args) {
        String appId = "jdt-a2ui-manage";
        String appSecret = "fac5a933-b976-4130-92f4-6509462da7f6";
        Long timestamp = System.currentTimeMillis();

        String plainText = appId + timestamp + appSecret;

        String signature = DigestUtils.md5DigestAsHex(plainText.getBytes());
        System.out.println(signature + " " + timestamp);
    }
}
