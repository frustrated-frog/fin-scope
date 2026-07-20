package com.finscope.service.brief;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.brief.BriefRepository;
import com.finscope.dao.insight.InsightCardRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.brief.Brief;
import com.finscope.domain.insight.InsightCard;
import com.finscope.domain.research.BriefResearchContext;
import com.finscope.rpc.llm.LlmChatClient;
import com.finscope.service.research.BriefResearchContextService;
import com.finscope.service.research.ResearchRunContext;
import com.finscope.service.vault.VaultWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BriefService {
    private static final String GENERATED_BRIEF_TITLE_PREFIX = "每日金融、投资、创业学习简报 - ";

    @Resource
    private ArticleRepository articleRepository;
    @Resource
    private BriefRepository briefRepository;
    @Resource
    private InsightCardRepository insightCardRepository;
    @Resource
    private BriefGenerator briefGenerator;
    @Resource
    private VaultWriter vaultWriter;
    @Resource
    private AgentRunRepository agentRunRepository;
    @Resource
    private BriefResearchContextService briefResearchContextService;
    @Resource
    private LlmChatClient llmChatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Brief generateToday() {
        return generate(LocalDate.now());
    }

    public List<Brief> list() {
        try {
            syncVaultBriefs();
        } catch (IllegalStateException ex) {
            log.warn("vault 简报索引失败，返回数据库已有简报列表 message={}", ex.getMessage());
            log.trace("vault 简报索引失败详情", ex);
        }
        return briefRepository.findAll();
    }

    public Brief detail(LocalDate date) {
        syncVaultBrief(date);
        return briefRepository.findByDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("简报不存在：" + date));
    }

    public BriefResearchContext researchContext(LocalDate date) {
        return briefResearchContextService.build(date);
    }

    public Brief generate(LocalDate date) {
        long start = System.currentTimeMillis();
        try {
            List<Article> articles = articleRepository.findByDate(date);
            List<Long> articleIds = articles.stream().map(Article::getId).collect(Collectors.toList());
            Map<Long, InsightCard> cardsByArticleId = insightCardRepository.findByArticleIds(articleIds);
            BriefResearchContext context = briefResearchContextService.build(date);
            String deterministicMarkdown = context.isEmpty()
                    ? briefGenerator.generate(date, articles, cardsByArticleId)
                    : briefGenerator.generate(date, articles, context);
            String markdown = synthesizeWithAgent(date, context, deterministicMarkdown);
            String synthStatus = deterministicMarkdown.equals(markdown) ? "FALLBACK" : "SUCCESS";
            Path markdownPath = vaultWriter.writeDailyBrief(date, markdown);
            Brief brief = new Brief();
            brief.setBriefDate(date);
            brief.setTitle(GENERATED_BRIEF_TITLE_PREFIX + date);
            brief.setContent(markdown);
            brief.setMarkdownPath(markdownPath.toString());
            Brief saved = briefRepository.upsert(brief);
            Long researchRunId = ResearchRunContext.currentRunId();
            agentRunRepository.record(researchRunId, null, null, "brief-synthesize", synthStatus,
                    "date=" + date + ", researchContextEmpty=" + context.isEmpty(),
                    "markdownChars=" + markdown.length(), null, System.currentTimeMillis() - start);
            agentRunRepository.record(researchRunId, null, null, "brief-generate", "SUCCESS", "date=" + date,
                    "articles=" + articles.size(), null, System.currentTimeMillis() - start);
            return saved;
        } catch (Exception ex) {
            Long researchRunId = ResearchRunContext.currentRunId();
            agentRunRepository.record(researchRunId, null, null, "brief-synthesize", "FAILED", "date=" + date,
                    null, ex.getMessage(), System.currentTimeMillis() - start);
            agentRunRepository.record(researchRunId, null, null, "brief-generate", "FAILED", "date=" + date,
                    null, ex.getMessage(), System.currentTimeMillis() - start);
            throw new IllegalStateException("Failed to generate brief", ex);
        }
    }

    private String synthesizeWithAgent(LocalDate date, BriefResearchContext context, String fallbackMarkdown) {
        if (context == null || context.isEmpty() || llmChatClient == null || !llmChatClient.isConfigured()) {
            return fallbackMarkdown;
        }
        try {
            String raw = llmChatClient.complete(briefSystemPrompt(), briefPrompt(date, fallbackMarkdown));
            String markdown = objectMapper.readTree(extractJson(raw)).path("markdown").asText("");
            if (isValidSynthesizedBrief(markdown)) {
                return markdown.trim();
            }
            return fallbackMarkdown;
        } catch (Exception ignored) {
            return fallbackMarkdown;
        }
    }

    private void syncVaultBriefs() {
        try {
            for (Path path : vaultWriter.listDailyBriefs()) {
                LocalDate date = parseBriefDate(path);
                if (date != null) {
                    syncVaultBrief(date);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to index daily briefs from vault", ex);
        }
    }

    private void syncVaultBrief(LocalDate date) {
        try {
            String markdown = vaultWriter.readDailyBrief(date);
            if (markdown == null || markdown.trim().isEmpty()) {
                return;
            }
            Brief brief = new Brief();
            brief.setBriefDate(date);
            brief.setTitle(extractTitle(markdown, date));
            brief.setContent(markdown);
            brief.setMarkdownPath(vaultWriter.dailyBriefPath(date).toString());
            briefRepository.upsert(brief);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to index daily brief: " + date, ex);
        }
    }

    private LocalDate parseBriefDate(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(".md")) {
            return null;
        }
        try {
            return LocalDate.parse(fileName.substring(0, fileName.length() - 3));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractTitle(String markdown, LocalDate date) {
        String[] lines = markdown.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        return GENERATED_BRIEF_TITLE_PREFIX + date;
    }

    private String briefSystemPrompt() {
        return "你是 FinScope 简报组织 Agent。你只能基于输入 Markdown 做结构和表达优化，"
                + "不得新增事实、来源、数字或投资建议。只返回 JSON:{\"markdown\":\"...\"}";
    }

    private String briefPrompt(LocalDate date, String markdown) {
        return "日期:" + date + "\n"
                + "要求:保留所有二级标题、证据和“不构成投资建议”提示；不要新增任何未出现的事实。\n"
                + "待优化 Markdown:\n" + limit(markdown, 9000);
    }

    private boolean isValidSynthesizedBrief(String markdown) {
        if (markdown == null || markdown.trim().length() < 80) {
            return false;
        }
        return markdown.contains("# " + GENERATED_BRIEF_TITLE_PREFIX)
                && markdown.contains("## 今日新变量")
                && markdown.contains("## 今日证据来源")
                && markdown.contains("不构成投资建议");
    }

    private String extractJson(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
