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
    private final RadarHotspotScoreService directHotspotScores = new RadarHotspotScoreService();
    private final RadarDashboardCategoryService directDashboardCategories = new RadarDashboardCategoryService();
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
    private final RadarHotspotRefreshService backgroundRefresh;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile NewsFeedSnapshot lastNewsSnapshot;

    @Autowired
    public ResearchRadarService(NewsFeedService news, RadarRepository repository,
                                RadarClusteringService clustering, RadarPriorityService priority,
                                WatchlistRepository watchlist, RadarEventEnhancementScheduler enhancementScheduler,
                                RadarEvidenceRepository evidenceRepository, AgentRunRepository agentRuns,
                                RadarEventInterpretationService interpretations,
                                RadarEventWorkspaceService workspace, RadarEventTimelineService timeline,
                                RadarEvidenceTrustService trust, RadarHotspotRefreshService backgroundRefresh) {
        this(news, repository, clustering, priority, watchlist, enhancementScheduler, evidenceRepository,
                agentRuns, interpretations, workspace, timeline, trust, backgroundRefresh, Clock.systemDefaultZone());
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
        this(news, repository, clustering, priority, watchlist, enhancementScheduler, evidenceRepository, agentRuns,
                interpretations, workspace, timeline, trust, null, clock);
    }

    ResearchRadarService(NewsFeedService news, RadarRepository repository,
                         RadarClusteringService clustering, RadarPriorityService priority,
                         WatchlistRepository watchlist, RadarHotspotRefreshService backgroundRefresh, Clock clock) {
        this(news, repository, clustering, priority, watchlist, null, null, null,
                null, null, null, null, backgroundRefresh, clock);
    }

    private ResearchRadarService(NewsFeedService news, RadarRepository repository,
                         RadarClusteringService clustering, RadarPriorityService priority,
                         WatchlistRepository watchlist, RadarEventEnhancementScheduler enhancementScheduler,
                         RadarEvidenceRepository evidenceRepository, AgentRunRepository agentRuns,
                         RadarEventInterpretationService interpretations, RadarEventWorkspaceService workspace,
                         RadarEventTimelineService timeline, RadarEvidenceTrustService trust,
                         RadarHotspotRefreshService backgroundRefresh, Clock clock) {
        this.news=news; this.repository=repository; this.clustering=clustering;
        this.priority=priority; this.watchlist=watchlist; this.clock=clock;
        this.enhancementScheduler=enhancementScheduler; this.evidenceRepository=evidenceRepository; this.agentRuns=agentRuns;
        this.interpretations=interpretations;
        this.workspace=workspace;
        this.timeline=timeline; this.trust=trust; this.backgroundRefresh=backgroundRefresh;
    }

    public ResearchRadarView load(String requestedCategory, boolean watchlistOnly, int requestedLimit) {
        return load(requestedCategory, watchlistOnly, requestedLimit, "ALL");
    }

    public ResearchRadarView load(String requestedCategory, boolean watchlistOnly, int requestedLimit, String requestedState) {
        String category = normalizeCategory(requestedCategory);
        String state = normalizeState(requestedState);
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        LocalDateTime now = LocalDateTime.now(clock);
        if (backgroundRefresh != null) {
            boolean accepted = backgroundRefresh.requestRefresh();
            ResearchRadarView stored = loadStored(category, watchlistOnly, limit, state);
            if (accepted) return stored.withWarnings(Collections.singletonList("已请求后台生产，当前展示最近一次热点快照"));
            if (backgroundRefresh.isRunning()) return stored.withWarnings(Collections.singletonList("雷达正在后台生产，当前展示最近一次热点快照"));
            return stored;
        }
        if (!refreshLock.tryLock()) return fallback(category, watchlistOnly, limit, state, now, "雷达正在刷新，已展示最近一次结果");
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
                event.setDashboardCategory(directDashboardCategories.classify(event));
                RadarHotspotScoreService.Score hotspot = directHotspotScores.score(cluster.getSignals(), now);
                event.setHotspotScore(hotspot.getTotalScore()); event.setHotspotExplanation(hotspot.getExplanation());
                event.setHotspotLifecycleState(hotspot.getLifecycleState());
                RadarPriorityService.PriorityResult result = priority.score(event, cluster.getSignals(), followed, now);
                event.setPriorityScore(result.getTotalScore()); event.setScoreExplanation(String.join("；", result.getReasons()));
                event.setWatchlistRelevance(result.getWatchlistScore()); event.setWatchlistExplanation(result.getWatchlistExplanation());
                event.setUncertainty(result.getUncertainty()); event.setNextObservation(result.getNextObservation()); event.setUpdatedAt(now);
                RadarEvent saved = repository.saveEvent(event); repository.replaceEventSignals(saved.getId(), cluster.getLinks());
                repository.expireDuplicateEventsByCanonicalTitle(saved.getCanonicalTitle(), saved.getId(), now);
                activeEventKeys.add(saved.getEventKey());
                boolean includeEvidence = evidenceSchedules < 2 && saved.getPriorityScore() >= 75;
                if (enhancementScheduler != null && (cluster.getSignals().size() > 1 || includeEvidence)) {
                    enhancementScheduler.schedule(saved, cluster.getSignals(), now, includeEvidence);
                    if (includeEvidence) evidenceSchedules++;
                }
                if (matches(category, saved) && (!watchlistOnly || saved.getWatchlistRelevance()>0)) savedEvents.add(saved);
            }
            repository.expireEventsExcept(activeEventKeys, now.minusHours(48), now);
            List<RadarEvent> latestEvents=new ArrayList<RadarEvent>(savedEvents);
            latestEvents.sort(Comparator.comparing(RadarEvent::getLastSeenAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            if(latestEvents.size()>5)latestEvents=new ArrayList<RadarEvent>(latestEvents.subList(0,5));
            savedEvents.sort(Comparator.comparingInt(RadarEvent::getPriorityScore).reversed()
                    .thenComparing(RadarEvent::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())));
            if (savedEvents.size()>50) savedEvents=new ArrayList<RadarEvent>(savedEvents.subList(0,50));
            Map<Long,ResearchRadarView.EventCard> cardIndex=cardIndex(savedEvents,latestEvents);
            return new ResearchRadarView(filteredCards(savedEvents,cardIndex,state,limit),filteredCards(latestEvents,cardIndex,state,5),Collections.<NewsFeedItem>emptyList(),
                    snapshot.getWarnings(),snapshot.getRefreshedAt());
        } catch (BusinessException ex) {
            if (ex.getErrorCode()==ErrorCode.REQUEST_PARAMETER_INVALID) throw ex;
            return fallback(category,watchlistOnly,limit,state,now,"实时资讯暂不可用，已展示最近一次结果");
        } catch (RuntimeException ex) {
            return fallback(category,watchlistOnly,limit,state,now,"实时资讯暂不可用，已展示最近一次结果");
        } finally {
            refreshLock.unlock();
        }
    }

    /** 仅提交后台生产；页面读取使用 {@link #loadStored}，不会直接发起外部抓取。 */
    public boolean requestRefresh() {
        return backgroundRefresh != null && backgroundRefresh.requestRefresh();
    }

    public ResearchRadarView loadStored(String requestedCategory, boolean watchlistOnly, int requestedLimit, String requestedState) {
        String category=normalizeCategory(requestedCategory);String state=normalizeState(requestedState);
        int limit=Math.max(1,Math.min(requestedLimit,50));List<RadarEvent> ranked=repository.findRanked(category,watchlistOnly,50);
        List<RadarEvent> latest=new ArrayList<RadarEvent>(ranked);latest.sort(Comparator.comparing(RadarEvent::getLastSeenAt,
                Comparator.nullsLast(Comparator.reverseOrder())));if(latest.size()>5)latest=new ArrayList<RadarEvent>(latest.subList(0,5));
        Map<Long,ResearchRadarView.EventCard> index=cardIndex(ranked,latest);NewsFeedSnapshot cached=lastNewsSnapshot;
        LocalDateTime refreshedAt=cached==null?LocalDateTime.now(clock):cached.getRefreshedAt();
        ResearchRadarView.ProductionStatus status = ResearchRadarView.ProductionStatus.of(false, "EMPTY", refreshedAt, 0, 0, 0, null);
        if (backgroundRefresh != null) {
            java.util.Optional<com.finscope.domain.radar.RadarRefreshRun> latestRun = backgroundRefresh.latestRun();
            if (latestRun.isPresent()) {
                com.finscope.domain.radar.RadarRefreshRun run = latestRun.get();
                if (run.getCompletedAt() != null) refreshedAt=run.getCompletedAt();
                status = ResearchRadarView.ProductionStatus.of(backgroundRefresh.isRunning(), run.getStatus(), refreshedAt,
                        run.getSourceCount(), run.getSignalCount(), run.getEventCount(), productionMessage(run));
            } else {
                status = ResearchRadarView.ProductionStatus.of(backgroundRefresh.isRunning(), "EMPTY", refreshedAt, 0, 0, 0, null);
            }
        }
        return new ResearchRadarView(filteredCards(ranked,index,state,limit),filteredCards(latest,index,state,5),Collections.<NewsFeedItem>emptyList(),
                Collections.<String>emptyList(), refreshedAt, status);
    }

    /** 列表快照缺失时仅返回生产状态，不允许为页面读取回查 SQLite。 */
    public ResearchRadarView emptyStored() {
        LocalDateTime refreshedAt = LocalDateTime.now(clock);
        ResearchRadarView.ProductionStatus status = ResearchRadarView.ProductionStatus.of(false, "EMPTY", refreshedAt, 0, 0, 0, null);
        if (backgroundRefresh != null) {
            java.util.Optional<com.finscope.domain.radar.RadarRefreshRun> latestRun = backgroundRefresh.latestRun();
            if (latestRun.isPresent()) {
                com.finscope.domain.radar.RadarRefreshRun run = latestRun.get();
                if (run.getCompletedAt() != null) refreshedAt = run.getCompletedAt();
                status = ResearchRadarView.ProductionStatus.of(backgroundRefresh.isRunning(), run.getStatus(), refreshedAt,
                        run.getSourceCount(), run.getSignalCount(), run.getEventCount(), productionMessage(run));
            } else {
                status = ResearchRadarView.ProductionStatus.of(backgroundRefresh.isRunning(), "EMPTY", refreshedAt, 0, 0, 0, null);
            }
        }
        return new ResearchRadarView(Collections.<ResearchRadarView.EventCard>emptyList(),
                Collections.<ResearchRadarView.EventCard>emptyList(), Collections.<NewsFeedItem>emptyList(),
                Collections.<String>emptyList(), refreshedAt, status);
    }

    public ResearchRadarView.EventDetail detail(Long id) {
        RadarEvent event=repository.findEvent(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"雷达事件不存在"));
        List<RadarSignal> signals=repository.findSignalsByEventId(id);
        List<RadarEvidence> evidence=evidenceRepository==null?Collections.emptyList():evidenceRepository.findByEventId(id);
        RadarEventInterpretation interpretation=interpretations==null?null:interpretations.current(event,signals,evidence).orElse(null);
        RadarEventWorkspaceService.OpenedEvent opened=null;
        List<RadarEventWorkspace.TimelineEntry> timelineEntries=Collections.emptyList();
        RadarEventWorkspace.Trust trustView=new RadarEventWorkspace.Trust();
        try { if(workspace!=null)opened=workspace.open(event); } catch(RuntimeException ignored) { }
        try { if(timeline!=null)timelineEntries=timeline.timeline(event,signals,evidence,interpretation); } catch(RuntimeException ignored) { }
        try { if(trust!=null)trustView=trust.assess(signals,evidence,interpretation); } catch(RuntimeException ignored) { }
        return new ResearchRadarView.EventDetail(event,signals,repository.findEventSignals(id),evidence,
                agentRuns==null?Collections.emptyList():agentRuns.findBySubject("RADAR_EVENT",id),interpretation,
                opened==null?null:opened.getState(),opened==null?Collections.<RadarEventWorkspace.Observation>emptyList():opened.getObservations(),
                timelineEntries,trustView,opened==null?Collections.<RadarEventWorkspace.ResearchLink>emptyList():opened.getResearchLinks());
    }

    public ResearchRadarView.InterpretationView requestInterpretation(Long id) {
        if(interpretations==null)throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR,"雷达事件解读暂不可用");
        return new ResearchRadarView.InterpretationView(interpretations.request(id));
    }

    private ResearchRadarView fallback(String category,boolean watchlistOnly,int limit,String state,LocalDateTime now,String warning) {
        NewsFeedSnapshot cached = lastNewsSnapshot;
        List<RadarEvent> ranked=repository.findRanked(category,watchlistOnly,50);
        List<RadarEvent> latest=new ArrayList<RadarEvent>(ranked);latest.sort(Comparator.comparing(RadarEvent::getLastSeenAt,
                Comparator.nullsLast(Comparator.reverseOrder())));if(latest.size()>5)latest=new ArrayList<RadarEvent>(latest.subList(0,5));
        Map<Long,ResearchRadarView.EventCard> cardIndex=cardIndex(ranked,latest);
        return new ResearchRadarView(filteredCards(ranked,cardIndex,state,limit),filteredCards(latest,cardIndex,state,5),Collections.<NewsFeedItem>emptyList(),
                Collections.singletonList(warning),cached==null?now:cached.getRefreshedAt());
    }
    private Map<Long,ResearchRadarView.EventCard> cardIndex(List<RadarEvent> events,List<RadarEvent> latestEvents) {
        Map<Long,RadarEvent> unique=new LinkedHashMap<Long,RadarEvent>();
        for(RadarEvent event:events)if(event.getId()!=null)unique.put(event.getId(),event);
        for(RadarEvent event:latestEvents)if(event.getId()!=null)unique.put(event.getId(),event);
        List<Long> ids=new ArrayList<Long>(unique.keySet());
        Map<Long,RadarEventInterpretation> latest=interpretations==null?Collections.<Long,RadarEventInterpretation>emptyMap()
                :interpretations.latestByEventIds(ids);
        Map<Long,RadarEventWorkspace.Summary> summaries=Collections.emptyMap();
        if(workspace!=null)try {
            summaries=workspace.summaries(ids);
            for(RadarEvent event:unique.values())workspace.reconcileRead(event,summaries.get(event.getId()));
            workspace.createChangeNotifications(new ArrayList<RadarEvent>(unique.values()),summaries);
        } catch(RuntimeException ignored) { summaries=Collections.emptyMap(); }
        Map<Long,ResearchRadarView.EventCard> result=new LinkedHashMap<Long,ResearchRadarView.EventCard>();
        for(RadarEvent event:unique.values())result.put(event.getId(),new ResearchRadarView.EventCard(event,latest.get(event.getId()),summaries.get(event.getId())));
        return result;
    }
    private List<ResearchRadarView.EventCard> cards(List<RadarEvent> events,Map<Long,ResearchRadarView.EventCard> index) {
        List<ResearchRadarView.EventCard> cards=new ArrayList<ResearchRadarView.EventCard>();
        for(RadarEvent event:events) cards.add(index.get(event.getId())); return cards;
    }
    private List<ResearchRadarView.EventCard> filteredCards(List<RadarEvent> events,Map<Long,ResearchRadarView.EventCard> index,String state,int limit) {
        List<ResearchRadarView.EventCard> result=new ArrayList<ResearchRadarView.EventCard>();
        for(RadarEvent event:events){ResearchRadarView.EventCard card=index.get(event.getId());if(card!=null&&matchesState(card,state))result.add(card);if(result.size()>=limit)break;}
        return result;
    }
    private boolean matchesState(ResearchRadarView.EventCard card,String state){
        if("UNREAD".equals(state))return !card.isRead()&&!"IGNORED".equals(card.getDisposition());
        if("FOLLOWED".equals(state))return card.isFollowed()&&!"IGNORED".equals(card.getDisposition());
        if("LATER".equals(state))return "LATER".equals(card.getDisposition());
        if("IGNORED".equals(state))return "IGNORED".equals(card.getDisposition());
        return !"IGNORED".equals(card.getDisposition());
    }
    private boolean matches(String category,RadarEvent event){return "ALL".equals(category)||category.equalsIgnoreCase(event.getCategoryCode());}
    private String normalizeCategory(String value){return value==null||value.trim().isEmpty()?"ALL":value.trim().toUpperCase(Locale.ROOT);}
    private String normalizeState(String value){String state=value==null?"ALL":value.trim().toUpperCase(Locale.ROOT);return Arrays.asList("ALL","UNREAD","FOLLOWED","LATER","IGNORED").contains(state)?state:"ALL";}
    /** 生产批次的人类可读状态摘要：失败给出固定降级文案（细节留在服务端日志），成功时透出来源告警摘要。 */
    private String productionMessage(com.finscope.domain.radar.RadarRefreshRun run) {
        if ("FAILED".equals(run.getStatus())) {
            return "本批次生产失败，详见服务端批次日志";
        }
        String warning = run.getWarning();
        return (warning == null || warning.trim().isEmpty()) ? null : "部分来源告警：" + trim(warning, 200);
    }

    private String trim(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max) + "…";
    }
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
