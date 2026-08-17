package com.finscope.service.globalexpectations;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationRadarMatch;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 只匹配 FinScope 已采集的近期 Radar 事件，不触发外部搜索。 */
@Component
public class GlobalExpectationRadarMatcher {
    private static final int MAX_MATCHES = 3;

    @Resource
    private RadarRepository radarRepository;

    public void attachRecent(List<GlobalExpectationEventGroup> groups) {
        attachRecent(groups, LocalDateTime.now());
    }

    void attachRecent(List<GlobalExpectationEventGroup> groups, LocalDateTime now) {
        try {
            attach(groups, radarRepository.findEventsSince(now.minusDays(3), 300));
            enrichStatistics(groups, now);
        } catch (RuntimeException ignored) {
            for (GlobalExpectationEventGroup group : groups) {
                group.setRadarMatches(List.of());
                group.setRealityDataStatus("FAILED");
            }
        }
    }

    void attach(List<GlobalExpectationEventGroup> groups, List<RadarEvent> events) {
        for (GlobalExpectationEventGroup group : groups) {
            List<GlobalExpectationRadarMatch> matches = new ArrayList<GlobalExpectationRadarMatch>();
            Set<String> sourceTokens = tokens(group.getTitle());
            for (RadarEvent event : events) {
                int overlap = overlap(sourceTokens, tokens(text(event)));
                if (overlap < 3) {
                    continue;
                }
                matches.add(match(event, Math.min(100, overlap * 12)));
            }
            matches.sort((left, right) -> Integer.compare(right.getMatchScore(), left.getMatchScore()));
            group.setRadarMatches(matches.size() <= MAX_MATCHES
                    ? matches : new ArrayList<GlobalExpectationRadarMatch>(matches.subList(0, MAX_MATCHES)));
            group.setRealityDataStatus("READY");
        }
    }

    private void enrichStatistics(List<GlobalExpectationEventGroup> groups, LocalDateTime now) {
        for (GlobalExpectationEventGroup group : groups) {
            for (GlobalExpectationRadarMatch match : group.getRadarMatches()) {
                summarize(match, radarRepository.findSignalsByEventId(match.getEventId()), now);
            }
        }
    }

    private void summarize(GlobalExpectationRadarMatch match, List<RadarSignal> signals, LocalDateTime now) {
        int newsCount1h = 0;
        int newsCountPrevious1h = 0;
        int newsCount24h = 0;
        Set<String> sources = new HashSet<String>();
        LocalDateTime lastSeenAt = null;
        for (RadarSignal signal : signals) {
            LocalDateTime observedAt = observedAt(signal);
            if (observedAt == null || observedAt.isAfter(now)) {
                continue;
            }
            if (!observedAt.isBefore(now.minusHours(24))) {
                newsCount24h++;
                String source = sourceKey(signal);
                if (!source.isEmpty()) {
                    sources.add(source);
                }
            }
            if (!observedAt.isBefore(now.minusHours(1))) {
                newsCount1h++;
            } else if (!observedAt.isBefore(now.minusHours(2))) {
                newsCountPrevious1h++;
            }
            if (lastSeenAt == null || observedAt.isAfter(lastSeenAt)) {
                lastSeenAt = observedAt;
            }
        }
        match.setNewsCount1h(newsCount1h);
        match.setNewsCountPrevious1h(newsCountPrevious1h);
        match.setNewsCount24h(newsCount24h);
        match.setIndependentSourceCount(sources.size());
        match.setLastSeenAt(lastSeenAt);
    }

    private LocalDateTime observedAt(RadarSignal signal) {
        if (signal.getPublishedAt() != null) {
            return signal.getPublishedAt();
        }
        if (signal.getFirstSeenAt() != null) {
            return signal.getFirstSeenAt();
        }
        return signal.getLastSeenAt();
    }

    private String sourceKey(RadarSignal signal) {
        if (signal.getSourceName() != null && !signal.getSourceName().trim().isEmpty()) {
            return signal.getSourceName().trim().toLowerCase(Locale.ROOT);
        }
        if (signal.getProviderCode() != null && !signal.getProviderCode().trim().isEmpty()) {
            return signal.getProviderCode().trim().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private GlobalExpectationRadarMatch match(RadarEvent event, int score) {
        GlobalExpectationRadarMatch match = new GlobalExpectationRadarMatch();
        match.setEventId(event.getId());
        match.setTitle(event.getCanonicalTitle());
        match.setSummary(event.getSummary());
        match.setMatchScore(score);
        return match;
    }

    private int overlap(Set<String> left, Set<String> right) {
        int count = 0;
        for (String token : left) {
            if (right.contains(token)) {
                count++;
            }
        }
        return count;
    }

    private Set<String> tokens(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}a-z0-9]+", " ").trim();
        Set<String> result = new HashSet<String>();
        for (String part : normalized.split(" +")) {
            if (part.matches(".*\\p{IsHan}.*")) {
                for (int index = 0; index + 1 < part.length(); index++) {
                    result.add(part.substring(index, index + 2));
                }
            } else if (part.length() >= 3) {
                result.add(part);
            }
        }
        return result;
    }

    private String text(RadarEvent event) {
        return (event.getCanonicalTitle() == null ? "" : event.getCanonicalTitle()) + " "
                + (event.getSummary() == null ? "" : event.getSummary());
    }
}
