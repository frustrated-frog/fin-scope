package com.finscope.service.dashboard;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.service.radar.RadarDashboardCategoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DashboardHotspotRankingService {
    private static final int BOARD_SIZE = 5;
    private final RadarRepository repository;
    private final RadarDashboardCategoryService categories;

    public DashboardHotspotRankingService(RadarRepository repository,
                                          RadarDashboardCategoryService categories) {
        this.repository = repository;
        this.categories = categories;
    }

    public List<Ranking> rankings() {
        classifyEventsCreatedBeforeDashboardRankings();
        List<Ranking> result = new ArrayList<Ranking>();
        result.add(ranking(RadarDashboardCategoryService.FINANCE, "金融"));
        result.add(ranking(RadarDashboardCategoryService.TECHNOLOGY, "科技"));
        result.add(ranking(RadarDashboardCategoryService.POLITICS, "政治"));
        return Collections.unmodifiableList(result);
    }

    private void classifyEventsCreatedBeforeDashboardRankings() {
        for (RadarEvent event : repository.findEventsForDashboardClassification(500)) {
            String classified = categories.classify(event);
            if (!classified.equals(event.getDashboardCategory())) {
                repository.updateDashboardCategory(event.getId(), classified);
            }
        }
    }

    private Ranking ranking(String categoryCode, String label) {
        List<Item> items = new ArrayList<Item>();
        for (RadarEvent event : repository.findTopByDashboardCategory(categoryCode, BOARD_SIZE)) {
            items.add(new Item(event));
        }
        return new Ranking(categoryCode, label, items);
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
