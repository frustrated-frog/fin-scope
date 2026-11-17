package com.finscope.service.news;

import com.finscope.common.enums.news.NewsAiJobState;
import com.finscope.common.enums.news.NewsAiRelation;
import com.finscope.dao.news.NewsAiRepository;
import com.finscope.dao.news.NewsCategoryRepository;
import com.finscope.dao.news.NewsReportRepository;
import com.finscope.domain.news.NewsCategory;
import com.finscope.domain.news.ai.*;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.llm.NewsAiLunaClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

/** 后台统一理解编排；页面、生产快照读取只使用已保存结果。 */
@Service
@Slf4j
public class NewsAiProcessor {
    @Resource
    private NewsAiRepository repository;
    @Resource
    private NewsReportRepository reports;
    @Resource
    private NewsCategoryRepository categories;
    @Resource
    private NewsAiUnifiedAgent agent;
    @Resource
    private NewsAiLunaClient client;
    @Resource
    private NewsAiProperties properties;
    @Resource
    private NewsAiCandidateSelector selector;

    public boolean isActive() {
        return properties.isEnabled() && client.isConfigured();
    }

    public void process(List<RadarSignal> signals, LocalDateTime now) {
        boolean active = isActive();
        LocalDateTime since = repository.observeMode(active, now);
        if (!active) {
            return;
        }
        Set<String> changed = properties.isBackfillExisting() ? Set.of() : repository.changedReportsSince(since.isAfter(now.minusHours(36)) ? since : now.minusHours(36));
        Map<String, RadarSignal> unique = new LinkedHashMap<>();
        signals.stream().sorted(Comparator.comparing(RadarSignal::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RadarSignal::getItemId)).forEach(signal -> {
            if (properties.isBackfillExisting() || changed.contains(signal.getItemId())) {
                unique.putIfAbsent(NewsAiContentIdentity.occurrence(signal.getTitle(), signal.getContent(), signal.getPublishedAt() == null ? signal.getFirstSeenAt() : signal.getPublishedAt()), signal);
            }
        });
        Map<String, NewsAiBinding> existing = repository.bindingsByHashes(unique.keySet());
        Map<String, String> linked = repository.linkedReportKeys(signals.stream().map(RadarSignal::getItemId).toList());
        for (RadarSignal signal : signals) {
            String occurrence = NewsAiContentIdentity.occurrence(signal.getTitle(), signal.getContent(),
                    signal.getPublishedAt() == null ? signal.getFirstSeenAt() : signal.getPublishedAt());
            NewsAiBinding binding = existing.get(occurrence);
            if (binding != null && !binding.getContentKey().equals(linked.get(signal.getItemId()))) {
                repository.linkReport(signal.getItemId(), signal.getTitle(), signal.getContent(), binding);
            }
        }
        unique.keySet().removeAll(existing.keySet());
        if (unique.isEmpty()) {
            return;
        }
        List<NewsCategory> directory = categories.findEnabled();
        if (directory.isEmpty()) {
            return;
        }
        String policy = NewsAiContentIdentity.digest(NewsAiUnifiedAgent.VERSION + client.model() + directory.stream()
                .map(category -> category.getCode() + ":" + category.getClassificationGuidance()).sorted().toList());
        NewsAiCandidateSelector.Index index = null;
        List<Claim> batch = new ArrayList<>();
        int calls = 0;
        for (Map.Entry<String, RadarSignal> input : unique.entrySet()) {
            if (!properties.isEnabled() || calls >= properties.getMaxBatchesPerRun()) {
                break;
            }
            String cacheKey = input.getKey() + ":" + policy;
            String owner = UUID.randomUUID().toString();
            if (!repository.claim(cacheKey, input.getKey(), owner, now, properties.getMaxAttempts())) {
                continue;
            }
            if (index == null) {
                index = selector.index(selector.pool(now));
            }
            NewsAiWorkItem work = work(input.getKey(), cacheKey, input.getValue(), index, directory);
            batch.add(new Claim(cacheKey, owner, work, input.getValue()));
            // 后续同批新闻可直接比较此前输入，不要求先完成另一次模型调用。
            NewsAiEvent seed = new NewsAiEvent(); seed.setEventKey("news-ai:" + input.getKey());
            NewsAiAnalysis description = new NewsAiAnalysis(); description.setTitle(work.getTitle()); description.setSummary(work.getContent());
            seed.setAnalysis(description); seed.setFirstAt(work.getPublishedAt()); seed.setLastAt(work.getPublishedAt()); index.put(seed);
            if (batch.size() >= properties.getBatchSize()) {
                runBatch(batch, directory, index, now); batch.clear(); calls++;
            }
        }
        if (!batch.isEmpty()) {
            runBatch(batch, directory, index, now);
        }
    }

    private NewsAiWorkItem work(String hash, String cacheKey, RadarSignal signal, NewsAiCandidateSelector.Index index, List<NewsCategory> directory) {
        NewsAiWorkItem work = new NewsAiWorkItem(); work.setContentKey(hash); work.setTitle(signal.getTitle());
        NewsAiAnalysis savedAnalysis = repository.analysis(cacheKey);
        work.setSavedAnalysis(savedAnalysis == null ? repository.reusableAnalysis(hash) : savedAnalysis);
        if (work.getSavedAnalysis() != null && directory.stream().noneMatch(category -> category.getCode().equals(work.getSavedAnalysis().getCategoryCode()))) {
            work.setSavedAnalysis(null);
        }
        work.setContent(work.getSavedAnalysis() == null ? selector.snippet(signal.getContent(), properties.getContentChars()) : "");
        work.setBodyTruncated(signal.getContent() != null && signal.getContent().length() > properties.getContentChars());
        work.setPublishedAt(signal.getPublishedAt()); work.setCandidates(index.select(signal.getTitle(), signal.getContent(), properties.getMaxCandidates()));
        return work;
    }

    private void runBatch(List<Claim> claims, List<NewsCategory> directory, NewsAiCandidateSelector.Index index, LocalDateTime now) {
        try {
            completeBatch(claims, directory, index, now);
        } finally {
            // 同批临时候选若合并到别处或失败，不能留给下一批造成无效匹配。
            for (Claim claim : claims) {
                index.removePending("news-ai:" + claim.work().getContentKey());
            }
        }
    }

    private void completeBatch(List<Claim> claims, List<NewsCategory> directory, NewsAiCandidateSelector.Index index, LocalDateTime now) {
        String callId = UUID.randomUUID().toString();
        List<NewsAiWorkItem> inputs = claims.stream().map(Claim::work).toList();
        NewsAiLunaClient.Prepared prepared = agent.prepare(inputs, directory);
        long upper = properties.costMicros(prepared.getInputTokenUpperBound(), properties.getMaxOutputTokens());
        if (!properties.isEnabled() || !repository.reserve(callId, client.model(), upper, properties.getDailyBudgetMicros(), properties.getDailyMaxCalls(), now)) {
            claims.forEach(claim -> repository.defer(claim.key(), claim.owner(), now.toLocalDate().plusDays(1).atStartOfDay()));
            return;
        }
        boolean owned = true;
        for (Claim claim : claims) {
            owned &= repository.attachCall(claim.key(), claim.owner(), callId);
        }
        if (!owned || !properties.isEnabled()) {
            repository.settle(callId, 0L, 0L, 0L, "CANCELLED_BEFORE_SEND");
            claims.forEach(claim -> repository.fail(claim.key(), claim.owner(), NewsAiJobState.RETRYABLE, now.plusMinutes(5)));
            return;
        }
        NewsAiLunaClient.Reply reply;
        try {
            reply = client.complete(prepared);
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            repository.settle(callId, null, null, null, error.getClass().getSimpleName());
            claims.forEach(claim -> repository.fail(claim.key(), claim.owner(), NewsAiJobState.UNKNOWN, now.plusDays(1)));
            log.warn("新闻 AI 请求结果未知，保留费用预留且不自动重发 callId={} errorType={}", callId, error.getClass().getSimpleName());
            return;
        }
        Long actual = reply.getInputTokens() != null && reply.getOutputTokens() != null
                ? properties.costMicros(reply.getInputTokens(), reply.getOutputTokens()) : null;
        repository.settle(callId, reply.getInputTokens(), reply.getOutputTokens(), actual, reply.isCompleted() ? null : "INCOMPLETE");
        Map<String, NewsAiDecision> decisions = agent.parse(reply.getText(), inputs, directory);
        Map<String, String> aliases = new HashMap<>();
        Map<String, Integer> acceptedVersions = new HashMap<>();
        for (Claim claim : claims) {
            NewsAiDecision decision = decisions.get(claim.work().getContentKey());
            if (decision == null) {
                repository.fail(claim.key(), claim.owner(), NewsAiJobState.RETRYABLE, now.plusHours(1));
                continue;
            }
            if (!repository.saveAnalysis(claim.key(), claim.owner(), decision.getAnalysis(), now)) {
                continue;
            }
            NewsAiEvent saved = bind(claim, decision, aliases, acceptedVersions, now);
            if (saved != null) {
                index.put(saved);
                reports.applyAi(claim.signal().getItemId(), claim.signal().getTitle(), claim.signal().getContent(), decision.getAnalysis(), directory, NewsAiUnifiedAgent.VERSION);
                NewsAiBinding binding = repository.bindings(List.of(claim.key())).get(claim.key());
                repository.linkReport(claim.signal().getItemId(), claim.signal().getTitle(), claim.signal().getContent(), binding);
            } else {
                repository.fail(claim.key(), claim.owner(), NewsAiJobState.RETRYABLE, now.plusMinutes(5));
            }
        }
        log.info("新闻 AI 批次完成 callId={} items={} valid={} inputTokens={} outputTokens={}", callId, claims.size(), decisions.size(), reply.getInputTokens(), reply.getOutputTokens());
    }

    private NewsAiEvent bind(Claim claim, NewsAiDecision decision, Map<String, String> aliases, Map<String, Integer> acceptedVersions, LocalDateTime now) {
        String ownKey = "news-ai:" + decision.getContentKey();
        String target = aliases.getOrDefault(decision.getTargetKey(), decision.getTargetKey());
        boolean joining = decision.getRelation() == NewsAiRelation.DUPLICATE || decision.getRelation() == NewsAiRelation.SAME_EVENT || decision.getRelation() == NewsAiRelation.PROGRESS;
        NewsAiEvent current = joining ? repository.event(target) : null;
        NewsAiEvent candidate = claim.work().getCandidates().stream().filter(value -> value.getEventKey().equals(decision.getTargetKey())).findFirst().orElse(null);
        if (joining && current == null && (candidate == null || candidate.getEventKey().startsWith("news-ai:"))) {
            return null;
        }
        int expected = current == null ? 0 : current.getRevision();
        // 同批前项更新可用已解析别名，其他候选必须仍匹配发给模型的版本。
        if (joining && current != null && candidate != null && current.getRevision() != acceptedVersions.getOrDefault(target, candidate.getRevision())) {
            return null;
        }
        NewsAiEvent next = new NewsAiEvent();
        next.setEventKey(joining ? target : ownKey); next.setRevision(expected + 1);
        next.setFirstAt(current != null ? current.getFirstAt() : joining && candidate != null ? candidate.getFirstAt() : claim.signal().getPublishedAt());
        next.setLastAt(current != null ? current.getLastAt() : joining && candidate != null ? candidate.getLastAt() : claim.signal().getPublishedAt());
        if (claim.signal().getPublishedAt() != null && (next.getFirstAt() == null || claim.signal().getPublishedAt().isBefore(next.getFirstAt()))) {
            next.setFirstAt(claim.signal().getPublishedAt());
        }
        NewsAiAnalysis base = current != null ? copy(current.getAnalysis()) : joining && candidate != null ? copy(candidate.getAnalysis()) : copy(decision.getAnalysis());
        if (current == null || decision.getRelation() == NewsAiRelation.PROGRESS || !decision.getNewFacts().isEmpty()) {
            base = copy(decision.getAnalysis());
            Set<String> facts = new LinkedHashSet<>();
            if (current != null) {
                facts.addAll(current.getAnalysis().getFacts());
            }
            facts.addAll(base.getFacts()); facts.addAll(decision.getNewFacts());
            base.setFacts(facts.stream().skip(Math.max(0, facts.size() - 32)).toList());
            if (current != null && !decision.getNewFacts().isEmpty()) {
                base.setSummary(cut(current.getAnalysis().getSummary(), 700) + "\n最新进展：" + String.join("；", decision.getNewFacts()));
            }
            LocalDateTime publishedAt = claim.signal().getPublishedAt();
            if ((!joining || decision.getRelation() == NewsAiRelation.PROGRESS || !decision.getNewFacts().isEmpty())
                    && publishedAt != null && (next.getLastAt() == null || publishedAt.isAfter(next.getLastAt()))) {
                next.setLastAt(publishedAt);
            }
        } else {
            next.setRevision(Math.max(1, expected)); // 转载增加报道关系，不改变语义版本或重写摘要。
        }
        next.setAnalysis(base);
        NewsAiBinding binding = new NewsAiBinding(); binding.setContentKey(decision.getContentKey()); binding.setEventKey(next.getEventKey());
        binding.setAnalysis(decision.getAnalysis()); binding.setRelation(decision.getRelation());
        binding.setContextHash(NewsAiContentIdentity.digest(claim.work().getCandidates().stream().map(value -> value.getEventKey() + ":" + value.getRevision()).toList().toString()));
        if (!repository.bind(claim.key(), claim.owner(), binding, next, expected, now)) {
            return null;
        }
        acceptedVersions.put(next.getEventKey(), next.getRevision());
        aliases.put(ownKey, next.getEventKey());
        if (joining) {
            aliases.put(decision.getTargetKey(), next.getEventKey());
        }
        return next;
    }

    private NewsAiAnalysis copy(NewsAiAnalysis value) {
        NewsAiAnalysis copy = new NewsAiAnalysis(); copy.setTitle(value.getTitle()); copy.setSummary(value.getSummary());
        copy.setCategoryCode(value.getCategoryCode()); copy.setDisplayCategory(value.getDisplayCategory()); copy.setEventType(value.getEventType());
        copy.setEntities(new ArrayList<>(value.getEntities())); copy.setTopics(new ArrayList<>(value.getTopics())); copy.setFacts(new ArrayList<>(value.getFacts())); return copy;
    }
    private String cut(String text, int limit) { return text.substring(0, Math.min(text.length(), limit)); }
    private record Claim(String key, String owner, NewsAiWorkItem work, RadarSignal signal) { }
}
