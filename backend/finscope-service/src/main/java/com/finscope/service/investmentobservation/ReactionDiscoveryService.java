package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.investmentobservation.ReactionDiscoveryStatus;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.domain.investmentobservation.ReactionStockMatch;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.rpc.research.material.ResearchMaterialRequest;
import com.finscope.service.research.material.ResearchMaterialGateway;
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
    private ResearchMaterialGateway materials;
    @Resource
    private RadarRepository radar;
    @Resource
    private ReactionSampleRepository repository;
    @Resource
    private ReactionStockResolver resolver;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    /** 先持久化全部候选，再有界补全；行情或模型故障不丢失新闻快照。 */
    public ReactionDiscoveryStatus discover() {
        LocalDateTime now = LocalDateTime.now(clock);
        ReactionDiscoveryStatus result = new ReactionDiscoveryStatus();
        boolean sourcesAvailable = captureSources(now, result);
        for (ReactionSample draft : repository.findUnresolved(now.minusHours(6), 10)) {
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
            var news = materials.readNewsFlashSources(new ResearchMaterialRequest("000001", "", 50));
            available = news.getWarnings().isEmpty();
            for (ResearchMaterial item : news.getMaterials()) {
                result.setCaptured(result.getCaptured() + capture(item.getTitle(), item.getContent(), item.getUrl(),
                        item.getPublishedAt(), now, "NEWS_ITEM", item.getProviderCode() + ":" + item.getExternalId(), now));
            }
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
        ReactionEventType type = classify(title);
        if (type == null || (publishedAt != null && (publishedAt.isAfter(now)
                || publishedAt.isBefore(now.minusHours(36))))) {
            return 0;
        }
        if (key != null && !key.isBlank() && publishedAt != null) {
            var previous = repository.findUnresolvedOrigin(origin, key);
            if (previous.isPresent() && previous.get().getPublishedAt() == null) {
                ReactionSample draft = previous.get();
                draft.setPublishedAt(publishedAt);
                draft.setOccurredDate(publishedAt.toLocalDate());
                draft.setEnrichmentAttemptAt(null);
                draft.setHistoricalBackfill(publishedAt.toLocalDate().isBefore(draft.getRegisteredAt().toLocalDate()));
                repository.saveDraft(draft);
                return 0;
            }
        }
        // 同日完全相同标题视为转载；不同标题保守保留，避免把后续公告合并掉。
        String identity = "NEWS:" + DigestUtils.md5DigestAsHex(((publishedAt == null ? origin + ":" + key : publishedAt.toLocalDate()) + "|"
                + title.replaceAll("[\\s\\p{Punct}，。！？：；【】（）]", "")).getBytes(StandardCharsets.UTF_8));
        if (!repository.findByIdentity(identity).isEmpty()) {
            return 0;
        }
        ReactionSample sample = new ReactionSample();
        sample.setSourceIdentity(identity);
        sample.setSourceOriginType(origin);
        sample.setSourceOriginKey(key);
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
        sample.setDiscoveryIssue("已自动保存，正在识别直接涉及的A股公司");
        repository.create(sample);
        return 1;
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
        sample.setEventType(draft.getEventType());
        return sample;
    }

    private ReactionEventType classify(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        if (title.matches(".*(业绩|季报|年报|半年报|财报|净利润).*")) {
            return ReactionEventType.EARNINGS;
        }
        if (title.matches(".*(合同|订单|中标).*")) {
            return ReactionEventType.CONTRACT;
        }
        return null;
    }
}
