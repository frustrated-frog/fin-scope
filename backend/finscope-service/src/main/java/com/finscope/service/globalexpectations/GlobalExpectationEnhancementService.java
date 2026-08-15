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

/** 将强异动提交给独立执行器；解释缓存与监控快照互不依赖。 */
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
            if (!"SIGNAL".equals(group.getStatus())) {
                continue;
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
            GlobalExpectationInterpretation queued = new GlobalExpectationInterpretation();
            queued.setStatus("QUEUED");
            queued.setFingerprint(fingerprint);
            cacheRepository.putInterpretation(group.getId(), queued);
            group.setInterpretation(queued);
            try {
                executor.execute(() -> complete(group, fingerprint, taskKey));
                requested++;
            } catch (RuntimeException error) {
                inFlight.remove(taskKey);
                GlobalExpectationInterpretation failed = new GlobalExpectationInterpretation();
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
            result.setFingerprint(fingerprint);
            cacheRepository.putInterpretation(group.getId(), result);
        } finally {
            inFlight.remove(taskKey);
        }
    }

    private String fingerprint(GlobalExpectationEventGroup group) {
        StringBuilder source = new StringBuilder();
        source.append(group.getId()).append('|').append(group.getTitle()).append('|')
                .append(group.getSignalScore()).append('|').append(group.getSignalReasons());
        for (GlobalExpectationItem market : safe(group.getMarkets())) {
            source.append('|').append(market.getMarketId()).append(':').append(market.getProbability())
                    .append(':').append(market.getVolume24h()).append(':').append(market.getRank())
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
}
