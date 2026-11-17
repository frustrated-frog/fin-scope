package com.finscope.service.news;

import com.finscope.dao.news.NewsAiRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.news.ai.*;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

/** 候选召回不以公司词典或分类作为门槛；模型负责最终关系判断。 */
@Service
@lombok.extern.slf4j.Slf4j
public class NewsAiCandidateSelector {
    @Resource
    private NewsAiRepository ai;
    @Resource
    private RadarRepository radar;

    public Map<String, NewsAiEvent> pool(LocalDateTime now) {
        Map<String, NewsAiEvent> result = new LinkedHashMap<>();
        for (var event : radar.findWorkspaceEvents("ALL", false)) {
            NewsAiEvent value = new NewsAiEvent(); value.setEventKey(event.getEventKey()); value.setRevision(0);
            NewsAiAnalysis analysis = new NewsAiAnalysis(); analysis.setTitle(event.getCanonicalTitle());
            analysis.setSummary(limit(event.getSummary(), 900)); analysis.setCategoryCode(event.getCategoryCode());
            value.setAnalysis(analysis); value.setFirstAt(event.getFirstSeenAt()); value.setLastAt(event.getLastSeenAt());
            result.put(value.getEventKey(), value);
        }
        for (NewsAiEvent event : ai.events(now.minusDays(7), 10000)) {
            result.put(event.getEventKey(), event);
        }
        return result;
    }

    public List<com.finscope.domain.radar.RadarSignal> originals(String eventKey) {
        try {
            return radar.findEventByKey(eventKey).map(event -> radar.findSignalsByEventId(event.getId())).orElse(List.of());
        } catch (RuntimeException error) {
            log.warn("原有事件报道关联暂不可读，保留新报道的AI结果 eventKey={} errorType={}", eventKey, error.getClass().getSimpleName());
            return List.of();
        }
    }

    public Index index(Map<String, NewsAiEvent> events) {
        Index result = new Index();
        events.values().forEach(result::put);
        return result;
    }

    /** 单轮工作集的倒排索引，事件变化时只更新对应条目，不为每条新闻重建全部特征。 */
    public final class Index {
        private final Map<String, NewsAiEvent> events = new LinkedHashMap<>();
        private final Map<String, Set<String>> termsByEvent = new HashMap<>();
        private final Map<String, Set<String>> postings = new HashMap<>();

        public void put(NewsAiEvent event) {
            String key = event.getEventKey();
            for (String old : termsByEvent.getOrDefault(key, Set.of())) {
                postings.get(old).remove(key);
            }
            Set<String> terms = grams(event.getAnalysis().getTitle() + " " + limit(event.getAnalysis().getSummary(), 500));
            termsByEvent.put(key, terms);
            terms.forEach(term -> postings.computeIfAbsent(term, ignored -> new HashSet<>()).add(key));
            events.put(key, event);
        }

        public void removePending(String key) {
            NewsAiEvent event = events.get(key);
            if (event == null || event.getRevision() != 0 || !key.startsWith("news-ai:")) {
                return;
            }
            for (String term : termsByEvent.getOrDefault(key, Set.of())) {
                postings.get(term).remove(key);
            }
            termsByEvent.remove(key);
            events.remove(key);
        }

        public List<NewsAiEvent> select(String title, String content, int limit) {
            Map<String, Double> scores = new HashMap<>();
            for (String term : grams(title + " " + NewsAiCandidateSelector.this.limit(content, 500))) {
                Set<String> matches = postings.getOrDefault(term, Set.of());
                double weight = Math.log(1.0 + events.size() / (double) Math.max(1, matches.size()));
                matches.forEach(key -> scores.merge(key, weight, Double::sum));
            }
            List<NewsAiEvent> ranked = scores.keySet().stream().map(events::get).sorted(
                    Comparator.comparingDouble((NewsAiEvent event) -> scores.get(event.getEventKey())
                            / Math.sqrt(Math.max(1, termsByEvent.get(event.getEventKey()).size()))).reversed()
                    .thenComparing(NewsAiEvent::getLastAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(NewsAiEvent::getEventKey)).limit(limit).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            // 完全无词面交集时仍保留近期候选，不将未知主体直接排除。
            if (ranked.size() < limit) {
                events.values().stream().filter(event -> ranked.stream().noneMatch(value -> value.getEventKey().equals(event.getEventKey())))
                        .sorted(Comparator.comparing(NewsAiEvent::getLastAt, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(NewsAiEvent::getEventKey))
                        .limit(limit - ranked.size()).forEach(ranked::add);
            }
            return ranked.stream().map(NewsAiCandidateSelector.this::compact).toList();
        }
    }

    /** 候选只发送有界事实卡，不重复携带整篇报道或不断增长的事件历史。 */
    private NewsAiEvent compact(NewsAiEvent event) {
        NewsAiEvent value = new NewsAiEvent();
        value.setEventKey(event.getEventKey());
        value.setRevision(event.getRevision());
        value.setFirstAt(event.getFirstAt());
        value.setLastAt(event.getLastAt());
        NewsAiAnalysis source = event.getAnalysis();
        NewsAiAnalysis analysis = new NewsAiAnalysis();
        analysis.setTitle(limit(source.getTitle(), 160));
        analysis.setSummary(limit(source.getSummary(), 400));
        analysis.setCategoryCode(source.getCategoryCode());
        analysis.setDisplayCategory(source.getDisplayCategory());
        analysis.setEventType(source.getEventType());
        analysis.setTopics(source.getTopics().stream().limit(4).map(text -> limit(text, 40)).toList());
        analysis.setEntities(source.getEntities().stream().limit(6).map(text -> limit(text, 60)).toList());
        analysis.setFacts(source.getFacts().stream().skip(Math.max(0, source.getFacts().size() - 6))
                .map(text -> limit(text, 120)).toList());
        value.setAnalysis(analysis);
        return value;
    }

    public String snippet(String content, int limit) {
        if (content == null || content.length() <= limit) {
            return content == null ? "" : content;
        }
        int lead = limit / 2;
        StringBuilder value = new StringBuilder(content.substring(0, lead));
        // 保留后续段落里的数值、否定和进展；不把正文末端的关键限定一律截掉。
        for (String sentence : content.substring(lead).split("(?<=[。！？；\\n])")) {
            if (sentence.matches("(?s).*(\\d|不|未|否认|批准|完成|终止|计划|拟).*")) {
                int remaining = limit - value.length();
                if (remaining <= 0) {
                    break;
                }
                value.append(sentence, 0, Math.min(remaining, sentence.length()));
            }
        }
        return value.toString();
    }

    private Set<String> grams(String text) {
        String value = (text == null ? "" : text).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        Set<String> result = new HashSet<>();
        for (int index = 0; index + 2 <= value.length(); index++) {
            result.add(value.substring(index, index + 2));
        }
        return result;
    }
    private String limit(String text, int max) { return text == null ? "" : text.substring(0, Math.min(text.length(), max)); }
}
