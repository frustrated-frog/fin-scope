package com.finscope.service.radar;

import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RadarPriorityService {
    public PriorityResult score(RadarEvent event, List<RadarSignal> signals,
                                List<WatchlistItem> watchlist, LocalDateTime now) {
        PriorityResult legacy = legacyScore(event, signals, watchlist, now);
        return legacy;
    }

    public PriorityResult score(RadarEvent event, List<RadarSignal> signals,
                                List<WatchlistItem> watchlist, LocalDateTime now,
                                int hotnessScore, int confidenceScore) {
        PriorityResult legacy = legacyScore(event, signals, watchlist, now);
        int relevance = relevance(legacy.matchType);
        int total = bounded(relevance * 0.45D + hotnessScore * 0.35D + confidenceScore * 0.20D);
        return new PriorityResult(legacy.noveltyScore, legacy.watchlistScore,
                legacy.sourceDiversityScore, legacy.sourceQualityScore, legacy.recencyScore,
                legacy.reasons, legacy.watchlistExplanation, legacy.uncertainty,
                legacy.nextObservation, relevance, total, legacy.matchType);
    }

    private PriorityResult legacyScore(RadarEvent event, List<RadarSignal> signals,
                                       List<WatchlistItem> watchlist, LocalDateTime now) {
        LocalDateTime firstSeen = event.getFirstSeenAt();
        LocalDateTime lastSeen = latestTime(event, signals);
        int novelty = ageScore(firstSeen, now, 15, 12, 7, 0);
        Match match = findWatchlistMatch(event, signals, watchlist);
        int watchlistScore = match.item == null ? 0 : 25;
        int diversity = sourceDiversity(signals);
        int quality = sourceQuality(signals);
        int recency = recency(lastSeen, now);

        List<String> reasons = new ArrayList<String>();
        String watchlistExplanation;
        if (match.item != null) {
            watchlistExplanation = "与自选「" + match.item.getName() + "」直接相关";
            reasons.add(watchlistExplanation);
        } else {
            watchlistExplanation = "未发现与当前自选标的的直接关系";
        }
        if (diversity >= 15) reasons.add("已有多个独立来源交叉确认");
        else reasons.add("目前独立来源较少");
        if (recency >= 12) reasons.add("事件仍在快速更新");
        else if (novelty >= 12) reasons.add("这是近期出现的新信息");
        while (reasons.size() > 3) reasons.remove(reasons.size() - 1);

        String uncertainty = uncertainty(lastSeen, signals, diversity);
        String nextObservation = diversity < 15 ? "等待第二个独立来源或公司公告确认"
                : "继续观察是否出现数据、公告或价格反馈";
        return new PriorityResult(novelty, watchlistScore, diversity, quality, recency,
                reasons, watchlistExplanation, uncertainty, nextObservation,
                match.item == null ? 0 : 100, novelty + watchlistScore + diversity + quality + recency,
                match.item == null ? "NONE" : match.type);
    }

    private Match findWatchlistMatch(RadarEvent event, List<RadarSignal> signals, List<WatchlistItem> watchlist) {
        StringBuilder text = new StringBuilder(safe(event.getCanonicalTitle())).append(' ').append(safe(event.getSummary()));
        for (RadarSignal signal : safeSignals(signals)) {
            text.append(' ').append(safe(signal.getTitle())).append(' ').append(safe(signal.getContent()));
        }
        String haystack = text.toString().toLowerCase(Locale.ROOT);
        if (watchlist != null) for (WatchlistItem item : watchlist) {
            if (containsCode(haystack, item.getCode())) return new Match(item, "CODE");
            if (containsName(haystack, item.getName())) return new Match(item, "NAME");
        }
        return new Match(null, "NONE");
    }

    private boolean containsCode(String haystack, String value) {
        if (value == null || value.trim().length() < 4) return false;
        String token = value.trim().toLowerCase(Locale.ROOT);
        return haystack.matches("(?s).*(^|[^0-9a-z])" + java.util.regex.Pattern.quote(token) + "([^0-9a-z]|$).*");
    }

    private boolean containsName(String haystack, String value) {
        return value != null && value.trim().length() >= 2
                && haystack.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private int relevance(String matchType) {
        if ("CODE".equals(matchType)) return 100;
        if ("NAME".equals(matchType)) return 95;
        return 0;
    }

    private int bounded(double value) { return Math.max(0, Math.min(100, (int) Math.round(value))); }

    private int sourceDiversity(List<RadarSignal> signals) {
        Set<String> providers = new HashSet<String>();
        for (RadarSignal signal : safeSignals(signals)) {
            String provider = firstNonBlank(signal.getProviderCode(), signal.getSourceName());
            if (!provider.isEmpty()) providers.add(provider.toUpperCase(Locale.ROOT));
        }
        if (providers.size() >= 3) return 20;
        if (providers.size() == 2) return 15;
        if (providers.size() == 1) return 8;
        return 0;
    }

    private int sourceQuality(List<RadarSignal> signals) {
        int best = 0;
        for (RadarSignal signal : safeSignals(signals)) {
            best = Math.max(best, RadarSourceQuality.resolve(signal.getSourceTier()).getPriorityPoints());
        }
        return best;
    }

    private int ageScore(LocalDateTime time, LocalDateTime now, int newest, int recent, int today, int older) {
        if (time == null || now == null) return 0;
        long hours = Math.max(0, Duration.between(time, now).toHours());
        if (hours <= 2) return newest;
        if (hours <= 6) return recent;
        if (hours <= 24) return today;
        return older;
    }

    private int recency(LocalDateTime time, LocalDateTime now) {
        if (time == null || now == null) return 0;
        long hours = Math.max(0, Duration.between(time, now).toHours());
        if (hours <= 1) return 25;
        if (hours <= 3) return 20;
        if (hours <= 6) return 12;
        if (hours <= 12) return 6;
        if (hours <= 24) return 2;
        return 0;
    }

    private LocalDateTime latestTime(RadarEvent event, List<RadarSignal> signals) {
        LocalDateTime latest = null;
        for (RadarSignal signal : safeSignals(signals)) {
            LocalDateTime time = signal.getPublishedAt() == null ? signal.getFirstSeenAt() : signal.getPublishedAt();
            if (time != null && (latest == null || time.isAfter(latest))) latest = time;
        }
        return latest == null && event != null ? event.getFirstSeenAt() : latest;
    }

    private String uncertainty(LocalDateTime lastSeen, List<RadarSignal> signals, int diversity) {
        List<String> gaps = new ArrayList<String>();
        if (lastSeen == null) gaps.add("缺少可靠发布时间");
        if (diversity < 15) gaps.add("尚缺第二个独立来源确认");
        if (signals == null || signals.isEmpty()) gaps.add("缺少原始信号");
        return gaps.isEmpty() ? "暂未发现明显信息缺口" : String.join("；", gaps);
    }

    private List<RadarSignal> safeSignals(List<RadarSignal> signals) {
        return signals == null ? java.util.Collections.<RadarSignal>emptyList() : signals;
    }
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        return second == null ? "" : second.trim();
    }
    private String safe(String value) { return value == null ? "" : value; }

    private static final class Match {
        private final WatchlistItem item;
        private final String type;
        private Match(WatchlistItem item, String type) { this.item = item; this.type = type; }
    }

    public static final class PriorityResult {
        private final int noveltyScore;
        private final int watchlistScore;
        private final int sourceDiversityScore;
        private final int sourceQualityScore;
        private final int recencyScore;
        private final List<String> reasons;
        private final String watchlistExplanation;
        private final String uncertainty;
        private final String nextObservation;
        private final int relevanceScore;
        private final int totalScore;
        private final String matchType;

        PriorityResult(int noveltyScore, int watchlistScore, int sourceDiversityScore,
                       int sourceQualityScore, int recencyScore, List<String> reasons,
                       String watchlistExplanation, String uncertainty, String nextObservation) {
            this(noveltyScore, watchlistScore, sourceDiversityScore, sourceQualityScore, recencyScore,
                    reasons, watchlistExplanation, uncertainty, nextObservation,
                    watchlistScore > 0 ? 100 : 0,
                    noveltyScore + watchlistScore + sourceDiversityScore + sourceQualityScore + recencyScore,
                    watchlistScore > 0 ? "NAME" : "NONE");
        }

        PriorityResult(int noveltyScore, int watchlistScore, int sourceDiversityScore,
                       int sourceQualityScore, int recencyScore, List<String> reasons,
                       String watchlistExplanation, String uncertainty, String nextObservation,
                       int relevanceScore, int totalScore, String matchType) {
            this.noveltyScore = noveltyScore; this.watchlistScore = watchlistScore;
            this.sourceDiversityScore = sourceDiversityScore; this.sourceQualityScore = sourceQualityScore;
            this.recencyScore = recencyScore; this.reasons = new ArrayList<String>(reasons);
            this.watchlistExplanation = watchlistExplanation; this.uncertainty = uncertainty;
            this.nextObservation = nextObservation;
            this.relevanceScore = relevanceScore;
            this.totalScore = totalScore;
            this.matchType = matchType;
        }
        public int getNoveltyScore() { return noveltyScore; }
        public int getWatchlistScore() { return watchlistScore; }
        public int getSourceDiversityScore() { return sourceDiversityScore; }
        public int getSourceQualityScore() { return sourceQualityScore; }
        public int getRecencyScore() { return recencyScore; }
        public int getTotalScore() { return totalScore; }
        public int componentTotal() { return noveltyScore + watchlistScore + sourceDiversityScore + sourceQualityScore + recencyScore; }
        public List<String> getReasons() { return new ArrayList<String>(reasons); }
        public String getWatchlistExplanation() { return watchlistExplanation; }
        public String getUncertainty() { return uncertainty; }
        public String getNextObservation() { return nextObservation; }
        public int getRelevanceScore() { return relevanceScore; }
    }
}
