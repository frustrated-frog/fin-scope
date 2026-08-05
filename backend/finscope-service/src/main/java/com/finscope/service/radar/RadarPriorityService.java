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
        LocalDateTime firstSeen = event.getFirstSeenAt();
        LocalDateTime lastSeen = latestTime(event, signals);
        int novelty = ageScore(firstSeen, now, 25, 20, 12, 5);
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
                reasons, watchlistExplanation, uncertainty, nextObservation);
    }

    private Match findWatchlistMatch(RadarEvent event, List<RadarSignal> signals, List<WatchlistItem> watchlist) {
        StringBuilder text = new StringBuilder(safe(event.getCanonicalTitle())).append(' ').append(safe(event.getSummary()));
        for (RadarSignal signal : safeSignals(signals)) {
            text.append(' ').append(safe(signal.getTitle())).append(' ').append(safe(signal.getContent()));
        }
        String haystack = text.toString().toLowerCase(Locale.ROOT);
        if (watchlist != null) for (WatchlistItem item : watchlist) {
            if (containsToken(haystack, item.getName()) || containsToken(haystack, item.getCode())) return new Match(item);
        }
        return new Match(null);
    }

    private boolean containsToken(String haystack, String value) {
        return value != null && !value.trim().isEmpty() && haystack.contains(value.trim().toLowerCase(Locale.ROOT));
    }

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
        if (hours <= 1) return 15;
        if (hours <= 3) return 12;
        if (hours <= 8) return 8;
        if (hours <= 24) return 4;
        return 0;
    }

    private LocalDateTime latestTime(RadarEvent event, List<RadarSignal> signals) {
        LocalDateTime latest = event.getLastSeenAt();
        for (RadarSignal signal : safeSignals(signals)) {
            LocalDateTime time = signal.getPublishedAt() == null ? signal.getLastSeenAt() : signal.getPublishedAt();
            if (time != null && (latest == null || time.isAfter(latest))) latest = time;
        }
        return latest;
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
        private Match(WatchlistItem item) { this.item = item; }
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

        PriorityResult(int noveltyScore, int watchlistScore, int sourceDiversityScore,
                       int sourceQualityScore, int recencyScore, List<String> reasons,
                       String watchlistExplanation, String uncertainty, String nextObservation) {
            this.noveltyScore = noveltyScore; this.watchlistScore = watchlistScore;
            this.sourceDiversityScore = sourceDiversityScore; this.sourceQualityScore = sourceQualityScore;
            this.recencyScore = recencyScore; this.reasons = new ArrayList<String>(reasons);
            this.watchlistExplanation = watchlistExplanation; this.uncertainty = uncertainty;
            this.nextObservation = nextObservation;
        }
        public int getNoveltyScore() { return noveltyScore; }
        public int getWatchlistScore() { return watchlistScore; }
        public int getSourceDiversityScore() { return sourceDiversityScore; }
        public int getSourceQualityScore() { return sourceQualityScore; }
        public int getRecencyScore() { return recencyScore; }
        public int getTotalScore() { return componentTotal(); }
        public int componentTotal() { return noveltyScore + watchlistScore + sourceDiversityScore + sourceQualityScore + recencyScore; }
        public List<String> getReasons() { return new ArrayList<String>(reasons); }
        public String getWatchlistExplanation() { return watchlistExplanation; }
        public String getUncertainty() { return uncertainty; }
        public String getNextObservation() { return nextObservation; }
    }
}
