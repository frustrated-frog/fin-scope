package com.finscope.service.radar;

import com.finscope.common.enums.news.NewsAiRelation;
import com.finscope.dao.news.NewsAiRepository;
import com.finscope.domain.news.ai.*;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

/** 主生产链路只读 AI 结果；模型关闭、限额或失败时未处理部分继续本地聚类。 */
@Service
@lombok.extern.slf4j.Slf4j
public class NewsAiAggregationService {
    @Resource
    private NewsAiRepository repository;
    @Resource
    private RadarClusteringService clustering;

    public List<RadarClusteringService.ClusterResult> cluster(List<RadarSignal> signals, LocalDateTime now) {
        try {
            return clusterWithCache(signals, now);
        } catch (RuntimeException error) {
            for (RadarSignal signal : signals) {
                signal.setAiDuplicate(false);
                signal.setAiRepostGroup(null);
                signal.setAiMeaningfulAt(null);
            }
            log.warn("AI新闻聚合结果暂不可读，继续本地聚类 errorType={}", error.getClass().getSimpleName());
            return clustering.cluster(signals);
        }
    }

    private List<RadarClusteringService.ClusterResult> clusterWithCache(List<RadarSignal> signals, LocalDateTime now) {
        Map<String, List<RadarSignal>> byContent = new LinkedHashMap<>();
        for (RadarSignal signal : signals) {
            signal.setAiDuplicate(false);
            signal.setAiRepostGroup(null);
            signal.setAiMeaningfulAt(null);
            String key = NewsAiContentIdentity.occurrence(signal.getTitle(), signal.getContent(), signal.getPublishedAt() == null ? signal.getFirstSeenAt() : signal.getPublishedAt());
            byContent.computeIfAbsent(key, ignored -> new ArrayList<>()).add(signal);
        }
        Map<String, NewsAiBinding> bindings = repository.bindingsByHashes(byContent.keySet());
        if (bindings.isEmpty()) {
            return clustering.cluster(signals);
        }
        Map<String, List<RadarSignal>> groups = new LinkedHashMap<>();
        Map<String, NewsAiBinding> descriptions = new HashMap<>();
        List<RadarSignal> local = new ArrayList<>();
        for (Map.Entry<String, List<RadarSignal>> entry : byContent.entrySet()) {
            NewsAiBinding binding = bindings.get(entry.getKey());
            if (binding == null) {
                local.addAll(entry.getValue());
            } else {
                for (RadarSignal signal : entry.getValue()) {
                    signal.setAiDuplicate(binding.getRelation() == NewsAiRelation.DUPLICATE);
                }
                groups.computeIfAbsent(binding.getEventKey(), ignored -> new ArrayList<>()).addAll(entry.getValue());
                descriptions.put(binding.getEventKey(), binding);
            }
        }
        List<RadarClusteringService.ClusterResult> fallback = new ArrayList<>();
        for (var cluster : clustering.cluster(local)) {
            List<RadarSignal> matched = groups.get(cluster.getEvent().getEventKey());
            if (matched == null) {
                fallback.add(cluster);
            } else {
                // 模型选择既有本地事件时，把该事件尚未理解的原始报道一并保留。
                matched.addAll(cluster.getSignals());
            }
        }
        Map<String, NewsAiEvent> events = new HashMap<>();
        repository.events(now.minusDays(30), 10000).forEach(event -> events.put(event.getEventKey(), event));
        List<RadarClusteringService.ClusterResult> result = new ArrayList<>();
        for (Map.Entry<String, List<RadarSignal>> group : groups.entrySet()) {
            NewsAiEvent stored = events.get(group.getKey());
            NewsAiAnalysis analysis = stored == null ? descriptions.get(group.getKey()).getAnalysis() : stored.getAnalysis();
            RadarSignal anchor = group.getValue().stream().filter(signal -> !signal.isAiDuplicate()).min(
                    Comparator.comparing(RadarSignal::getPublishedAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(RadarSignal::getItemId)).orElse(null);
            if (anchor != null) {
                anchor.setAiRepostGroup(group.getKey());
            }
            for (RadarSignal signal : group.getValue()) {
                if (signal.isAiDuplicate()) {
                    signal.setAiRepostGroup(group.getKey());
                    signal.setAiMeaningfulAt(stored == null ? null : stored.getLastAt());
                }
                signal.setCategoryCode(analysis.getCategoryCode());
            }
            var cluster = clustering.group(group.getValue(), group.getKey());
            cluster.getEvent().setCanonicalTitle(analysis.getTitle()); cluster.getEvent().setSummary(analysis.getSummary());
            cluster.getEvent().setCategoryCode(analysis.getCategoryCode()); cluster.getEvent().setAiAnalysis(analysis);
            result.add(cluster);
        }
        result.addAll(fallback);
        return result;
    }
}
