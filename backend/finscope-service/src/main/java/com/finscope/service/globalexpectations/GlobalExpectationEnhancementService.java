package com.finscope.service.globalexpectations;

import com.finscope.dao.cache.GlobalExpectationsCacheRepository;
import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationInterpretation;
import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import com.finscope.domain.globalexpectations.GlobalExpectationRadarMatch;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** 分批增强全部观察卡片；规则快读始终作为可见回退。 */
@Service
public class GlobalExpectationEnhancementService {
    private static final int MAX_GROUPS_PER_REFRESH = 5;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    @Resource
    private GlobalExpectationsCacheRepository cacheRepository;
    @Resource
    private GlobalExpectationInterpretationAgent agent;
    @Resource(name = "globalExpectationExecutor")
    private Executor executor;

    public void request(List<GlobalExpectationEventGroup> groups) {
        int requested = 0;
        for (GlobalExpectationEventGroup group : groups) {
            if (requested >= MAX_GROUPS_PER_REFRESH) {
                break;
            }
            String fingerprint = fingerprint(group);
            Optional<GlobalExpectationInterpretation> cached = cacheRepository.getInterpretation(group.getId());
            if (cached.isPresent() && fingerprint.equals(cached.get().getFingerprint())) {
                group.setInterpretation(cached.get());
                continue;
            }
            String taskKey = group.getId() + ":" + fingerprint;
            if (!inFlight.add(taskKey)) {
                continue;
            }
            GlobalExpectationInterpretation queued = copy(group.getInterpretation());
            queued.setStatus("QUEUED");
            queued.setFingerprint(fingerprint);
            cacheRepository.putInterpretation(group.getId(), queued);
            group.setInterpretation(queued);
            try {
                executor.execute(() -> complete(group, fingerprint, taskKey));
                requested++;
            } catch (RuntimeException error) {
                inFlight.remove(taskKey);
                GlobalExpectationInterpretation failed = copy(queued);
                failed.setStatus("FAILED");
                failed.setFingerprint(fingerprint);
                failed.setFailureMessage("AI 解读任务繁忙，请稍后刷新");
                cacheRepository.putInterpretation(group.getId(), failed);
                group.setInterpretation(failed);
            }
        }
    }

    public void attachCached(List<GlobalExpectationEventGroup> groups) {
        for (GlobalExpectationEventGroup group : groups) {
            String fingerprint = fingerprint(group);
            cacheRepository.getInterpretation(group.getId())
                    .filter(value -> fingerprint.equals(value.getFingerprint()))
                    .ifPresent(group::setInterpretation);
        }
    }

    private void complete(GlobalExpectationEventGroup group, String fingerprint, String taskKey) {
        try {
            GlobalExpectationInterpretation result = agent.interpret(group);
            mergeMissing(result, group.getInterpretation());
            result.setFingerprint(fingerprint);
            cacheRepository.putInterpretation(group.getId(), result);
            group.setInterpretation(result);
        } finally {
            inFlight.remove(taskKey);
        }
    }

    private String fingerprint(GlobalExpectationEventGroup group) {
        StringBuilder source = new StringBuilder();
        source.append(group.getId()).append('|').append(group.getTitle()).append('|')
                .append(group.getExpectationRealityState()).append('|').append(group.getSignalReasons())
                .append('|').append(group.getThemes());
        for (GlobalExpectationItem market : safe(group.getMarkets())) {
            source.append('|').append(market.getMarketId()).append(':').append(probabilityBucket(market.getProbability()))
                    .append(':').append(market.getSignalReasons());
        }
        for (GlobalExpectationRadarMatch match : safe(group.getRadarMatches())) {
            source.append('|').append(match.getEventId()).append(':').append(match.getTitle());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception error) {
            return Integer.toHexString(source.toString().hashCode());
        }
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private int probabilityBucket(Integer probability) {
        return probability == null ? -1 : Math.floorDiv(probability, 5);
    }

    private GlobalExpectationInterpretation copy(GlobalExpectationInterpretation source) {
        GlobalExpectationInterpretation target = new GlobalExpectationInterpretation();
        if (source == null) {
            target.setSource("RULE");
            return target;
        }
        target.setSource(source.getSource());
        target.setHappened(source.getHappened());
        target.setMeaning(source.getMeaning());
        target.setRelatedVariables(source.getRelatedVariables());
        target.setNextObservation(source.getNextObservation());
        target.setUncertainty(source.getUncertainty());
        return target;
    }

    private void mergeMissing(GlobalExpectationInterpretation result, GlobalExpectationInterpretation fallback) {
        if (fallback == null) {
            return;
        }
        if (blank(result.getHappened())) {
            result.setHappened(fallback.getHappened());
        }
        if (blank(result.getMeaning())) {
            result.setMeaning(fallback.getMeaning());
        }
        if (blank(result.getRelatedVariables())) {
            result.setRelatedVariables(fallback.getRelatedVariables());
        }
        if (blank(result.getNextObservation())) {
            result.setNextObservation(fallback.getNextObservation());
        }
        if (blank(result.getUncertainty())) {
            result.setUncertainty(fallback.getUncertainty());
        }
        if (blank(result.getSource()) || !"READY".equals(result.getStatus())) {
            result.setSource(fallback.getSource());
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
