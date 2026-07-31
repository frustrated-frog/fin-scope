package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.news.NewsFeedItem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResearchRadarView {
    private final Overview overview;
    private final List<EventCard> events;
    private final List<NewsFeedItem> liveItems;
    private final List<String> warnings;
    private final LocalDateTime refreshedAt;

    public ResearchRadarView(List<EventCard> events, List<NewsFeedItem> liveItems,
                             List<String> warnings, LocalDateTime refreshedAt) {
        this.events = immutable(events); this.liveItems = immutable(liveItems);
        this.warnings = immutable(warnings); this.refreshedAt = refreshedAt;
        this.overview = Overview.from(this.events);
    }

    public static ResearchRadarView empty(LocalDateTime refreshedAt) {
        return new ResearchRadarView(Collections.<EventCard>emptyList(), Collections.<NewsFeedItem>emptyList(),
                Collections.<String>emptyList(), refreshedAt);
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null ? Collections.<T>emptyList() : values));
    }
    public Overview getOverview() { return overview; }
    public List<EventCard> getEvents() { return events; }
    public List<NewsFeedItem> getLiveItems() { return liveItems; }
    public List<String> getWarnings() { return warnings; }
    public LocalDateTime getRefreshedAt() { return refreshedAt; }

    public static final class Overview {
        private final int eventCount;
        private final int highPriorityCount;
        private final int watchlistRelatedCount;
        private final int sourceCount;
        private Overview(int eventCount, int highPriorityCount, int watchlistRelatedCount, int sourceCount) {
            this.eventCount=eventCount; this.highPriorityCount=highPriorityCount;
            this.watchlistRelatedCount=watchlistRelatedCount; this.sourceCount=sourceCount;
        }
        static Overview from(List<EventCard> cards) {
            int high=0, watch=0, sources=0;
            for (EventCard card : cards) { if (card.priorityScore >= 75) high++; if (card.watchlistRelated) watch++; sources += card.sourceCount; }
            return new Overview(cards.size(), high, watch, sources);
        }
        public int getEventCount() { return eventCount; }
        public int getHighPriorityCount() { return highPriorityCount; }
        public int getWatchlistRelatedCount() { return watchlistRelatedCount; }
        public int getSourceCount() { return sourceCount; }
    }

    public static class EventCard {
        private final Long id;
        private final String title;
        private final String summary;
        private final String categoryCode;
        private final int priorityScore;
        private final String recommendation;
        private final List<String> reasons;
        private final boolean watchlistRelated;
        private final String watchlistExplanation;
        private final int sourceCount;
        private final int signalCount;
        private final String uncertainty;
        private final String nextObservation;
        private final String suggestedResearchQuestion;
        private final LocalDateTime lastSeenAt;

        public EventCard(RadarEvent event) {
            this.id=event.getId(); this.title=event.getCanonicalTitle(); this.summary=event.getSummary();
            this.categoryCode=event.getCategoryCode(); this.priorityScore=event.getPriorityScore();
            this.recommendation=recommendation(event.getPriorityScore()); this.reasons=splitReasons(event.getScoreExplanation());
            this.watchlistRelated=event.getWatchlistRelevance()>0; this.watchlistExplanation=event.getWatchlistExplanation();
            this.sourceCount=event.getSourceCount(); this.signalCount=event.getSignalCount();
            this.uncertainty=event.getUncertainty(); this.nextObservation=event.getNextObservation();
            this.suggestedResearchQuestion="围绕“" + safe(event.getCanonicalTitle()) + "”，哪些事实已经确认，后续应重点观察什么？";
            this.lastSeenAt=event.getLastSeenAt();
        }
        private static String recommendation(int score) { return score>=75 ? "重点关注" : score>=55 ? "值得浏览" : "暂存观察"; }
        private static List<String> splitReasons(String value) {
            if (value == null || value.trim().isEmpty()) return Collections.emptyList();
            List<String> values = new ArrayList<String>(); for (String part : value.split("；")) if (!part.trim().isEmpty()) values.add(part.trim());
            return Collections.unmodifiableList(values);
        }
        private static String safe(String value) { return value == null ? "这件事" : value; }
        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getSummary() { return summary; }
        public String getCategoryCode() { return categoryCode; }
        public int getPriorityScore() { return priorityScore; }
        public String getRecommendation() { return recommendation; }
        public List<String> getReasons() { return reasons; }
        public boolean isWatchlistRelated() { return watchlistRelated; }
        public String getWatchlistExplanation() { return watchlistExplanation; }
        public int getSourceCount() { return sourceCount; }
        public int getSignalCount() { return signalCount; }
        public String getUncertainty() { return uncertainty; }
        public String getNextObservation() { return nextObservation; }
        public String getSuggestedResearchQuestion() { return suggestedResearchQuestion; }
        public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    }

    public static final class SignalView {
        private final Long id; private final String title; private final String content; private final String url;
        private final String sourceName; private final String sourceTier; private final LocalDateTime publishedAt;
        private final String relationType; private final double matchScore; private final String matchReason;
        SignalView(RadarSignal signal, RadarEventSignal link) {
            id=signal.getId(); title=signal.getTitle(); content=signal.getContent(); url=signal.getUrl();
            sourceName=signal.getSourceName(); sourceTier=signal.getSourceTier(); publishedAt=signal.getPublishedAt();
            relationType=link==null?null:link.getRelationType(); matchScore=link==null?0:link.getMatchScore(); matchReason=link==null?null:link.getMatchReason();
        }
        public Long getId(){return id;} public String getTitle(){return title;} public String getContent(){return content;}
        public String getUrl(){return url;} public String getSourceName(){return sourceName;} public String getSourceTier(){return sourceTier;}
        public LocalDateTime getPublishedAt(){return publishedAt;} public String getRelationType(){return relationType;}
        public double getMatchScore(){return matchScore;} public String getMatchReason(){return matchReason;}
    }

    public static final class EventDetail {
        private final EventCard event;
        private final List<SignalView> signals;
        public EventDetail(RadarEvent event, List<RadarSignal> signals, List<RadarEventSignal> links) {
            this.event = new EventCard(event); Map<Long,RadarEventSignal> bySignal=new LinkedHashMap<Long,RadarEventSignal>();
            for (RadarEventSignal link:links) bySignal.put(link.getSignalId(),link);
            List<SignalView> values=new ArrayList<SignalView>(); for(RadarSignal signal:signals) values.add(new SignalView(signal,bySignal.get(signal.getId())));
            this.signals=Collections.unmodifiableList(values);
        }
        public EventCard getEvent(){return event;} public List<SignalView> getSignals(){return signals;}
    }
}
