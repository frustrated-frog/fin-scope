package com.finscope.service.brief;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.brief.BriefRepository;
import com.finscope.dao.insight.InsightCardRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.brief.Brief;
import com.finscope.domain.insight.InsightCard;
import com.finscope.domain.research.BriefResearchContext;
import com.finscope.service.research.BriefResearchContextService;
import com.finscope.service.vault.VaultWriter;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BriefService {
    private static final String GENERATED_BRIEF_TITLE_PREFIX = "每日金融、投资、创业学习简报 - ";

    private final ArticleRepository articleRepository;
    private final BriefRepository briefRepository;
    private final InsightCardRepository insightCardRepository;
    private final BriefGenerator briefGenerator;
    private final VaultWriter vaultWriter;
    private final AgentRunRepository agentRunRepository;
    private final BriefResearchContextService briefResearchContextService;

    public BriefService(ArticleRepository articleRepository,
                        BriefRepository briefRepository,
                        InsightCardRepository insightCardRepository,
                        BriefGenerator briefGenerator,
                        VaultWriter vaultWriter,
                        AgentRunRepository agentRunRepository,
                        BriefResearchContextService briefResearchContextService) {
        this.articleRepository = articleRepository;
        this.briefRepository = briefRepository;
        this.insightCardRepository = insightCardRepository;
        this.briefGenerator = briefGenerator;
        this.vaultWriter = vaultWriter;
        this.agentRunRepository = agentRunRepository;
        this.briefResearchContextService = briefResearchContextService;
    }

    public Brief generateToday() {
        return generate(LocalDate.now());
    }

    public List<Brief> list() {
        syncVaultBriefs();
        return briefRepository.findAll();
    }

    public Brief detail(LocalDate date) {
        syncVaultBrief(date);
        return briefRepository.findByDate(date)
                .orElseThrow(() -> new IllegalArgumentException("Brief not found: " + date));
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
            String markdown = context.isEmpty()
                    ? briefGenerator.generate(date, articles, cardsByArticleId)
                    : briefGenerator.generate(date, articles, context);
            Path markdownPath = vaultWriter.writeDailyBrief(date, markdown);
            Brief brief = new Brief();
            brief.setBriefDate(date);
            brief.setTitle(GENERATED_BRIEF_TITLE_PREFIX + date);
            brief.setContent(markdown);
            brief.setMarkdownPath(markdownPath.toString());
            Brief saved = briefRepository.upsert(brief);
            agentRunRepository.record("brief-generate", "SUCCESS", "date=" + date,
                    "articles=" + articles.size(), null, System.currentTimeMillis() - start);
            return saved;
        } catch (Exception ex) {
            agentRunRepository.record("brief-generate", "FAILED", "date=" + date,
                    null, ex.getMessage(), System.currentTimeMillis() - start);
            throw new IllegalStateException("Failed to generate brief", ex);
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
}
