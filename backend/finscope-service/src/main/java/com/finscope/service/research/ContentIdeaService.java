package com.finscope.service.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.util.StringUtils;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.research.ContentIdeaRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.response.PageResponse;
import com.finscope.domain.research.EvidenceItem;
import com.finscope.domain.research.ContentIdea;
import com.finscope.domain.research.EventCluster;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ContentIdeaService {
    private static final Set<String> VALID_STATUSES = new LinkedHashSet<String>(Arrays.asList(
            ResearchEnums.CONTENT_STATUS_IDEA,
            ResearchEnums.CONTENT_STATUS_DRAFTING,
            ResearchEnums.CONTENT_STATUS_READY,
            ResearchEnums.CONTENT_STATUS_PUBLISHED,
            ResearchEnums.CONTENT_STATUS_ARCHIVED));

    @Resource
    private ContentIdeaRepository contentIdeaRepository;
    @Resource
    private EvidenceService evidenceService;
    @Resource
    private AgentRunRepository agentRunRepository;
    @Resource
    private LlmChatClient llmChatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void generateIfAbsent(EventCluster event, Article article, boolean meaningfulUpdate) {
        if (!meaningfulUpdate || event == null || event.getId() == null) {
            return;
        }
        if (contentIdeaRepository.countByEventId(event.getId()) > 0) {
            return;
        }
        List<EvidenceItem> evidenceItems = event.getId() == null
                ? Collections.<EvidenceItem>emptyList()
                : evidenceService.listByEventId(event.getId());
        long start = System.currentTimeMillis();
        List<ContentIdea> ideas = generateWithAgent(event, article, evidenceItems, start);
        String status = "SUCCESS";
        if (ideas.isEmpty()) {
            ideas = buildIdeas(event, article, evidenceItems);
            status = "FALLBACK";
        }
        for (ContentIdea idea : ideas) {
            contentIdeaRepository.save(idea);
        }
        agentRunRepository.record(ResearchRunContext.currentRunId(), event.getId(), article == null ? null : article.getId(),
                "content-idea-generate", status,
                "eventId=" + event.getId() + ", theme=" + event.getThemeCode(),
                "ideas=" + ideas.size(), null, System.currentTimeMillis() - start);
    }

    private List<ContentIdea> generateWithAgent(EventCluster event,
                                                Article article,
                                                List<EvidenceItem> evidenceItems,
                                                long start) {
        if (ResearchRunContext.isBatchResearch() || llmChatClient == null || !llmChatClient.isConfigured()) {
            return Collections.emptyList();
        }
        String prompt = contentPrompt(event, article, evidenceItems);
        try {
            String raw = llmChatClient.complete(contentSystemPrompt(), prompt);
            List<ContentIdea> ideas = parseIdeas(raw, event);
            if (ideas.isEmpty()) {
                agentRunRepository.record(ResearchRunContext.currentRunId(), event.getId(), article == null ? null : article.getId(),
                        "content-idea-generate", "REJECTED", prompt, raw, "No valid content idea",
                        System.currentTimeMillis() - start);
            }
            return ideas;
        } catch (Exception ex) {
            agentRunRepository.record(ResearchRunContext.currentRunId(), event.getId(), article == null ? null : article.getId(),
                    "content-idea-generate", "FAILED", prompt, null, ex.getMessage(), System.currentTimeMillis() - start);
            return Collections.emptyList();
        }
    }

    public List<ContentIdea> list() {
        return contentIdeaRepository.findAll();
    }

    public PageResponse<ContentIdea> listPaged(int page, int pageSize) {
        return PageResponse.of(contentIdeaRepository.findAllPaged(page, pageSize),
                contentIdeaRepository.countAll(), page, pageSize);
    }

    public List<ContentIdea> listByEventId(Long eventId) {
        return contentIdeaRepository.findByEventId(eventId);
    }

    public ContentIdea detail(Long id) {
        return contentIdeaRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Content idea not found: " + id));
    }

    public ContentIdea updateStatus(Long id, String status) {
        ContentIdea existing = detail(id);
        String normalizedStatus = normalizeStatus(status);
        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "Unsupported content idea status: " + status);
        }
        return contentIdeaRepository.updateStatus(existing.getId(), normalizedStatus);
    }

    private List<ContentIdea> parseIdeas(String raw, EventCluster event) throws Exception {
        JsonNode root = objectMapper.readTree(extractJson(raw));
        JsonNode ideasNode = root.isArray() ? root : root.path("ideas");
        List<ContentIdea> ideas = new ArrayList<ContentIdea>();
        if (!ideasNode.isArray()) {
            return ideas;
        }
        for (JsonNode node : ideasNode) {
            String title = text(node, "title", "");
            String angle = text(node, "angle", "");
            if (StringUtils.isBlank(title) || StringUtils.isBlank(angle)) {
                continue;
            }
            ideas.add(idea(event,
                    limit(title, 120),
                    limit(angle, 220),
                    validFormat(text(node, "format", ResearchEnums.CONTENT_FORMAT_LONG_ARTICLE)),
                    limit(text(node, "audience", "金融学习和内容创作者"), 120),
                    clamp(node.path("score").asInt(75)),
                    limit(text(node, "scoreReason", "证据和学习价值较强。"), 180),
                    limit(text(node, "outline", "1. 事件是什么\n2. 变量是什么\n3. 如何跟踪"), 260)));
            if (ideas.size() >= 3) {
                break;
            }
        }
        return ideas;
    }

    private String contentSystemPrompt() {
        return "你是 FinScope 内容选题 Agent。基于事件和证据生成教育型财经内容选题。"
                + "只返回 JSON，不做买卖建议，不制造没有证据的事实。";
    }

    private String contentPrompt(EventCluster event, Article article, List<EvidenceItem> evidenceItems) {
        StringBuilder builder = new StringBuilder();
        builder.append("输出格式:{\"ideas\":[{\"title\":\"标题\",\"angle\":\"角度\",")
                .append("\"format\":\"LONG_ARTICLE|SHORT_VIDEO|PODCAST|X_THREAD|XIAOHONGSHU_NOTE\",")
                .append("\"audience\":\"受众\",\"score\":0-100,\"scoreReason\":\"评分理由\",\"outline\":\"1. ...\"}]}\n");
        builder.append("事件:").append(event.getCanonicalTitle()).append("\n");
        builder.append("主题:").append(event.getThemeCode()).append("\n");
        builder.append("摘要:").append(event.getSummary()).append("\n");
        if (article != null) {
            builder.append("文章:").append(article.getTitle()).append("\n");
        }
        for (EvidenceItem item : evidenceItems) {
            builder.append("证据:").append(item.getEvidenceType()).append("/").append(item.getSourceTier())
                    .append(" ").append(item.getClaim()).append("\n");
        }
        return builder.toString();
    }

    private List<ContentIdea> buildIdeas(EventCluster event, Article article, List<EvidenceItem> evidenceItems) {
        String eventTitle = StringUtils.firstNonBlank(event.getCanonicalTitle(), article == null ? null : article.getTitle(), "这个事件");
        ResearchSignalSnapshot signals = ResearchSignalSnapshot.from(event, article, evidenceItems);
        int evidenceStrength = signals.evidenceStrength();
        if (ResearchEnums.THEME_CHINA_MACRO.equals(event.getThemeCode())) {
            if (signals.policySignal()) {
                return Arrays.asList(
                        idea(event,
                                eventTitle + "：一次 MLF 降息到底在传递什么政策信号？",
                                "从 MLF、LPR 和银行负债成本出发，把政策工具如何传导到信用和资产定价讲清楚。",
                                ResearchEnums.CONTENT_FORMAT_LONG_ARTICLE,
                                "想把宏观政策看懂而不是只背 headline 的学习型读者",
                                score(82, 91, 80, 88, 84, evidenceStrength),
                                "政策信号明确，且能沉淀成理解货币政策工具的长期框架。",
                                "1. 先分清 MLF 和 LPR\n2. 再画传导链\n3. 最后看资产价格怎么反应"),
                        idea(event,
                                "看懂" + eventTitle + "，先别急着只看降息两个字",
                                "把政策工具、流动性价格和信用扩张拆成一条可复用的观察链。",
                                ResearchEnums.CONTENT_FORMAT_X_THREAD,
                                "需要快速建立政策观察框架的财经内容读者",
                                score(86, 83, 87, 79, 84, evidenceStrength),
                                "政策工具天然适合做解释型内容，时效性和教育性都强。",
                                "1. 工具是什么\n2. 信号是什么\n3. 下一步该盯什么"));
            }
            if (signals.inflationSignal()) {
                return Arrays.asList(
                        idea(event,
                                eventTitle + "为什么会立刻改写市场的降息定价？",
                                "用通胀数据、实际利率和估值折现率解释宏观数据为什么能瞬间改变市场预期。",
                                ResearchEnums.CONTENT_FORMAT_LONG_ARTICLE,
                                "想建立宏观数据解读能力的投资学习者",
                                score(84, 88, 85, 87, 85, evidenceStrength),
                                "数据型事件兼具时效性和框架价值，适合沉淀成方法论。 ",
                                "1. 数据和预期差\n2. 实际利率怎么动\n3. 风险资产为什么一起反应"));
            }
            return Arrays.asList(
                    idea(event,
                            "为什么" + eventTitle + "会先推升黄金？",
                            "用利率预期、实际利率和资金流解释宏观事件如何提前反映到黄金定价。",
                            ResearchEnums.CONTENT_FORMAT_X_THREAD,
                            "想系统理解宏观资产定价的学习型读者",
                            score(88, 84, 86, 82, 85, evidenceStrength),
                            "事件具备强时效性，也能沉淀成长期有效的宏观框架。",
                            "1. 先拆政策预期\n2. 再看实际利率\n3. 最后看资金为什么先去黄金"),
                    idea(event,
                            eventTitle + "背后最值得补的三个宏观框架",
                            "把新闻追踪转成可复用的宏观指标观察框架。",
                            ResearchEnums.CONTENT_FORMAT_LONG_ARTICLE,
                            "希望把新闻看懂而不是只记结论的投资学习者",
                            score(83, 90, 76, 88, 86, evidenceStrength),
                            "知识密度和长期价值都高，适合沉淀成长文。",
                            "1. 指标怎么选\n2. 传导链路怎么画\n3. 未来怎么跟踪"));
        }
        if (ResearchEnums.THEME_AI_STARTUP.equals(event.getThemeCode())) {
            if (signals.aiFundingSignal()) {
                return Arrays.asList(
                        idea(event,
                                eventTitle + "反映的是 AI 融资回暖，还是头部公司虹吸？",
                                "从融资环境、商业化质量和资本偏好三个维度拆 AI 融资新闻。",
                                ResearchEnums.CONTENT_FORMAT_PODCAST,
                                "关注 AI 创业、融资和赛道演化的学习者",
                                score(82, 86, 81, 80, 88, evidenceStrength),
                                "融资新闻讨论度高，但要靠结构化拆解才能变成长期内容资产。",
                                "1. 钱为什么投进来\n2. 商业化有没有跟上\n3. 普通创业者该怎么看"));
            }
            if (signals.aiProductSignal() || signals.aiEcosystemSignal()) {
                return Arrays.asList(
                        idea(event,
                                eventTitle + "说明 AI 产品竞争已经卷到哪一层了？",
                                "把模型能力、工作流封装和开发者生态拆成三个竞争层次。",
                                ResearchEnums.CONTENT_FORMAT_LONG_ARTICLE,
                                "关注 AI 产品和开发者生态的学习型创作者",
                                score(85, 87, 83, 84, 88, evidenceStrength),
                                "兼顾热点解释和长期产品判断，适合做主线内容。",
                                "1. 产品层看什么\n2. 生态层看什么\n3. 护城河会落在哪"));
            }
            return Arrays.asList(
                    idea(event,
                            eventTitle + "说明了 AI 创业该押产品、流量还是生态？",
                            "从产品形态、技术栈和商业化视角拆 AI 事件。",
                            ResearchEnums.CONTENT_FORMAT_PODCAST,
                            "关注 AI 创业和独立开发的学习者",
                            score(84, 82, 82, 78, 88, evidenceStrength),
                            "匹配用户长期内容定位，也适合口播表达。",
                            "1. 事件是什么\n2. 创业含义是什么\n3. 普通人能借什么力"));
        }
        return Arrays.asList(
                idea(event,
                        "怎么把" + eventTitle + "讲成一个能复用的投资认知？",
                        "把单次事件转成结构化理解，不停留在资讯复述。",
                        ResearchEnums.CONTENT_FORMAT_LONG_ARTICLE,
                        "想建立长期金融认知框架的读者",
                        score(80, 82, 72, 85, 84, evidenceStrength),
                        "适合作为学习型内容母题。",
                        "1. 事件拆解\n2. 变量抽象\n3. 方法复用"));
    }

    private ContentIdea idea(EventCluster event, String title, String angle, String format, String audience,
                             int score, String scoreReason, String outline) {
        ContentIdea idea = new ContentIdea();
        idea.setEventId(event.getId());
        idea.setThemeCode(event.getThemeCode());
        idea.setTitle(title);
        idea.setAngle(angle);
        idea.setFormat(format);
        idea.setAudience(audience);
        idea.setScore(score);
        idea.setScoreReason(scoreReason);
        idea.setOutline(outline);
        idea.setStatus(ResearchEnums.CONTENT_STATUS_IDEA);
        return idea;
    }

    private int score(int understandability, int knowledgeDensity, int timeliness, int longTailValue,
                      int personalFit, int evidenceStrength) {
        double weighted = understandability * 0.20
                + knowledgeDensity * 0.20
                + timeliness * 0.15
                + longTailValue * 0.20
                + personalFit * 0.15
                + evidenceStrength * 0.10;
        return clamp((int) Math.round(weighted));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
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

    private String validFormat(String value) {
        String normalized = normalizeStatus(value);
        if (ResearchEnums.CONTENT_FORMAT_LONG_ARTICLE.equals(normalized)
                || ResearchEnums.CONTENT_FORMAT_SHORT_VIDEO.equals(normalized)
                || ResearchEnums.CONTENT_FORMAT_PODCAST.equals(normalized)
                || ResearchEnums.CONTENT_FORMAT_X_THREAD.equals(normalized)
                || ResearchEnums.CONTENT_FORMAT_XIAOHONGSHU_NOTE.equals(normalized)) {
            return normalized;
        }
        return ResearchEnums.CONTENT_FORMAT_LONG_ARTICLE;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
