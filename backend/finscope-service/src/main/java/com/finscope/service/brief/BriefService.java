package com.finscope.service.brief;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.brief.BriefRepository;
import com.finscope.dao.insight.InsightCardRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.brief.Brief;
import com.finscope.domain.insight.InsightCard;
import com.finscope.service.vault.VaultWriter;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BriefService {
    private final ArticleRepository articleRepository;
    private final BriefRepository briefRepository;
    private final InsightCardRepository insightCardRepository;
    private final BriefGenerator briefGenerator;
    private final VaultWriter vaultWriter;
    private final AgentRunRepository agentRunRepository;

    public BriefService(ArticleRepository articleRepository,
                        BriefRepository briefRepository,
                        InsightCardRepository insightCardRepository,
                        BriefGenerator briefGenerator,
                        VaultWriter vaultWriter,
                        AgentRunRepository agentRunRepository) {
        this.articleRepository = articleRepository;
        this.briefRepository = briefRepository;
        this.insightCardRepository = insightCardRepository;
        this.briefGenerator = briefGenerator;
        this.vaultWriter = vaultWriter;
        this.agentRunRepository = agentRunRepository;
    }

    public Brief generateToday() {
        return generate(LocalDate.now());
    }

    public List<Brief> list() {
        return briefRepository.findAll();
    }

    public Brief detail(LocalDate date) {
        return briefRepository.findByDate(date)
                .orElseThrow(() -> new IllegalArgumentException("Brief not found: " + date));
    }

    public Brief generate(LocalDate date) {
        long start = System.currentTimeMillis();
        try {
            List<Article> articles = articleRepository.findByDate(date);
            List<Long> articleIds = articles.stream().map(Article::getId).collect(Collectors.toList());
            Map<Long, InsightCard> cardsByArticleId = insightCardRepository.findByArticleIds(articleIds);
            String markdown = briefGenerator.generate(date, articles, cardsByArticleId);
            Path markdownPath = vaultWriter.writeDailyBrief(date, markdown);
            Brief brief = new Brief();
            brief.setBriefDate(date);
            brief.setTitle("FinScope Daily Brief - " + date);
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
}
