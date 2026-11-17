package com.finscope.domain.radar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 公共与个人榜单共用基础分与稳定次序；主题重复只做有界降权，不丢弃报道或事件。 */
public final class NewswireRankingPolicy {
    private static final int DIVERSIFIED_PREFIX = 50;
    private static final int REPEATED_SUBJECT_PENALTY = 12;

    private NewswireRankingPolicy() { }

    public static int publicScore(RadarEvent event) {
        return event.getNewsworthiness() == null ? event.getHotspotScore() : event.getNewsworthiness().getPublicScore();
    }

    public static List<RadarEvent> rank(List<RadarEvent> events, boolean personal) {
        Comparator<RadarEvent> order = Comparator.comparingInt((RadarEvent event) -> score(event, personal)).reversed()
                .thenComparing(Comparator.comparingInt(NewswireRankingPolicy::publicScore).reversed())
                .thenComparing(NewswireRankingPolicy::meaningfulTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RadarEvent::getId, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RadarEvent::getEventKey, Comparator.nullsLast(Comparator.naturalOrder()));
        List<RadarEvent> remaining = new ArrayList<>(events);
        remaining.sort(order);
        Map<RadarEvent, String> diversityKeys = resolveDiversityKeys(remaining);
        List<RadarEvent> selected = new ArrayList<>();
        Map<String, Integer> subjects = new HashMap<>();
        while (!remaining.isEmpty() && selected.size() < DIVERSIFIED_PREFIX) {
            int best = 0;
            int highest = Integer.MIN_VALUE;
            for (int index = 0; index < remaining.size(); index++) {
                RadarEvent event = remaining.get(index);
                int penalty = Math.min(36, subjects.getOrDefault(diversityKeys.get(event), 0) * REPEATED_SUBJECT_PENALTY);
                int adjusted = score(event, personal) - penalty;
                if (adjusted > highest) {
                    best = index;
                    highest = adjusted;
                }
            }
            RadarEvent next = remaining.remove(best);
            selected.add(next);
            subjects.merge(diversityKeys.get(next), 1, Integer::sum);
        }
        selected.addAll(remaining);
        return selected;
    }

    private static Map<RadarEvent, String> resolveDiversityKeys(List<RadarEvent> events) {
        // 从本批次已知主体复用别名，覆盖“上交所审议通过某公司收购”这类非公司前缀标题。
        List<String> aliases = events.stream().map(NewswireRankingPolicy::diversityKey)
                .filter(key -> key.startsWith("subject:")).map(key -> key.substring(8))
                .filter(key -> key.length() >= 2).distinct()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder())).toList();
        Map<RadarEvent, String> result = new java.util.IdentityHashMap<>();
        for (RadarEvent event : events) {
            String key = diversityKey(event);
            if (key.startsWith("event:") && event.getCanonicalTitle() != null) {
                for (String alias : aliases) {
                    if (event.getCanonicalTitle().contains(alias)) {
                        key = "subject:" + alias;
                        break;
                    }
                }
            }
            result.put(event, key);
        }
        return result;
    }

    private static int score(RadarEvent event, boolean personal) {
        return personal ? event.getPriorityScore() : publicScore(event);
    }

    private static String diversityKey(RadarEvent event) {
        if (event.getNewsworthiness() != null && event.getNewsworthiness().getDiversityKey() != null) {
            return event.getNewsworthiness().getDiversityKey();
        }
        return "event:" + event.getEventKey() + ":" + event.getId();
    }

    private static java.time.LocalDateTime meaningfulTime(RadarEvent event) {
        return event.getNewsworthiness() == null ? event.getLastSeenAt() : event.getNewsworthiness().getMeaningfulChangeAt();
    }
}
