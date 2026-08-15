package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationInterpretation;
import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按 Gamma 官方事件标识聚合互斥选项，并消除跨分类重复市场。 */
@Component
public class GlobalExpectationEventAggregator {
    public List<GlobalExpectationEventGroup> aggregate(List<GlobalExpectationItem> items) {
        Map<String, GroupState> groups = new LinkedHashMap<String, GroupState>();
        for (GlobalExpectationItem item : items) {
            String groupId = groupId(item);
            GroupState state = groups.computeIfAbsent(groupId, ignored -> new GroupState(groupId, title(item)));
            state.add(item);
        }
        List<GlobalExpectationEventGroup> result = new ArrayList<GlobalExpectationEventGroup>();
        for (GroupState state : groups.values()) {
            result.add(state.toGroup());
        }
        result.sort(Comparator.comparing(GlobalExpectationEventGroup::getSignalScore).reversed()
                .thenComparing(GlobalExpectationEventGroup::getVolume24h, Comparator.reverseOrder()));
        return result;
    }

    private String groupId(GlobalExpectationItem item) {
        if (item.getEventId() != null && !item.getEventId().isBlank()) {
            return "event:" + item.getEventId();
        }
        String identity = item.getMarketId();
        if (identity == null || identity.isBlank()) {
            identity = item.getMarketUrl();
        }
        return "market:" + identity;
    }

    private String title(GlobalExpectationItem item) {
        if (item.getEventTitle() != null && !item.getEventTitle().isBlank()) {
            return item.getEventTitle();
        }
        return item.getQuestion();
    }

    private static final class GroupState {
        private final String id;
        private final String title;
        private final Set<String> themes = new LinkedHashSet<String>();
        private final Set<String> reasons = new LinkedHashSet<String>();
        private final Map<String, GlobalExpectationItem> markets = new LinkedHashMap<String, GlobalExpectationItem>();
        private int signalScore;

        private GroupState(String id, String title) {
            this.id = id;
            this.title = title;
        }

        private void add(GlobalExpectationItem item) {
            themes.add(item.getTheme());
            if (item.getSignalReasons() != null) {
                reasons.addAll(item.getSignalReasons());
            }
            signalScore = Math.max(signalScore, item.getSignalScore() == null ? 0 : item.getSignalScore());
            markets.putIfAbsent(marketIdentity(item), item);
        }

        private GlobalExpectationEventGroup toGroup() {
            GlobalExpectationEventGroup group = new GlobalExpectationEventGroup();
            group.setId(id);
            group.setTitle(title);
            group.setThemes(new ArrayList<String>(themes));
            group.setSignalScore(signalScore);
            group.setSignalReasons(new ArrayList<String>(reasons));
            group.setStatus(signalScore >= 40 ? "SIGNAL" : "WATCHING");
            group.setMarkets(new ArrayList<GlobalExpectationItem>(markets.values()));
            group.setVolume24h(markets.values().stream()
                    .map(GlobalExpectationItem::getVolume24h)
                    .filter(value -> value != null)
                    .mapToDouble(Double::doubleValue)
                    .sum());
            group.setRadarMatches(new ArrayList<>());
            GlobalExpectationInterpretation interpretation = new GlobalExpectationInterpretation();
            interpretation.setStatus("NOT_REQUESTED");
            group.setInterpretation(interpretation);
            return group;
        }

        private String marketIdentity(GlobalExpectationItem item) {
            if (item.getMarketId() != null && !item.getMarketId().isBlank()) {
                return item.getMarketId();
            }
            return item.getMarketUrl();
        }
    }
}
