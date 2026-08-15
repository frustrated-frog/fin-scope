package com.finscope.service.globalexpectations;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationRadarMatch;
import com.finscope.domain.radar.RadarEvent;
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
        try {
            attach(groups, radarRepository.findEventsSince(LocalDateTime.now().minusDays(3), 300));
        } catch (RuntimeException ignored) {
            for (GlobalExpectationEventGroup group : groups) {
                group.setRadarMatches(List.of());
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
        }
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
