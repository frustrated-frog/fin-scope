package com.finscope.service.radar;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.radar.RadarEvidenceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.service.news.NewsFeedItem;
import com.finscope.service.news.NewsFeedService;
import com.finscope.service.news.NewsFeedSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ResearchRadarService {
    private final NewsFeedService news;
    private final RadarRepository repository;
    private final RadarClusteringService clustering;
    private final RadarPriorityService priority;
    private final WatchlistRepository watchlist;
    private final Clock clock;
    private final RadarEventEnhancementScheduler enhancementScheduler;
    private final RadarEvidenceRepository evidenceRepository;
    private final AgentRunRepository agentRuns;
    private final RadarEventInterpretationService interpretations;
    private final RadarEventWorkspaceService workspace;
    private final RadarEventTimelineService timeline;
    private final RadarEvidenceTrustService trust;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile NewsFeedSnapshot lastNewsSnapshot;

    @Autowired
    public ResearchRadarService(NewsFeedService news, RadarRepository repository,
                                RadarClusteringService clustering, RadarPriorityService priority,
                                WatchlistRepository watchlist, RadarEventEnhancementScheduler enhancementScheduler,
                                RadarEvidenceRepository evidenceRepository, AgentRunRepository agentRuns,
                                RadarEventInterpretationService interpretations,
                                RadarEventWorkspaceService workspace, RadarEventTimelineService timeline,
                                RadarEvidenceTrustService trust) {
        this(news, repository, clustering, priority, watchlist, enhancementScheduler, evidenceRepository,
                agentRuns, interpretations, workspace, timeline, trust, Clock.systemDefaultZone());
    }

    ResearchRadarService(NewsFeedService news, RadarRepository repository,
                         RadarClusteringService clustering, RadarPriorityService priority,
                         WatchlistRepository watchlist, Clock clock) {
        this(news,repository,clustering,priority,watchlist,null,null,null,null,null,null,null,clock);
    }

    ResearchRadarService(NewsFeedService news, RadarRepository repository,
                         RadarClusteringService clustering, RadarPriorityService priority,
                         WatchlistRepository watchlist, RadarEventEnhancementScheduler enhancementScheduler,
                         RadarEvidenceRepository evidenceRepository, AgentRunRepository agentRuns, Clock clock) {
        this(news,repository,clustering,priority,watchlist,enhancementScheduler,evidenceRepository,agentRuns,null,null,null,null,clock);
    }

    ResearchRadarService(NewsFeedService news, RadarRepository repository,
                         RadarClusteringService clustering, RadarPriorityService priority,
                         WatchlistRepository watchlist, RadarEventEnhancementScheduler enhancementScheduler,
                         RadarEvidenceRepository evidenceRepository, AgentRunRepository agentRuns,
                         RadarEventInterpretationService interpretations, Clock clock) {
        this(news,repository,clustering,priority,watchlist,enhancementScheduler,evidenceRepository,agentRuns,interpretations,null,null,null,clock);
    }

    ResearchRadarService(NewsFeedService news, RadarRepository repository,
                         RadarClusteringService clustering, RadarPriorityService priority,
                         WatchlistRepository watchlist, RadarEventEnhancementScheduler enhancementScheduler,
                         RadarEvidenceRepository evidenceRepository, AgentRunRepository agentRuns,
                         RadarEventInterpretationService interpretations, RadarEventWorkspaceService workspace, Clock clock) {
        this(news,repository,clustering,priority,watchlist,enhancementScheduler,evidenceRepository,agentRuns,interpretations,workspace,null,null,clock);
    }

    ResearchRadarService(NewsFeedService news, RadarRepository repository,
                         RadarClusteringService clustering, RadarPriorityService priority,
                         WatchlistRepository watchlist, RadarEventEnhancementScheduler enhancementScheduler,
                         RadarEvidenceRepository evidenceRepository, AgentRunRepository agentRuns,
                         RadarEventInterpretationService interpretations, RadarEventWorkspaceService workspace,
                         RadarEventTimelineService timeline, RadarEvidenceTrustService trust, Clock clock) {
        this.news=news; this.repository=repository; this.clustering=clustering;
        this.priority=priority; this.watchlist=watchlist; this.clock=clock;
        this.enhancementScheduler=enhancementScheduler; this.evidenceRepository=evidenceRepository; this.agentRuns=agentRuns;
        this.interpretations=interpretations;
        this.workspace=workspace;
        this.timeline=timeline; this.trust=trust;
    }

    public ResearchRadarView load(String requestedCategory, boolean watchlistOnly, int requestedLimit) {
        String category = normalizeCategory(requestedCategory);
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!refreshLock.tryLock()) return fallback(category, watchlistOnly, limit, now, "雷达正在刷新，已展示最近一次结果");
        try {
            NewsFeedSnapshot snapshot = news.load(category, 100);
            lastNewsSnapshot = snapshot;
            for (NewsFeedItem item : snapshot.getItems()) repository.capture(toSignal(item), now);
            repository.expireSignals(now.minusHours(48), now);
            List<RadarSignal> active = repository.findActiveSignals(now.minusHours(48), 500);
            List<WatchlistItem> followed = watchlist.findByTypes(Arrays.asList("STOCK", "FUND"));
            List<RadarEvent> savedEvents = new ArrayList<RadarEvent>();
            Set<String> activeEventKeys = new HashSet<String>();
            int evidenceSchedules = 0;
            for (RadarClusteringService.ClusterResult cluster : clustering.cluster(active)) {
                RadarEvent event = cluster.getEvent();
                RadarPriorityService.PriorityResult result = priority.score(event, cluster.getSignals(), followed, now);
                event.setPriorityScore(result.getTotalScore()); event.setScoreExplanation(String.join("；", result.getReasons()));
                event.setWatchlistRelevance(result.getWatchlistScore()); event.setWatchlistExplanation(result.getWatchlistExplanation());
                event.setUncertainty(result.getUncertainty()); event.setNextObservation(result.getNextObservation()); event.setUpdatedAt(now);
                RadarEvent saved = repository.saveEvent(event); repository.replaceEventSignals(saved.getId(), cluster.getLinks());
                activeEventKeys.add(saved.getEventKey());
                boolean includeEvidence = evidenceSchedules < 2 && saved.getPriorityScore() >= 75;
                if (enhancementScheduler != null && (cluster.getSignals().size() > 1 || includeEvidence)) {
                    enhancementScheduler.schedule(saved, cluster.getSignals(), now, includeEvidence);
                    if (includeEvidence) evidenceSchedules++;
                }
                if (matches(category, saved) && (!watchlistOnly || saved.getWatchlistRelevance()>0)) savedEvents.add(saved);
            }
            repository.expireEventsExcept(activeEventKeys, now);
            List<RadarEvent> latestEvents=new ArrayList<RadarEvent>(savedEvents);
            latestEvents.sort(Comparator.comparing(RadarEvent::getLastSeenAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            if(latestEvents.size()>5)latestEvents=new ArrayList<RadarEvent>(latestEvents.subList(0,5));
            savedEvents.sort(Comparator.comparingInt(RadarEvent::getPriorityScore).reversed()
                    .thenComparing(RadarEvent::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())));
            if (savedEvents.size()>limit) savedEvents=new ArrayList<RadarEvent>(savedEvents.subList(0,limit));
            Map<Long,ResearchRadarView.EventCard> cardIndex=cardIndex(savedEvents,latestEvents);
            return new ResearchRadarView(cards(savedEvents,cardIndex),cards(latestEvents,cardIndex),Collections.<NewsFeedItem>emptyList(),
                    snapshot.getWarnings(),snapshot.getRefreshedAt());
        } catch (BusinessException ex) {
            if (ex.getErrorCode()==ErrorCode.REQUEST_PARAMETER_INVALID) throw ex;
            return fallback(category,watchlistOnly,limit,now,"实时资讯暂不可用，已展示最近一次结果");
        } catch (RuntimeException ex) {
            return fallback(category,watchlistOnly,limit,now,"实时资讯暂不可用，已展示最近一次结果");
        } finally {
            refreshLock.unlock();
        }
    }

    public ResearchRadarView.EventDetail detail(Long id) {
        RadarEvent event=repository.findEvent(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"雷达事件不存在"));
        List<RadarSignal> signals=repository.findSignalsByEventId(id);
        List<RadarEvidence> evidence=evidenceRepository==null?Collections.emptyList():evidenceRepository.findByEventId(id);
        RadarEventInterpretation interpretation=interpretations==null?null:interpretations.current(event,signals,evidence).orElse(null);
        RadarEventWorkspaceService.OpenedEvent opened=workspace==null?null:workspace.open(event);
        List<RadarEventWorkspace.TimelineEntry> timelineEntries=timeline==null?Collections.<RadarEventWorkspace.TimelineEntry>emptyList()
                :timeline.timeline(event,signals,evidence,interpretation);
        RadarEventWorkspace.Trust trustView=trust==null?new RadarEventWorkspace.Trust():trust.assess(signals,evidence,interpretation);
        return new ResearchRadarView.EventDetail(event,signals,repository.findEventSignals(id),evidence,
                agentRuns==null?Collections.emptyList():agentRuns.findBySubject("RADAR_EVENT",id),interpretation,
                opened==null?null:opened.getState(),opened==null?Collections.<RadarEventWorkspace.Observation>emptyList():opened.getObservations(),
                timelineEntries,trustView,opened==null?Collections.<RadarEventWorkspace.ResearchLink>emptyList():opened.getResearchLinks());
    }

    public ResearchRadarView.InterpretationView requestInterpretation(Long id) {
        if(interpretations==null)throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR,"雷达事件解读暂不可用");
        return new ResearchRadarView.InterpretationView(interpretations.request(id));
    }

    private ResearchRadarView fallback(String category,boolean watchlistOnly,int limit,LocalDateTime now,String warning) {
        NewsFeedSnapshot cached = lastNewsSnapshot;
        List<RadarEvent> ranked=repository.findRanked(category,watchlistOnly,limit);
        List<RadarEvent> latest=new ArrayList<RadarEvent>(ranked);latest.sort(Comparator.comparing(RadarEvent::getLastSeenAt,
                Comparator.nullsLast(Comparator.reverseOrder())));if(latest.size()>5)latest=new ArrayList<RadarEvent>(latest.subList(0,5));
        Map<Long,ResearchRadarView.EventCard> cardIndex=cardIndex(ranked,latest);
        return new ResearchRadarView(cards(ranked,cardIndex),cards(latest,cardIndex),Collections.<NewsFeedItem>emptyList(),
                Collections.singletonList(warning),cached==null?now:cached.getRefreshedAt());
    }
    private Map<Long,ResearchRadarView.EventCard> cardIndex(List<RadarEvent> events,List<RadarEvent> latestEvents) {
        Map<Long,RadarEvent> unique=new LinkedHashMap<Long,RadarEvent>();
        for(RadarEvent event:events)if(event.getId()!=null)unique.put(event.getId(),event);
        for(RadarEvent event:latestEvents)if(event.getId()!=null)unique.put(event.getId(),event);
        List<Long> ids=new ArrayList<Long>(unique.keySet());
        Map<Long,RadarEventInterpretation> latest=interpretations==null?Collections.<Long,RadarEventInterpretation>emptyMap()
                :interpretations.latestByEventIds(ids);
        Map<Long,RadarEventWorkspace.Summary> summaries=workspace==null?Collections.<Long,RadarEventWorkspace.Summary>emptyMap():workspace.summaries(ids);
        if(workspace!=null)workspace.createChangeNotifications(new ArrayList<RadarEvent>(unique.values()),summaries);
        Map<Long,ResearchRadarView.EventCard> result=new LinkedHashMap<Long,ResearchRadarView.EventCard>();
        for(RadarEvent event:unique.values())result.put(event.getId(),new ResearchRadarView.EventCard(event,latest.get(event.getId()),summaries.get(event.getId())));
        return result;
    }
    private List<ResearchRadarView.EventCard> cards(List<RadarEvent> events,Map<Long,ResearchRadarView.EventCard> index) {
        List<ResearchRadarView.EventCard> cards=new ArrayList<ResearchRadarView.EventCard>();
        for(RadarEvent event:events) cards.add(index.get(event.getId())); return cards;
    }
    private boolean matches(String category,RadarEvent event){return "ALL".equals(category)||category.equalsIgnoreCase(event.getCategoryCode());}
    private String normalizeCategory(String value){return value==null||value.trim().isEmpty()?"ALL":value.trim().toUpperCase(Locale.ROOT);}
    private RadarSignal toSignal(NewsFeedItem item) {
        RadarSignal signal=new RadarSignal(); signal.setItemId(item.getId()); signal.setProviderCode(item.getProviderCode());
        signal.setSourceName(item.getSourceName()); signal.setSourceTier(item.getSourceTier()); signal.setCategoryCode(item.getCategoryCode());
        signal.setTitle(item.getTitle()); signal.setContent(item.getContent()); signal.setUrl(item.getUrl()); signal.setPublishedAt(item.getPublishedAt());
        signal.setContentHash(hash(item.getTitle()+"\n"+item.getContent()+"\n"+item.getUrl())); signal.setStatus("ACTIVE"); return signal;
    }
    private String hash(String value) {
        try { byte[] digest=MessageDigest.getInstance("SHA-256").digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(); for(byte b:digest) hex.append(String.format("%02x",b)); return hex.toString();
        } catch(Exception ex){throw new IllegalStateException("无法生成雷达内容指纹",ex);}
    }
}
