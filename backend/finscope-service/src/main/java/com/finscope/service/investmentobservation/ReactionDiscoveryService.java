package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionResolutionStatus;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.investmentobservation.ReactionDiscoveryStatus;
import com.finscope.domain.investmentobservation.ReactionEventRules;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.domain.investmentobservation.ReactionStockMatch;
import com.finscope.domain.investmentobservation.ReactionStockResolution;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.service.news.NewsWindowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReactionDiscoveryService {
    private static final int LOOKBACK_HOURS = 36;

    @Resource
    private NewsWindowService materials;
    @Resource
    private RadarRepository radar;
    @Resource
    private ReactionSampleRepository repository;
    @Resource
    private ReactionStockResolver resolver;
    private final ReactionEventRules rules = new ReactionEventRules();
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public ReactionDiscoveryStatus discover() {
        return discover(false);
    }

    public ReactionDiscoveryStatus discover(boolean manual) {
        LocalDateTime now = LocalDateTime.now(clock);
        ReactionDiscoveryStatus result = new ReactionDiscoveryStatus();
        Map<List<String>, ReactionStockResolution> resolutions = new HashMap<>();
        boolean available = true;
        try (var ignored = ProviderCallDeadline.open(Duration.ofSeconds(manual ? 45 : 20))) {
            try {
                materials.scan(batch -> {
                    for (var item : batch) {
                        admit(item.getTitle(), item.getContent(), item.getUrl(), item.getPublishedAt(),
                                item.getFirstSeenAt(), "NEWS_ITEM", item.getId(), now, resolutions, result);
                    }
                });
            } catch (RuntimeException ex) {
                available = false;
                log.warn("reaction news scan unavailable", ex);
            }
            try {
                for (RadarSignal signal : radar.findActiveSignals(now.minusHours(LOOKBACK_HOURS), 500)) {
                    admit(signal.getTitle(), signal.getContent(), signal.getUrl(), signal.getPublishedAt(),
                            signal.getFirstSeenAt(), "RADAR_SIGNAL", signal.getItemId(), now, resolutions, result);
                }
            } catch (RuntimeException ex) {
                available = false;
                log.warn("reaction radar scan unavailable", ex);
            }
            available = available && ProviderCallDeadline.remainingMillis() > 0
                    && resolutions.values().stream().noneMatch(value -> value.getStatus() == ReactionResolutionStatus.LOOKUP_UNAVAILABLE);
        }
        result.setLastCompletedAt(now);
        result.setMessage(available ? "已核对近期公司业绩与合同事件"
                : "部分来源或公司身份暂未核对，后续会重读近期资讯");
        return result;
    }

    private void admit(String title, String summary, String url, LocalDateTime publishedAt,
                       LocalDateTime firstSeen, String origin, String key, LocalDateTime now,
                       Map<List<String>, ReactionStockResolution> resolutions, ReactionDiscoveryStatus result) {
        if (publishedAt == null || publishedAt.isAfter(now) || publishedAt.isBefore(now.minusHours(LOOKBACK_HOURS))) {
            return;
        }
        // 关联与事件分类必须来自同一条直接标题，不能用正文里提及的另一家公司补主体。
        var decision = rules.evaluate(title);
        if (!decision.getSubtype().isSpecific() || decision.getSubjects().isEmpty()) {
            return;
        }
        String stableKey = key == null || key.isBlank() ? url : key;
        if (stableKey == null || stableKey.isBlank() || repository.hasResolvedOrigin(origin, stableKey, title, summary)) {
            return;
        }
        var subjects = List.copyOf(decision.getSubjects());
        var resolution = resolutions.computeIfAbsent(subjects, ignored -> resolver.resolveDirect(decision));
        if (resolution.getMatches().isEmpty()) {
            // 暂缺数据的新闻仍在原始资讯中，下一轮在窗口内重读，不创建观察草稿。
            return;
        }
        String mergeKey = decision.getMergeAnchor() != null ? "ANNOUNCEMENT:" + decision.getMergeAnchor()
                : publishedAt.toLocalDate() + "|" + decision.getFact().replaceAll("\\s", "");
        String identity = "EVENT:" + DigestUtils.md5DigestAsHex(mergeKey.getBytes(StandardCharsets.UTF_8));
        List<ReactionSample> samples = new ArrayList<>();
        for (ReactionStockMatch match : resolution.getMatches()) {
            ReactionSample sample = new ReactionSample();
            sample.setSourceIdentity(identity);
            sample.setSourceOriginType(origin);
            sample.setSourceOriginKey(stableKey);
            sample.setTitle(title);
            sample.setSummary(summary);
            sample.setSourceUrl(url);
            sample.setPublishedAt(publishedAt);
            sample.setOccurredDate(publishedAt.toLocalDate());
            sample.setFirstCapturedAt(firstSeen == null ? now : firstSeen);
            sample.setRegisteredAt(now);
            sample.setHistoricalBackfill(publishedAt.toLocalDate().isBefore(now.toLocalDate()));
            sample.setAutomatic(true);
            sample.setEventType(decision.getEventType());
            sample.setEventSubtype(decision.getSubtype());
            sample.setRuleVersion(decision.getRuleVersion());
            sample.setRuleEvidence(decision.getEvidence());
            sample.setFact(decision.getFact());
            sample.setInstrumentCode(match.getCode());
            sample.setInstrumentName(match.getName());
            sample.setState(ReactionSampleState.OBSERVING);
            sample.setResolutionStatus(ReactionResolutionStatus.RESOLVED);
            sample.setRelationNote(resolution.getEvidence() + "；来源时间不代表首次公告时间。");
            samples.add(sample);
        }
        int created = repository.captureResolvedSource(samples);
        if (created > 0) {
            result.setCaptured(result.getCaptured() + 1);
            result.setResolved(result.getResolved() + created);
        }
    }
}
