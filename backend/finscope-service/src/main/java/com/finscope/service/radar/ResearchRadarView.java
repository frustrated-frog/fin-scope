package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.domain.agent.AgentRun;
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
    private final ProductionStatus productionStatus;

    public ResearchRadarView(List<EventCard> events, List<NewsFeedItem> liveItems,
                             List<String> warnings, LocalDateTime refreshedAt) {
        this(events, liveItems, warnings, refreshedAt, ProductionStatus.idle(refreshedAt));
    }

    public ResearchRadarView(List<EventCard> events, List<NewsFeedItem> liveItems,
                             List<String> warnings, LocalDateTime refreshedAt, ProductionStatus productionStatus) {
        this.events = immutable(events); this.liveItems = immutable(liveItems);
        this.warnings = immutable(warnings); this.refreshedAt = refreshedAt;
        this.productionStatus = productionStatus == null ? ProductionStatus.idle(refreshedAt) : productionStatus;
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
    public ProductionStatus getProductionStatus() { return productionStatus; }
    public ResearchRadarView withWarnings(List<String> values) {
        return new ResearchRadarView(events, liveItems, values, refreshedAt, productionStatus);
    }
    public ResearchRadarView withProductionStatus(ProductionStatus value) {
        return new ResearchRadarView(events, liveItems, warnings, refreshedAt, value);
    }

    public static final class ProductionStatus {
        private final boolean running;
        private final String status;
        private final LocalDateTime completedAt;
        private final int sourceCount;
        private final int signalCount;
        private final int eventCount;
        private final String warning;

        private ProductionStatus(boolean running, String status, LocalDateTime completedAt,
                                 int sourceCount, int signalCount, int eventCount, String warning) {
            this.running = running; this.status = status; this.completedAt = completedAt;
            this.sourceCount = sourceCount; this.signalCount = signalCount; this.eventCount = eventCount; this.warning = warning;
        }
        static ProductionStatus idle(LocalDateTime completedAt) { return new ProductionStatus(false, "EMPTY", completedAt, 0, 0, 0, null); }
        public static ProductionStatus of(boolean running, String status, LocalDateTime completedAt,
                                          int sourceCount, int signalCount, int eventCount, String warning) {
            return new ProductionStatus(running, status, completedAt, sourceCount, signalCount, eventCount, warning);
        }
        public boolean isRunning() { return running; }
        public String getStatus() { return status; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public int getSourceCount() { return sourceCount; }
        public int getSignalCount() { return signalCount; }
        public int getEventCount() { return eventCount; }
        public String getWarning() { return warning; }
    }

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
        private final String eventKey;
        private final String title;
        private final String summary;
        private final String categoryCode;
        private final int hotspotScore;
        private final String hotspotExplanation;
        private final String hotspotLifecycleState;
        private final int confidenceScore;
        private final String confidenceExplanation;
        private final String scoreVersion;
        private final int priorityScore;
        private final String recommendation;
        private final List<String> reasons;
        private final boolean watchlistRelated;
        private final String watchlistExplanation;
        private final int sourceCount;
        private final int signalCount;
        private final String uncertainty;
        private final String nextObservation;
        private final String evidenceStatus;
        private final String evidenceSummary;
        private final String evidenceWarning;
        private final int evidenceCount;
        private final int evidenceSourceCount;
        private final String suggestedResearchQuestion;
        private final LocalDateTime lastSeenAt;
        private final String changeType;
        private final String changeSummary;
        private final String interpretationStatus;
        private final boolean read;
        private final boolean followed;
        private final String disposition;
        private final int observationCount;
        private final int openObservationCount;
        private final int researchRunCount;
        private final int unreadNotificationCount;

        public EventCard(RadarEvent event) {
            this(event, null);
        }

        public EventCard(RadarEvent event, RadarEventInterpretation interpretation) {
            this(event, interpretation, null);
        }

        public EventCard(RadarEvent event, RadarEventInterpretation interpretation, RadarEventWorkspace.Summary workspace) {
            this.id=event.getId(); this.eventKey=event.getEventKey(); this.title=event.getCanonicalTitle(); this.summary=event.getSummary();
            this.categoryCode=event.getCategoryCode(); this.hotspotScore=event.getHotspotScore(); this.hotspotExplanation=event.getHotspotExplanation();
            this.hotspotLifecycleState=event.getHotspotLifecycleState();
            this.confidenceScore=event.getConfidenceScore(); this.confidenceExplanation=event.getConfidenceExplanation();
            this.scoreVersion=event.getScoreVersion();
            this.priorityScore=event.getPriorityScore();
            this.recommendation=recommendation(event.getPriorityScore()); this.reasons=splitReasons(event.getScoreExplanation());
            this.watchlistRelated=event.getWatchlistRelevance()>0; this.watchlistExplanation=event.getWatchlistExplanation();
            this.sourceCount=event.getSourceCount(); this.signalCount=event.getSignalCount();
            this.uncertainty=event.getUncertainty(); this.nextObservation=event.getNextObservation();
            this.evidenceStatus=event.getEvidenceStatus(); this.evidenceSummary=event.getEvidenceSummary();
            this.evidenceWarning=event.getEvidenceWarning(); this.evidenceCount=event.getEvidenceCount();
            this.evidenceSourceCount=event.getEvidenceSourceCount();
            this.suggestedResearchQuestion="围绕“" + safe(event.getCanonicalTitle()) + "”，哪些事实已经确认，后续应重点观察什么？";
            this.lastSeenAt=event.getLastSeenAt();
            this.changeType=changeType(event); this.changeSummary=changeSummary(event, this.changeType);
            this.interpretationStatus=interpretation==null?null:interpretation.getStatus();
            this.read=workspace!=null&&workspace.isRead(); this.followed=workspace!=null&&workspace.isFollowed();
            this.disposition=workspace==null?"ACTIVE":workspace.getDisposition();
            this.observationCount=workspace==null?0:workspace.getObservationCount();
            this.openObservationCount=workspace==null?0:workspace.getOpenObservationCount();
            this.researchRunCount=workspace==null?0:workspace.getResearchRunCount();
            this.unreadNotificationCount=workspace==null?0:workspace.getUnreadNotificationCount();
        }
        private static String recommendation(int score) { return score>=75 ? "重点关注" : score>=55 ? "值得浏览" : "暂存观察"; }
        private static List<String> splitReasons(String value) {
            if (value == null || value.trim().isEmpty()) return Collections.emptyList();
            List<String> values = new ArrayList<String>(); for (String part : value.split("；")) if (!part.trim().isEmpty()) values.add(part.trim());
            return Collections.unmodifiableList(values);
        }
        private static String safe(String value) { return value == null ? "这件事" : value; }
        private static String changeType(RadarEvent event) {
            if(event.getFirstSeenAt()!=null&&event.getLastSeenAt()!=null
                    &&event.getLastSeenAt().isAfter(event.getFirstSeenAt().plusMinutes(1)))return "FOLLOW_UP";
            if(event.getSourceCount()>1||event.getSignalCount()>1)return "MULTI_SOURCE";
            return "NEW_EVENT";
        }
        private static String changeSummary(RadarEvent event,String type) {
            if("FOLLOW_UP".equals(type))return "事件出现后续信息，已更新聚合结果";
            if("MULTI_SOURCE".equals(type))return "新增独立来源确认同一事件";
            return "首次进入研究雷达";
        }
        public Long getId() { return id; }
        public String getEventKey() { return eventKey; }
        public String getTitle() { return title; }
        public String getSummary() { return summary; }
        public String getCategoryCode() { return categoryCode; }
        public int getHotspotScore() { return hotspotScore; }
        public String getHotspotExplanation() { return hotspotExplanation; }
        public String getHotspotLifecycleState() { return hotspotLifecycleState; }
        public int getConfidenceScore() { return confidenceScore; }
        public String getConfidenceExplanation() { return confidenceExplanation; }
        public String getScoreVersion() { return scoreVersion; }
        public int getPriorityScore() { return priorityScore; }
        public String getRecommendation() { return recommendation; }
        public List<String> getReasons() { return reasons; }
        public boolean isWatchlistRelated() { return watchlistRelated; }
        public String getWatchlistExplanation() { return watchlistExplanation; }
        public int getSourceCount() { return sourceCount; }
        public int getSignalCount() { return signalCount; }
        public String getUncertainty() { return uncertainty; }
        public String getNextObservation() { return nextObservation; }
        public String getEvidenceStatus() { return evidenceStatus; }
        public String getEvidenceSummary() { return evidenceSummary; }
        public String getEvidenceWarning() { return evidenceWarning; }
        public int getEvidenceCount() { return evidenceCount; }
        public int getEvidenceSourceCount() { return evidenceSourceCount; }
        public String getSuggestedResearchQuestion() { return suggestedResearchQuestion; }
        public LocalDateTime getLastSeenAt() { return lastSeenAt; }
        public String getChangeType(){return changeType;} public String getChangeSummary(){return changeSummary;}
        public String getInterpretationStatus(){return interpretationStatus;}
        public boolean isRead(){return read;} public boolean isFollowed(){return followed;}
        public String getDisposition(){return disposition;} public int getObservationCount(){return observationCount;}
        public int getOpenObservationCount(){return openObservationCount;} public int getResearchRunCount(){return researchRunCount;}
        public int getUnreadNotificationCount(){return unreadNotificationCount;}
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
        private final List<EvidenceView> evidence;
        private final List<AgentTraceView> agentTrace;
        private final InterpretationView interpretation;
        private final RadarEventWorkspace.State workspaceState;
        private final List<RadarEventWorkspace.Observation> observations;
        private final List<RadarEventWorkspace.TimelineEntry> timeline;
        private final RadarEventWorkspace.Trust trust;
        private final List<RadarEventWorkspace.ResearchLink> researchLinks;
        public EventDetail(RadarEvent event, List<RadarSignal> signals, List<RadarEventSignal> links) {
            this(event,signals,links,Collections.<RadarEvidence>emptyList(),Collections.<AgentRun>emptyList());
        }
        public EventDetail(RadarEvent event, List<RadarSignal> signals, List<RadarEventSignal> links,
                           List<RadarEvidence> evidence, List<AgentRun> traces) {
            this(event,signals,links,evidence,traces,null);
        }
        public EventDetail(RadarEvent event, List<RadarSignal> signals, List<RadarEventSignal> links,
                           List<RadarEvidence> evidence, List<AgentRun> traces,
                           RadarEventInterpretation interpretation) {
            this(event,signals,links,evidence,traces,interpretation,null,Collections.<RadarEventWorkspace.Observation>emptyList());
        }
        public EventDetail(RadarEvent event, List<RadarSignal> signals, List<RadarEventSignal> links,
                           List<RadarEvidence> evidence, List<AgentRun> traces,
                           RadarEventInterpretation interpretation, RadarEventWorkspace.State workspaceState,
                           List<RadarEventWorkspace.Observation> observations) {
            this(event,signals,links,evidence,traces,interpretation,workspaceState,observations,
                    Collections.<RadarEventWorkspace.TimelineEntry>emptyList(),new RadarEventWorkspace.Trust());
        }
        public EventDetail(RadarEvent event, List<RadarSignal> signals, List<RadarEventSignal> links,
                           List<RadarEvidence> evidence, List<AgentRun> traces,
                           RadarEventInterpretation interpretation, RadarEventWorkspace.State workspaceState,
                           List<RadarEventWorkspace.Observation> observations,
                           List<RadarEventWorkspace.TimelineEntry> timeline, RadarEventWorkspace.Trust trust) {
            this(event,signals,links,evidence,traces,interpretation,workspaceState,observations,timeline,trust,
                    Collections.<RadarEventWorkspace.ResearchLink>emptyList());
        }
        public EventDetail(RadarEvent event, List<RadarSignal> signals, List<RadarEventSignal> links,
                           List<RadarEvidence> evidence, List<AgentRun> traces,
                           RadarEventInterpretation interpretation, RadarEventWorkspace.State workspaceState,
                           List<RadarEventWorkspace.Observation> observations,
                           List<RadarEventWorkspace.TimelineEntry> timeline, RadarEventWorkspace.Trust trust,
                           List<RadarEventWorkspace.ResearchLink> researchLinks) {
            this.event = new EventCard(event); Map<Long,RadarEventSignal> bySignal=new LinkedHashMap<Long,RadarEventSignal>();
            for (RadarEventSignal link:links) bySignal.put(link.getSignalId(),link);
            List<SignalView> values=new ArrayList<SignalView>(); for(RadarSignal signal:signals) values.add(new SignalView(signal,bySignal.get(signal.getId())));
            this.signals=Collections.unmodifiableList(values);
            List<EvidenceView> evidenceViews=new ArrayList<EvidenceView>();
            if(evidence!=null)for(RadarEvidence item:evidence)evidenceViews.add(new EvidenceView(item));
            this.evidence=Collections.unmodifiableList(evidenceViews);
            List<AgentTraceView> traceViews=new ArrayList<AgentTraceView>();
            if(traces!=null)for(AgentRun trace:traces)traceViews.add(new AgentTraceView(trace));
            this.agentTrace=Collections.unmodifiableList(traceViews);
            this.interpretation=interpretation==null?null:new InterpretationView(interpretation);
            this.workspaceState=workspaceState;
            this.observations=immutable(observations);
            this.timeline=immutable(timeline); this.trust=trust==null?new RadarEventWorkspace.Trust():trust;
            this.researchLinks=immutable(researchLinks);
        }
        public EventCard getEvent(){return event;} public List<SignalView> getSignals(){return signals;}
        public List<EvidenceView> getEvidence(){return evidence;} public List<AgentTraceView> getAgentTrace(){return agentTrace;}
        public InterpretationView getInterpretation(){return interpretation;}
        public RadarEventWorkspace.State getWorkspaceState(){return workspaceState;}
        public List<RadarEventWorkspace.Observation> getObservations(){return observations;}
        public List<RadarEventWorkspace.TimelineEntry> getTimeline(){return timeline;}
        public RadarEventWorkspace.Trust getTrust(){return trust;}
        public List<RadarEventWorkspace.ResearchLink> getResearchLinks(){return researchLinks;}
    }

    public static final class InterpretationView {
        private final Long id,eventId; private final String status,failureCode,failureMessage;
        private final boolean stale; private final Long durationMs; private final RadarEventInterpretation.Result result;
        InterpretationView(RadarEventInterpretation value){id=value.getId();eventId=value.getEventId();status=value.getStatus();
            failureCode=value.getFailureCode();failureMessage=value.getFailureMessage();stale=value.isStale();
            durationMs=value.getDurationMs();result=value.getResult();}
        public static InterpretationView queued(Long eventId){RadarEventInterpretation value=new RadarEventInterpretation();
            value.setEventId(eventId);value.setStatus("QUEUED");return new InterpretationView(value);}
        public Long getId(){return id;} public Long getEventId(){return eventId;} public String getStatus(){return status;}
        public String getFailureCode(){return failureCode;} public String getFailureMessage(){return failureMessage;}
        public boolean isStale(){return stale;} public Long getDurationMs(){return durationMs;}
        public RadarEventInterpretation.Result getResult(){return result;}
    }

    public static final class EvidenceView {
        private final Long id; private final String toolCode,evidenceType,title,summary,url,sourceName,sourceTier;
        private final LocalDateTime publishedAt;
        EvidenceView(RadarEvidence value){id=value.getId();toolCode=value.getToolCode();evidenceType=value.getEvidenceType();
            title=value.getTitle();summary=value.getSummary();url=value.getUrl();sourceName=value.getSourceName();
            sourceTier=value.getSourceTier();publishedAt=value.getPublishedAt();}
        public Long getId(){return id;} public String getToolCode(){return toolCode;} public String getEvidenceType(){return evidenceType;}
        public String getTitle(){return title;} public String getSummary(){return summary;} public String getUrl(){return url;}
        public String getSourceName(){return sourceName;} public String getSourceTier(){return sourceTier;} public LocalDateTime getPublishedAt(){return publishedAt;}
    }

    public static final class AgentTraceView {
        private final String nodeName,status,summary,errorType,fallbackReason; private final long durationMs; private final boolean fallbackUsed;
        AgentTraceView(AgentRun value){nodeName=value.getNodeName();status=value.getStatus();summary=value.getOutput();
            errorType=value.getErrorType();fallbackUsed=value.isFallbackUsed();fallbackReason=value.getFallbackReason();durationMs=value.getDurationMs();}
        public String getNodeName(){return nodeName;} public String getStatus(){return status;} public String getSummary(){return summary;}
        public String getErrorType(){return errorType;} public boolean isFallbackUsed(){return fallbackUsed;}
        public String getFallbackReason(){return fallbackReason;} public long getDurationMs(){return durationMs;}
    }
}
