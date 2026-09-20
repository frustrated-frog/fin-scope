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
        for (ReactionSample draft : repository.findUnresolved(retryUnresolved ? now.plusSeconds(1) : now.minusHours(6), 10)) {
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
        var decision = new com.finscope.domain.investmentobservation.ReactionEventRules().evaluate(title);
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
        if (draft.getPublishedAt() == null) {
            draft.setDiscoveryIssue("来源未提供公开时刻，已保留事件；暂不能对齐精确行情窗口");
            repository.saveDraft(draft);
            return 0;
        }
        List<ReactionStockMatch> matches = resolver.resolve(draft.getTitle());
        if (matches.isEmpty()) {
            draft.setDiscoveryIssue("尚未可靠关联A股公司，系统每6小时重试；无需填写表单即可阅读事件");
            repository.saveDraft(draft);
            return 0;
        }
        List<ReactionSample> samples = new java.util.ArrayList<>();
        // 每家公司保留独立价格路径，不能因为其中一只上涨才事后加入。
        for (ReactionStockMatch match : matches) {
            ReactionSample sample = copy(draft);
            sample.setInstrumentCode(match.getCode());
            sample.setInstrumentName(match.getName());
            sample.setState(ReactionSampleState.OBSERVING);
            sample.setDiscoveryIssue(null);
            sample.setRelationNote("标题明确出现“" + match.getName() + "”，代码经标的资料或行情名称核对；以来源发布时刻对齐，不代表已核实为公司首次公告。");
            samples.add(sample);
        }
        // 去掉仅用于补全的无股票占位项；保留归档意味着用户主动忽略，不再重新创建。
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
        var decision = new com.finscope.domain.investmentobservation.ReactionEventRules().evaluate(draft.getTitle());
        sample.setEventSubtype(decision.getSubtype());
        sample.setRuleVersion(decision.getRuleVersion());
        sample.setRuleEvidence(decision.getEvidence());
        sample.setFact(decision.getFact());
        return sample;
    }

}
