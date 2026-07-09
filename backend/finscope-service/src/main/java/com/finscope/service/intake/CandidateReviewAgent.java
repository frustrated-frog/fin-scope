package com.finscope.service.intake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.intake.CandidateReview;
import com.finscope.domain.intake.IntakeCandidate;
import com.finscope.domain.intake.IntakeEnums;
import com.finscope.rpc.llm.LlmChatClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class CandidateReviewAgent {
    private static final String NODE_NAME = "candidate-review";
    private static final int BODY_LIMIT = 5000;

    @Resource
    private LlmChatClient llmChatClient;
    @Resource
    private AgentRunRepository agentRunRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CandidateReview review(IntakeCandidate candidate) {
        long start = System.currentTimeMillis();
        String input = traceInput(candidate);
        if (llmChatClient == null || !llmChatClient.isConfigured()) {
            CandidateReview fallback = fallback(candidate);
            record("FALLBACK", input, toJson(fallback), "LLM_UNCONFIGURED", start);
            return fallback;
        }
        try {
            String raw = llmChatClient.complete(systemPrompt(), userPrompt(candidate));
            CandidateReview review = parse(raw, fallback(candidate));
            record("SUCCESS", input, raw, null, start);
            return review;
        } catch (Exception ex) {
            CandidateReview fallback = fallback(candidate);
            record("FALLBACK", input, toJson(fallback), ex.getMessage(), start);
            return fallback;
        }
    }

    public String reviewJson(CandidateReview review) {
        return toJson(review);
    }

    private CandidateReview parse(String raw, CandidateReview fallback) throws Exception {
        JsonNode root = objectMapper.readTree(extractJson(raw));
        CandidateReview review = new CandidateReview();
        review.setChineseTitle(text(root, "chineseTitle", fallback.getChineseTitle()));
        review.setDecisionSummary(text(root, "decisionSummary", fallback.getDecisionSummary()));
        review.setKeyFacts(list(root.get("keyFacts"), fallback.getKeyFacts()));
        review.setWhyItMatters(text(root, "whyItMatters", fallback.getWhyItMatters()));
        review.setNoveltyJudgment(text(root, "noveltyJudgment", fallback.getNoveltyJudgment()));
        review.setRiskFlags(list(root.get("riskFlags"), fallback.getRiskFlags()));
        review.setScore(Math.max(0, Math.min(100, root.path("score").asInt(fallback.getScore()))));
        review.setRecommendation(text(root, "recommendation", fallback.getRecommendation()));
        review.setReason(text(root, "reason", fallback.getReason()));
        return review;
    }

    private CandidateReview fallback(IntakeCandidate candidate) {
        String title = firstNonBlank(candidate.getOriginalTitle(), candidate.getOriginalUrl(), "未命名候选内容");
        String summary = firstNonBlank(candidate.getOriginalSummary(), firstSentence(candidate.getOriginalBody()), title);
        CandidateReview review = new CandidateReview();
        review.setChineseTitle(toChineseDecisionTitle(title));
        review.setDecisionSummary("值得复核：这条信息来自 " + firstNonBlank(candidate.getSourceName(), "未知来源")
                + "，核心内容是“" + limit(summary, 72) + "”，需要你判断是否进入文章库。");
        List<String> facts = new ArrayList<String>();
        facts.add(limit(summary, 90));
        if (!isBlank(candidate.getOriginalUrl())) {
            facts.add("原始链接：" + candidate.getOriginalUrl());
        }
        review.setKeyFacts(facts);
        review.setWhyItMatters(whyItMatters(candidate));
        review.setNoveltyJudgment("NEED_REVIEW");
        List<String> risks = new ArrayList<String>();
        if (candidate.getPublishedAt() == null) {
            risks.add("发布时间不确定");
        }
        if (candidate.getExtractionQualityScore() < 65) {
            risks.add("正文抽取质量偏低");
        }
        review.setRiskFlags(risks);
        review.setScore(score(candidate));
        review.setRecommendation(review.getScore() >= 70 ? IntakeEnums.AGENT_PROMOTABLE : IntakeEnums.AGENT_NEED_REVIEW);
        review.setReason("Fallback 依据来源可信度、正文长度、抽取质量和关键词进行保守评分。");
        return review;
    }

    private String whyItMatters(IntakeCandidate candidate) {
        String text = (firstNonBlank(candidate.getOriginalTitle(), "") + " "
                + firstNonBlank(candidate.getOriginalSummary(), "") + " "
                + firstNonBlank(candidate.getOriginalBody(), "")).toLowerCase();
        if (containsAny(text, "美联储", "fed", "降息", "利率", "通胀", "黄金")) {
            return "它可能影响利率预期、黄金、债券和权益风险偏好，是宏观研究中值得快速判断的信息。";
        }
        if (containsAny(text, "ai", "人工智能", "模型", "算力", "创业")) {
            return "它可能影响 AI 技术趋势、创业机会或后续学习主题，适合判断是否沉淀为研究文章。";
        }
        if (containsAny(text, "财报", "营收", "利润", "公司", "融资")) {
            return "它可能改变公司基本面或产业叙事，适合判断是否进入公司/行业研究池。";
        }
        return "它可能成为后续研究、简报或知识库的素材，需人工判断价值密度和相关性。";
    }

    private int score(IntakeCandidate candidate) {
        int score = 45;
        score += Math.min(25, Math.max(0, candidate.getExtractionQualityScore() / 4));
        int length = length(candidate.getOriginalSummary()) + length(candidate.getOriginalBody());
        if (length > 800) {
            score += 15;
        } else if (length > 240) {
            score += 10;
        } else if (length > 80) {
            score += 5;
        }
        String text = firstNonBlank(candidate.getOriginalTitle(), "") + " " + firstNonBlank(candidate.getOriginalSummary(), "");
        if (containsAny(text.toLowerCase(), "美联储", "fed", "ai", "人工智能", "财报", "政策", "监管")) {
            score += 8;
        }
        return Math.max(0, Math.min(100, score));
    }

    private String systemPrompt() {
        return "你是 FinScope 的信息摄入预审 Agent。你的任务不是写普通摘要，而是帮助用户判断候选内容是否值得人工入文章库。"
                + "必须使用中文输出，只返回 JSON，不要 Markdown，不要代码块。";
    }

    private String userPrompt(IntakeCandidate candidate) {
        return "请预审这条候选内容，输出字段：chineseTitle, decisionSummary, keyFacts, whyItMatters, "
                + "noveltyJudgment, riskFlags, score, recommendation, reason。\n\n"
                + "来源：" + safe(candidate.getSourceName()) + "\n"
                + "标题：" + safe(candidate.getOriginalTitle()) + "\n"
                + "URL：" + safe(candidate.getOriginalUrl()) + "\n"
                + "摘要：" + safe(candidate.getOriginalSummary()) + "\n"
                + "正文：" + limit(candidate.getOriginalBody(), BODY_LIMIT);
    }

    private String traceInput(IntakeCandidate candidate) {
        return "candidateId=" + candidate.getId()
                + "\nsource=" + safe(candidate.getSourceName())
                + "\ntitle=" + safe(candidate.getOriginalTitle());
    }

    private void record(String status, String input, String output, String errorMessage, long start) {
        agentRunRepository.record(NODE_NAME, status, input, output, errorMessage, System.currentTimeMillis() - start);
    }

    private String toJson(CandidateReview review) {
        try {
            return objectMapper.writeValueAsString(review);
        } catch (Exception ex) {
            return "{\"chineseTitle\":\"" + safe(review.getChineseTitle()) + "\"}";
        }
    }

    private List<String> list(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<String> values = new ArrayList<String>();
        for (JsonNode item : node) {
            if (!isBlank(item.asText())) {
                values.add(item.asText());
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || isBlank(value.asText()) ? fallback : value.asText();
    }

    private String extractJson(String value) {
        if (value == null) {
            return "{}";
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private String toChineseDecisionTitle(String title) {
        if (containsChinese(title)) {
            return title;
        }
        return "候选信息：" + title;
    }

    private String firstSentence(String value) {
        if (isBlank(value)) {
            return "";
        }
        String[] parts = value.trim().split("[。！？.!?]\\s*");
        return parts.length == 0 ? value.trim() : parts[0].trim();
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) {
            return false;
        }
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase()) || text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsChinese(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(value.charAt(i));
            if (Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(block)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private int length(String value) {
        return value == null ? 0 : value.trim().length();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
