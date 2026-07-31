package com.finscope.service.radar;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.radar.RadarEvidenceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarSignal;
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
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ResearchRadarService {
    private final NewsFeedService news;
    private final RadarRepository repository;
    private final RadarClusteringService clustering;
    private final RadarPriorityService priority;
    private final WatchlistRepository watchlist;
    private final Clock clock;
    private final RadarEvidenceOrchestrator evidenceOrchestrator;
    private final RadarEvidenceRepository evidenceRepository;
    private final AgentRunRepository agentRuns;
    private final ReentrantLock refreshLock = new ReentrantLock();

    @Autowired
    public ResearchRadarService(NewsFeedService news, RadarRepository repository,
                                RadarClusteringService clustering, RadarPriorityService priority,
                                WatchlistRepository watchlist, RadarEvidenceOrchestrator evidenceOrchestrator,
                                RadarEvidenceRepository evidenceRepository, AgentRunRepository agentRuns) {
        this(news, repository, clustering, priority, watchlist, evidenceOrchestrator, evidenceRepository,
                agentRuns, Clock.systemDefaultZone());
    }

    ResearchRadarService(NewsFeedService news, RadarRepository repository,
                         RadarClusteringService clustering, RadarPriorityService priority,
                         WatchlistRepository watchlist, Clock clock) {
        this(news,repository,clustering,priority,watchlist,null,null,null,clock);
    }

    ResearchRadarService(NewsFeedService news, RadarRepository repository,
                         RadarClusteringService clustering, RadarPriorityService priority,
                         WatchlistRepository watchlist, RadarEvidenceOrchestrator evidenceOrchestrator,
                         RadarEvidenceRepository evidenceRepository, AgentRunRepository agentRuns, Clock clock) {
        this.news=news; this.repository=repository; this.clustering=clustering;
        this.priority=priority; this.watchlist=watchlist; this.clock=clock;
        this.evidenceOrchestrator=evidenceOrchestrator; this.evidenceRepository=evidenceRepository; this.agentRuns=agentRuns;
    }

    public ResearchRadarView load(String requestedCategory, boolean watchlistOnly, int requestedLimit) {
        String category = normalizeCategory(requestedCategory);
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!refreshLock.tryLock()) return fallback(category, watchlistOnly, limit, now, "雷达正在刷新，已展示最近一次结果");
        try {
            NewsFeedSnapshot snapshot = news.load(category, 100);
            for (NewsFeedItem item : snapshot.getItems()) repository.capture(toSignal(item), now);
            repository.expireSignals(now.minusHours(48), now);
            List<RadarSignal> active = repository.findActiveSignals(now.minusHours(48), 500);
            List<WatchlistItem> followed = watchlist.findByTypes(Arrays.asList("STOCK", "FUND"));
            List<RadarEvent> savedEvents = new ArrayList<RadarEvent>();
            int evidenceRefreshes = 0;
            for (RadarClusteringService.ClusterResult cluster : clustering.cluster(active)) {
                RadarEvent event = cluster.getEvent();
                RadarPriorityService.PriorityResult result = priority.score(event, cluster.getSignals(), followed, now);
                event.setPriorityScore(result.getTotalScore()); event.setScoreExplanation(String.join("；", result.getReasons()));
                event.setWatchlistRelevance(result.getWatchlistScore()); event.setWatchlistExplanation(result.getWatchlistExplanation());
                event.setUncertainty(result.getUncertainty()); event.setNextObservation(result.getNextObservation()); event.setUpdatedAt(now);
                RadarEvent saved = repository.saveEvent(event); repository.replaceEventSignals(saved.getId(), cluster.getLinks());
                if (evidenceOrchestrator != null && evidenceRefreshes < 2 && saved.getPriorityScore() >= 75) {
                    try {
                        RadarEvidenceOrchestrator.Outcome outcome = evidenceOrchestrator.enrich(saved, cluster.getSignals());
                        if (!"CACHED".equals(outcome.getStatus()) && !"SKIPPED".equals(outcome.getStatus())) {
                            saved.setEvidenceStatus(outcome.getStatus()); saved.setEvidenceSummary(outcome.getSummary());
                            saved.setEvidenceWarning(outcome.getWarning()); saved.setEvidenceCount(outcome.getEvidenceCount());
                            saved.setEvidenceSourceCount(outcome.getSourceCount());
                            if (outcome.getNextObservation()!=null&&!outcome.getNextObservation().trim().isEmpty()) saved.setNextObservation(outcome.getNextObservation());
                            if (!"DEGRADED".equals(outcome.getStatus())) saved.setEvidenceFingerprint(outcome.getFingerprint());
                            saved.setEvidenceUpdatedAt(now); saved = repository.saveEvent(saved); evidenceRefreshes++;
                        }
                    } catch (RuntimeException ignored) {
                        // 证据增强失败不能阻断雷达事件与实时资讯。
                    }
                }
                if (matches(category, saved) && (!watchlistOnly || saved.getWatchlistRelevance()>0)) savedEvents.add(saved);
            }
            savedEvents.sort(Comparator.comparingInt(RadarEvent::getPriorityScore).reversed()
                    .thenComparing(RadarEvent::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())));
            if (savedEvents.size()>limit) savedEvents=new ArrayList<RadarEvent>(savedEvents.subList(0,limit));
            return new ResearchRadarView(cards(savedEvents), snapshot.getItems(), snapshot.getWarnings(), snapshot.getRefreshedAt());
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
        return new ResearchRadarView.EventDetail(event,repository.findSignalsByEventId(id),repository.findEventSignals(id),
                evidenceRepository==null?Collections.emptyList():evidenceRepository.findByEventId(id),
                agentRuns==null?Collections.emptyList():agentRuns.findBySubject("RADAR_EVENT",id));
    }

    private ResearchRadarView fallback(String category,boolean watchlistOnly,int limit,LocalDateTime now,String warning) {
        return new ResearchRadarView(cards(repository.findRanked(category,watchlistOnly,limit)),Collections.<NewsFeedItem>emptyList(),
                Collections.singletonList(warning),now);
    }
    private List<ResearchRadarView.EventCard> cards(List<RadarEvent> events) {
        List<ResearchRadarView.EventCard> cards=new ArrayList<ResearchRadarView.EventCard>();
        for(RadarEvent event:events) cards.add(new ResearchRadarView.EventCard(event)); return cards;
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
