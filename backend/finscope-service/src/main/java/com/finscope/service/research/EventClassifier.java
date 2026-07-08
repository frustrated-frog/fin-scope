package com.finscope.service.research;

import com.finscope.common.util.StringUtils;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.service.dedupe.FingerprintService;
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

@Component
public class EventClassifier {

    private static final Pattern NUMBER_PATTERN = Pattern.compile(".*(\\d|%|％|亿美元|亿元|万亿|million|billion|trillion).*",
            Pattern.CASE_INSENSITIVE);

    @Resource
    private FingerprintService fingerprintService;

    public EventSignature signature(Article article) {
        String text = searchable(article);
        String themeCode = resolveTheme(article, text);
        String eventKey = themeCode + ":" + String.join(":", keyTerms(text, themeCode));
        int importance = importance(text, themeCode);
        return new EventSignature(themeCode, eventKey, importance);
    }

    public MatchDecision decide(Article article, EventSignature signature, List<EventCluster> candidates) {
        EventCluster best = null;
        double bestScore = 0.0;
        for (EventCluster candidate : candidates) {
            double score = matchScore(article, signature, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null || bestScore < 0.60) {
            return new MatchDecision(null, 0.0, ResearchEnums.NOVELTY_NEW, "首次进入事件记忆");
        }
        if (hasNewVariable(article)) {
            return new MatchDecision(best, bestScore, ResearchEnums.NOVELTY_FOLLOW_UP, "命中历史事件，包含新的数据、时间线或市场反应");
        }
        return new MatchDecision(best, bestScore, ResearchEnums.NOVELTY_RECAP, "命中历史事件，正文没有明显新增变量");
    }

    private double matchScore(Article article, EventSignature signature, EventCluster candidate) {
        boolean sameKey = signature.getCanonicalEventKey().equals(candidate.getCanonicalEventKey());
        double keyScore = sameKey ? 1.0 : 0.0;
        double titleScore = fingerprintService.titleSimilarity(article.getTitle(), candidate.getCanonicalTitle());
        double contextScore = fingerprintService.titleSimilarity(
                StringUtils.firstNonBlank(article.getSummary(), article.getBody(), article.getTitle()),
                StringUtils.firstNonBlank(candidate.getSummary(), candidate.getCanonicalTitle(), ""));
        double score = 0.45 * keyScore + 0.30 * titleScore + 0.25 * contextScore;
        if (sameKey) {
            return Math.max(score, 0.76);
        }
        return score;
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

    private List<String> keyTerms(String text, String themeCode) {
        Set<String> terms = new LinkedHashSet<String>();
        if (ResearchEnums.THEME_CHINA_MACRO.equals(themeCode)) {
            addIf(terms, "fed", containsAny(text, "美联储", "fed"));
            addIf(terms, "rate", containsAny(text, "降息", "加息", "利率"));
            addIf(terms, "gold", containsAny(text, "黄金", "gold", "贵金属"));
            addIf(terms, "inflation", containsAny(text, "通胀", "cpi", "pce"));
            addIf(terms, "pboc", containsAny(text, "央行", "pbo c", "pboc"));
        } else if (ResearchEnums.THEME_COMPANY_IPO.equals(themeCode)) {
            addIf(terms, "ipo", containsAny(text, "ipo", "招股", "上市", "聆讯"));
            addIf(terms, "earnings", containsAny(text, "财报", "营收", "利润", "指引"));
            addIf(terms, "company", true);
        } else if (ResearchEnums.THEME_AI_STARTUP.equals(themeCode)) {
            addIf(terms, "ai", containsAny(text, "ai", "人工智能", "模型"));
            addIf(terms, "product", containsAny(text, "发布", "release", "产品", "github"));
            addIf(terms, "startup", containsAny(text, "创业", "融资", "公司"));
        }
        if (terms.isEmpty()) {
            terms.addAll(fallbackTokens(text));
        }
        return new ArrayList<String>(terms);
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

    private boolean hasNewVariable(Article article) {
        String text = searchable(article);
        return NUMBER_PATTERN.matcher(text).matches()
                || containsAny(text, "单周", "新高", "上调", "下调", "提交", "批准", "流入", "流出", "指引", "市场反应");
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

    private String searchable(Article article) {
        return (StringUtils.firstNonBlank(article.getTitle(), "") + " "
                + StringUtils.firstNonBlank(article.getSummary(), "") + " "
                + StringUtils.firstNonBlank(article.getBody(), "")).toLowerCase(Locale.ROOT);
    }

    public static class EventSignature {
        private final String themeCode;
        private final String canonicalEventKey;
        private final int importanceScore;

        public EventSignature(String themeCode, String canonicalEventKey, int importanceScore) {
            this.themeCode = themeCode;
            this.canonicalEventKey = canonicalEventKey;
            this.importanceScore = importanceScore;
        }

        public String getThemeCode() {
            return themeCode;
        }

        public String getCanonicalEventKey() {
            return canonicalEventKey;
        }

        public int getImportanceScore() {
            return importanceScore;
        }
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
