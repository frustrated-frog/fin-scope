package com.finscope.service.radar;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 快照与即时查询共用处理状态语义，统计先于展示截断。 */
final class RadarEventCardQuery {
    private RadarEventCardQuery() { }

    static boolean matches(ResearchRadarView.EventCard card, String state) {
        if ("IGNORED".equals(state)) {
            return "IGNORED".equals(card.getDisposition());
        }
        if ("IGNORED".equals(card.getDisposition())) {
            return false;
        }
        if ("UNREAD".equals(state)) {
            return !card.isRead();
        }
        if ("FOLLOWED".equals(state)) {
            return card.isFollowed();
        }
        if ("LATER".equals(state)) {
            return "LATER".equals(card.getDisposition());
        }
        return true;
    }

    static List<ResearchRadarView.EventCard> filter(List<ResearchRadarView.EventCard> cards, String state, int limit) {
        return cards.stream().filter(card -> matches(card, state)).limit(limit).collect(Collectors.toList());
    }

    static Map<String, Integer> counts(List<ResearchRadarView.EventCard> cards) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String state : List.of("ALL", "UNREAD", "FOLLOWED", "LATER", "IGNORED")) {
            counts.put(state, (int) cards.stream().filter(card -> matches(card, state)).count());
        }
        return counts;
    }
}
