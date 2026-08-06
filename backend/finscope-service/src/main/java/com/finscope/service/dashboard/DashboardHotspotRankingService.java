package com.finscope.service.dashboard;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.service.radar.RadarDashboardCategoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

@Service
public class DashboardHotspotRankingService {
    private static final int BOARD_SIZE = 5;
    private final RadarRepository repository;
    public DashboardHotspotRankingService(RadarRepository repository) {
        this.repository = repository;
    }

    public List<Ranking> rankings() {
        List<Ranking> result = new ArrayList<Ranking>();
        result.add(ranking(RadarDashboardCategoryService.FINANCE, "金融"));
        result.add(ranking(RadarDashboardCategoryService.TECHNOLOGY, "科技"));
        result.add(ranking(RadarDashboardCategoryService.POLITICS, "政治"));
        return Collections.unmodifiableList(result);
    }

    /** 生产链路直接从本轮事件构建榜单，避免预热阶段回查 SQLite。 */
    public List<Ranking> rankings(List<RadarEvent> events) {
        List<RadarEvent> source = events == null ? Collections.<RadarEvent>emptyList() : events;
        List<Ranking> result = new ArrayList<Ranking>();
        result.add(ranking(source, RadarDashboardCategoryService.FINANCE, "金融"));
        result.add(ranking(source, RadarDashboardCategoryService.TECHNOLOGY, "科技"));
        result.add(ranking(source, RadarDashboardCategoryService.POLITICS, "政治"));
        return Collections.unmodifiableList(result);
    }

    private Ranking ranking(String categoryCode, String label) {
        List<Item> items = new ArrayList<Item>();
        for (RadarEvent event : repository.findTopByDashboardCategory(categoryCode, BOARD_SIZE)) {
            items.add(new Item(event));
        }
        return new Ranking(categoryCode, label, items);
    }

    private Ranking ranking(List<RadarEvent> events, String categoryCode, String label) {
        List<RadarEvent> selected = new ArrayList<RadarEvent>();
        for (RadarEvent event : events) {
            if (event != null && active(event) && categoryCode.equals(event.getDashboardCategory())) selected.add(event);
        }
        selected.sort(Comparator.comparingInt(RadarEvent::getHotspotScore).reversed()
                .thenComparing(RadarEvent::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RadarEvent::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        List<Item> items = new ArrayList<Item>();
        for (RadarEvent event : selected.subList(0, Math.min(BOARD_SIZE, selected.size()))) items.add(new Item(event));
        return new Ranking(categoryCode, label, items);
    }

    private boolean active(RadarEvent event) {
        return "ACTIVE".equalsIgnoreCase(event.getStatus()) || "QUIET".equalsIgnoreCase(event.getStatus());
    }

    public static final class Ranking {
        private final String categoryCode;
        private final String label;
        private final List<Item> items;

        Ranking(String categoryCode, String label, List<Item> items) {
            this.categoryCode = categoryCode;
            this.label = label;
            this.items = Collections.unmodifiableList(new ArrayList<Item>(items));
        }

        public String getCategoryCode() { return categoryCode; }
        public String getLabel() { return label; }
        public List<Item> getItems() { return items; }
    }

    public static final class Item {
        private final Long id;
        private final String title;
        private final String summary;
        private final int hotspotScore;
        private final String lifecycleState;
        private final int sourceCount;
        private final int signalCount;
        private final LocalDateTime lastSeenAt;

        Item(RadarEvent event) {
            this.id = event.getId();
            this.title = event.getCanonicalTitle();
            this.summary = firstNonBlank(event.getSummary(), event.getEvidenceSummary(), "暂无事件摘要");
            this.hotspotScore = event.getHotspotScore();
            this.lifecycleState = event.getHotspotLifecycleState();
            this.sourceCount = event.getSourceCount();
            this.signalCount = event.getSignalCount();
            this.lastSeenAt = event.getLastSeenAt();
        }

        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getSummary() { return summary; }
        public int getHotspotScore() { return hotspotScore; }
        public String getLifecycleState() { return lifecycleState; }
        public int getSourceCount() { return sourceCount; }
        public int getSignalCount() { return signalCount; }
        public LocalDateTime getLastSeenAt() { return lastSeenAt; }

        private static String firstNonBlank(String... values) {
            for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
            return "";
        }
    }
}
