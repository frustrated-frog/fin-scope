package com.finscope.service.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.util.StringUtils;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.source.Source;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    private final AgentRunRepository agentRunRepository;
    private final LlmChatClient llmChatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EvidenceService(EvidenceItemRepository evidenceItemRepository,
                           SourceRepository sourceRepository,
                           AgentRunRepository agentRunRepository,
                           LlmChatClient llmChatClient) {
        this.evidenceItemRepository = evidenceItemRepository;
        this.sourceRepository = sourceRepository;
        this.agentRunRepository = agentRunRepository;
        this.llmChatClient = llmChatClient;
    }

    public int capture(EventCluster event, Article article) {
        long start = System.currentTimeMillis();
        List<EvidenceItem> agentItems = extractWithAgent(event, article, start);
        if (!agentItems.isEmpty()) {
            for (EvidenceItem item : agentItems) {
                evidenceItemRepository.save(item);
            }
            return evidenceItemRepository.countByEventId(event.getId());
        }
        EvidenceItem item = new EvidenceItem();
        item.setEventId(event.getId());
        item.setArticleId(article.getId());
        item.setSourceTier(resolveSourceTier(article));
        item.setEvidenceType(resolveEvidenceType(article));
        item.setClaim(resolveClaim(article));
        item.setConfidence(resolveConfidence(item.getSourceTier()));
        EvidenceItem saved = evidenceItemRepository.save(item);
        int count = evidenceItemRepository.countByEventId(event.getId());
        agentRunRepository.record(ResearchRunContext.currentRunId(), event.getId(), article.getId(),
                "evidence-extract", "FALLBACK",
                "articleId=" + article.getId() + ", eventId=" + event.getId(),
                saved.getEvidenceType() + "/" + saved.getSourceTier() + ": " + saved.getClaim(),
                null, System.currentTimeMillis() - start);
        return count;
    }

    private List<EvidenceItem> extractWithAgent(EventCluster event, Article article, long start) {
        if (llmChatClient == null || !llmChatClient.isConfigured()) {
            return java.util.Collections.emptyList();
        }
        String input = evidencePrompt(event, article);
        try {
            String raw = llmChatClient.complete(evidenceSystemPrompt(), input);
            List<EvidenceItem> items = parseEvidence(raw, event, article);
            if (items.isEmpty()) {
                agentRunRepository.record(ResearchRunContext.currentRunId(), event.getId(), article.getId(),
                        "evidence-extract", "REJECTED", input, raw, "No valid evidence item",
                        System.currentTimeMillis() - start);
                return java.util.Collections.emptyList();
            }
            agentRunRepository.record(ResearchRunContext.currentRunId(), event.getId(), article.getId(),
                    "evidence-extract", "SUCCESS", input, raw, null, System.currentTimeMillis() - start);
            return items;
        } catch (Exception ex) {
            agentRunRepository.record(ResearchRunContext.currentRunId(), event.getId(), article.getId(),
                    "evidence-extract", "FAILED", input, null, ex.getMessage(), System.currentTimeMillis() - start);
            return java.util.Collections.emptyList();
        }
    }

    private List<EvidenceItem> parseEvidence(String raw, EventCluster event, Article article) throws Exception {
        JsonNode root = objectMapper.readTree(extractJson(raw));
        JsonNode itemsNode = root.isArray() ? root : root.path("items");
        List<EvidenceItem> items = new ArrayList<EvidenceItem>();
        String sourceTier = resolveSourceTier(article);
        if (!itemsNode.isArray()) {
            return items;
        }
        for (JsonNode node : itemsNode) {
            String claim = text(node, "claim", "");
            if (StringUtils.isBlank(claim)) {
                continue;
            }
            EvidenceItem item = new EvidenceItem();
            item.setEventId(event.getId());
            item.setArticleId(article.getId());
            item.setSourceTier(sourceTier);
            item.setEvidenceType(validEvidenceType(text(node, "evidenceType", resolveEvidenceType(article))));
            item.setClaim(limit(claim.replaceAll("\\s+", " ").trim(), 180));
            item.setConfidence(clamp(node.path("confidence").asInt(resolveConfidence(sourceTier))));
            items.add(item);
            if (items.size() >= 5) {
                break;
            }
        }
        return items;
    }

    private String evidenceSystemPrompt() {
        return "你是 FinScope 证据抽取 Agent。只基于输入文章抽取可引用事实，返回 JSON，不写 Markdown。"
                + "不要编造来源、URL、数字或投资建议。";
    }

    private String evidencePrompt(EventCluster event, Article article) {
        return "输出格式:{\"items\":[{\"evidenceType\":\"FACT|DATA|TIMELINE\",\"claim\":\"事实句\","
                + "\"confidence\":0-100}]}\n"
                + "事件:" + event.getCanonicalTitle() + "\n"
                + "来源:" + article.getSourceName() + "\n"
                + "标题:" + article.getTitle() + "\n"
                + "摘要:" + article.getSummary() + "\n"
                + "正文:" + limit(article.getBody(), 5000);
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

    private String extractJson(String raw) {
        String value = StringUtils.firstNonBlank(raw, "").trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        int objectStart = value.indexOf('{');
        int objectEnd = value.lastIndexOf('}');
        int arrayStart = value.indexOf('[');
        int arrayEnd = value.lastIndexOf(']');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return value.substring(objectStart, objectEnd + 1);
        }
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return value.substring(arrayStart, arrayEnd + 1);
        }
        return value;
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return StringUtils.isBlank(value) ? fallback : value.trim();
    }

    private String validEvidenceType(String value) {
        String normalized = StringUtils.firstNonBlank(value, "").trim().toUpperCase(Locale.ROOT);
        if (ResearchEnums.EVIDENCE_DATA.equals(normalized)
                || ResearchEnums.EVIDENCE_TIMELINE.equals(normalized)
                || ResearchEnums.EVIDENCE_FACT.equals(normalized)) {
            return normalized;
        }
        return ResearchEnums.EVIDENCE_FACT;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
