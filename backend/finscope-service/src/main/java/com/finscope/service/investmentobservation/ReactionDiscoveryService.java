package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.investmentobservation.ReactionDiscoveryStatus;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.domain.investmentobservation.ReactionStockMatch;
import com.finscope.domain.radar.RadarSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
public class ReactionDiscoveryService {
    @Resource
    private com.finscope.service.news.NewsWindowService materials;
    @Resource
    private RadarRepository radar;
    @Resource
    private ReactionSampleRepository repository;
    @Resource
    private ReactionStockResolver resolver;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    /** 先持久化全部候选，再有界补全；行情或模型故障不丢失新闻快照。 */
    public ReactionDiscoveryStatus discover() {
        return discover(false);
    }

    public ReactionDiscoveryStatus discover(boolean retryUnresolved) {
        LocalDateTime now = LocalDateTime.now(clock);
        ReactionDiscoveryStatus result = new ReactionDiscoveryStatus();
        boolean sourcesAvailable = captureSources(now, result);
        for (ReactionSample draft : repository.findUnresolved(retryUnresolved ? now.plusSeconds(1) : now.minusMinutes(30), 30)) {
            try {
                result.setResolved(result.getResolved() + enrich(draft, now));
            } catch (RuntimeException ex) {
                log.warn("reaction enrichment failed sampleId={}", draft.getId(), ex);
                draft.setEnrichmentAttemptAt(now);
                draft.setDiscoveryIssue("关联补全暂不可用，系统稍后重试；可直接阅读来源");
                repository.saveDraft(draft);
            }
        }
        result.setLastCompletedAt(now);
        result.setMessage(sourcesAvailable ? "自动读取新闻与雷达，已关联样本持续跟踪行情"
                : "部分新闻来源尚未同步；已保留现有样本，等待下一轮自动读取");
        return result;
    }

    private boolean captureSources(LocalDateTime now, ReactionDiscoveryStatus result) {
        boolean available = true;
        try {
            materials.scan(batch -> {
                for (var item : batch) {
                    result.setCaptured(result.getCaptured() + capture(item.getTitle(), item.getContent(), item.getUrl(),
                            item.getPublishedAt(), item.getFirstSeenAt(), "NEWS_ITEM", item.getId(), now));
                }
            });
        } catch (RuntimeException ex) {
            available = false;
            log.warn("reaction news snapshot unavailable; retained drafts will still be enriched", ex);
        }
        try {
            for (RadarSignal signal : radar.findActiveSignals(now.minusHours(36), 500)) {
                result.setCaptured(result.getCaptured() + capture(signal.getTitle(), signal.getContent(), signal.getUrl(),
                        signal.getPublishedAt(), signal.getFirstSeenAt(), "RADAR_SIGNAL", signal.getItemId(), now));
            }
        } catch (RuntimeException ex) {
            available = false;
            log.warn("reaction radar snapshot unavailable; retained drafts will still be enriched", ex);
        }
        return available;
    }

    private int capture(String title, String summary, String url, LocalDateTime publishedAt,
                        LocalDateTime firstSeen, String origin, String key, LocalDateTime now) {
        var decision = new com.finscope.domain.investmentobservation.ReactionEventRules().evaluateMaterial(title, summary);
        ReactionEventType type = decision.getEventType();
        if (type == null || (publishedAt != null && (publishedAt.isAfter(now)
                || publishedAt.isBefore(now.minusHours(36))))) {
            return 0;
        }
        String stableKey = key == null || key.isBlank() ? (url == null || url.isBlank() ? title : url) : key;
        String mergeKey = decision.getMergeAnchor() != null ? "ANNOUNCEMENT:" + decision.getMergeAnchor()
                : (publishedAt == null ? origin + ":" + stableKey : publishedAt.toLocalDate()) + "|" + title.replaceAll("\\s", "");
        String identity = "EVENT:" + DigestUtils.md5DigestAsHex(mergeKey.getBytes(StandardCharsets.UTF_8));
        ReactionSample sample = new ReactionSample();
        sample.setSourceIdentity(identity);
        sample.setSourceOriginType(origin);
        sample.setSourceOriginKey(stableKey);
        sample.setTitle(title);
        sample.setSummary(summary);
        sample.setSourceUrl(url);
        sample.setPublishedAt(publishedAt);
        sample.setOccurredDate(publishedAt == null ? null : publishedAt.toLocalDate());
        sample.setFirstCapturedAt(firstSeen == null ? now : firstSeen);
        sample.setRegisteredAt(now);
        sample.setHistoricalBackfill(publishedAt != null && publishedAt.toLocalDate().isBefore(now.toLocalDate()));
        sample.setAutomatic(true);
        sample.setEventType(type);
        sample.setEventSubtype(decision.getSubtype());
        sample.setRuleVersion(decision.getRuleVersion());
        sample.setRuleEvidence(decision.getEvidence());
        sample.setFact(decision.getFact());
        sample.setDiscoveryIssue("已自动保存，正在识别直接涉及的A股公司");
        return repository.captureSource(sample) ? 1 : 0;
    }

    private int enrich(ReactionSample draft, LocalDateTime now) {
        draft.setEnrichmentAttemptAt(now);
        var resolution = resolver.resolve(draft.getTitle(), draft.getSummary());
        draft.setResolutionStatus(resolution.getStatus());
        List<ReactionStockMatch> matches = resolution.getMatches();
        if (matches.isEmpty()) {
            draft.setDiscoveryIssue(switch (resolution.getStatus()) {
                case LOOKUP_UNAVAILABLE -> "证券关联服务暂不可用，稍后自动重试";
                case AMBIGUOUS -> "公司身份有歧义，等待更明确的代码或材料";
                default -> "材料未发现可核验的直接关联 A 股公司，等待来源补充";
            });
            repository.saveDraft(draft);
            return 0;
        }
        List<ReactionSample> samples = new java.util.ArrayList<>();
        // 每家公司保留独立价格路径，不能因为其中一只上涨才事后加入。
        for (ReactionStockMatch match : matches) {
            if (draft.getInstrumentCode() != null && !draft.getInstrumentCode().isBlank()
                    && !draft.getInstrumentCode().equals(match.getCode())) {
                continue;
            }
            ReactionSample sample = copy(draft);
            sample.setInstrumentCode(match.getCode());
            sample.setInstrumentName(match.getName());
            sample.setState(draft.getPublishedAt() == null ? ReactionSampleState.DRAFT : ReactionSampleState.OBSERVING);
            sample.setResolutionStatus(draft.getPublishedAt() == null
                    ? com.finscope.common.enums.investmentobservation.ReactionResolutionStatus.TIME_MISSING
                    : com.finscope.common.enums.investmentobservation.ReactionResolutionStatus.RESOLVED);
            sample.setDiscoveryIssue(draft.getPublishedAt() == null ? "股票已关联，来源公开时间待补全" : null);
            sample.setEnrichmentAttemptAt(now);
            sample.setRelationNote(resolution.getEvidence() + "；来源时间不代表首次公告时间。");
            samples.add(sample);
        }
        // 第一只股票沿用草稿 ID，旧入口在自动补全后仍然有效。
        return repository.promoteDraft(draft, samples) ? samples.size() : 0;
    }

    private ReactionSample copy(ReactionSample draft) {
        ReactionSample sample = new ReactionSample();
        sample.setSourceIdentity(draft.getSourceIdentity());
        sample.setSourceOriginType(draft.getSourceOriginType());
        sample.setSourceOriginKey(draft.getSourceOriginKey());
        sample.setTitle(draft.getTitle());
        sample.setSummary(draft.getSummary());
        sample.setSourceUrl(draft.getSourceUrl());
        sample.setPublishedAt(draft.getPublishedAt());
        sample.setOccurredDate(draft.getOccurredDate());
        sample.setFirstCapturedAt(draft.getFirstCapturedAt());
        sample.setRegisteredAt(draft.getRegisteredAt());
        sample.setHistoricalBackfill(draft.isHistoricalBackfill());
        sample.setAutomatic(true);
        sample.setFollowed(draft.isFollowed());
        sample.setEventType(draft.getEventType());
        var decision = new com.finscope.domain.investmentobservation.ReactionEventRules().evaluateMaterial(draft.getTitle(), draft.getSummary());
        sample.setEventSubtype(decision.getSubtype());
        sample.setRuleVersion(decision.getRuleVersion());
        sample.setRuleEvidence(decision.getEvidence());
        sample.setFact(decision.getFact());
        return sample;
    }

}
