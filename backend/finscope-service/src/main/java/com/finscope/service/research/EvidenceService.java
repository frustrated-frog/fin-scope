package com.finscope.service.research;

import com.finscope.common.util.StringUtils;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.source.Source;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class EvidenceService {
    private static final Pattern DATA_PATTERN = Pattern.compile(".*(\\d|%|％|亿美元|亿元|万亿|bp|bps|million|billion|trillion).*",
            Pattern.CASE_INSENSITIVE);

    private final EvidenceItemRepository evidenceItemRepository;
    private final SourceRepository sourceRepository;

    public EvidenceService(EvidenceItemRepository evidenceItemRepository, SourceRepository sourceRepository) {
        this.evidenceItemRepository = evidenceItemRepository;
        this.sourceRepository = sourceRepository;
    }

    public int capture(EventCluster event, Article article) {
        EvidenceItem item = new EvidenceItem();
        item.setEventId(event.getId());
        item.setArticleId(article.getId());
        item.setSourceTier(resolveSourceTier(article));
        item.setEvidenceType(resolveEvidenceType(article));
        item.setClaim(resolveClaim(article));
        item.setConfidence(resolveConfidence(item.getSourceTier()));
        evidenceItemRepository.save(item);
        return evidenceItemRepository.countByEventId(event.getId());
    }

    public List<EvidenceItem> listByEventId(Long eventId) {
        return evidenceItemRepository.findByEventId(eventId);
    }

    public List<EvidenceItem> listAll() {
        return evidenceItemRepository.findAll();
    }

    public List<EvidenceItem> listAll(Long eventId, String sourceTier, String evidenceType, Integer minConfidence) {
        return evidenceItemRepository.findAll().stream()
                .filter(item -> eventId == null || eventId.equals(item.getEventId()))
                .filter(item -> matches(item.getSourceTier(), sourceTier))
                .filter(item -> matches(item.getEvidenceType(), evidenceType))
                .filter(item -> minConfidence == null || value(item.getConfidence()) >= minConfidence)
                .collect(Collectors.toList());
    }

    public EvidenceItem detail(Long id) {
        return evidenceItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + id));
    }

    private String resolveSourceTier(Article article) {
        String sourceName = StringUtils.firstNonBlank(article.getSourceName(), "").toLowerCase(Locale.ROOT);
        if (containsAny(sourceName, "reuters", "bloomberg", "cnbc", "financial times", "wsj", "techcrunch", "the verge")) {
            return ResearchEnums.SOURCE_TIER_MEDIA;
        }
        if (containsAny(sourceName, "fed", "美联储", "pboc", "pbo c", "央行", "人民银行", "中国人民银行",
                "sec", "hkex", "国务院", "统计局")) {
            return ResearchEnums.SOURCE_TIER_REGULATOR;
        }
        if (containsAny(sourceName, "ir", "investor relations", "company", "公司公告")) {
            return ResearchEnums.SOURCE_TIER_COMPANY;
        }
        if (containsAny(sourceName, "x", "twitter")) {
            return ResearchEnums.SOURCE_TIER_SOCIAL;
        }
        if (article.getSourceId() != null) {
            Source source = sourceRepository.findById(article.getSourceId()).orElse(null);
            if (source != null) {
                if (source.getCredibility() >= 5) {
                    return ResearchEnums.SOURCE_TIER_REGULATOR;
                }
                if (source.getCredibility() >= 4) {
                    return ResearchEnums.SOURCE_TIER_MEDIA;
                }
            }
        }
        return ResearchEnums.SOURCE_TIER_UNKNOWN;
    }

    private String resolveEvidenceType(Article article) {
        String text = searchable(article);
        if (containsAny(text, "宣布", "表示", "提交", "批准", "获批", "发布", "上线", "推出", "上调", "下调", "开展", "实施")) {
            return ResearchEnums.EVIDENCE_TIMELINE;
        }
        if (DATA_PATTERN.matcher(text).find()) {
            return ResearchEnums.EVIDENCE_DATA;
        }
        return ResearchEnums.EVIDENCE_FACT;
    }

    private String resolveClaim(Article article) {
        String text = StringUtils.firstNonBlank(article.getSummary(), firstSentence(article.getBody()), article.getTitle(), "未提取到证据");
        return limit(text.replaceAll("\\s+", " ").trim(), 140);
    }

    private int resolveConfidence(String sourceTier) {
        if (ResearchEnums.SOURCE_TIER_REGULATOR.equals(sourceTier) || ResearchEnums.SOURCE_TIER_OFFICIAL.equals(sourceTier)) {
            return 90;
        }
        if (ResearchEnums.SOURCE_TIER_COMPANY.equals(sourceTier)) {
            return 85;
        }
        if (ResearchEnums.SOURCE_TIER_MEDIA.equals(sourceTier)) {
            return 75;
        }
        if (ResearchEnums.SOURCE_TIER_SOCIAL.equals(sourceTier)) {
            return 50;
        }
        return 60;
    }

    private String searchable(Article article) {
        return (StringUtils.firstNonBlank(article.getTitle(), "") + " "
                + StringUtils.firstNonBlank(article.getSummary(), "") + " "
                + StringUtils.firstNonBlank(article.getBody(), "")).toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String firstSentence(String body) {
        String text = StringUtils.firstNonBlank(body, "").trim();
        if (text.isEmpty()) {
            return "";
        }
        String[] parts = text.split("[。！？!?\\n]");
        return parts.length == 0 ? text : parts[0];
    }

    private String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private boolean matches(String actual, String expected) {
        if (StringUtils.isBlank(expected)) {
            return true;
        }
        return StringUtils.firstNonBlank(actual, "").equalsIgnoreCase(expected.trim());
    }

    private int value(Integer confidence) {
        return confidence == null ? 0 : confidence;
    }
}
