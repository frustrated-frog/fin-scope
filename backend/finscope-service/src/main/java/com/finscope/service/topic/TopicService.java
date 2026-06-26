package com.finscope.service.topic;

import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.brief.BriefRepository;
import com.finscope.dao.insight.InsightCardRepository;
import com.finscope.dao.topic.TopicRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.brief.Brief;
import com.finscope.domain.insight.InsightCard;
import com.finscope.domain.topic.TopicDetail;
import com.finscope.domain.topic.Topic;
import com.finscope.service.agent.ArticleInterpretation;
import com.finscope.service.agent.ArticleInterpretationAgent;
import com.finscope.service.vault.VaultWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TopicService {
    private final TopicRepository topicRepository;
    private final ArticleRepository articleRepository;
    private final BriefRepository briefRepository;
    private final InsightCardRepository insightCardRepository;
    private final TopicExtractor topicExtractor;
    private final ArticleInterpretationAgent articleInterpretationAgent;
    private final VaultWriter vaultWriter;

    public TopicService(TopicRepository topicRepository,
                        ArticleRepository articleRepository,
                        BriefRepository briefRepository,
                        InsightCardRepository insightCardRepository,
                        TopicExtractor topicExtractor,
                        ArticleInterpretationAgent articleInterpretationAgent,
                        VaultWriter vaultWriter) {
        this.topicRepository = topicRepository;
        this.articleRepository = articleRepository;
        this.briefRepository = briefRepository;
        this.insightCardRepository = insightCardRepository;
        this.topicExtractor = topicExtractor;
        this.articleInterpretationAgent = articleInterpretationAgent;
        this.vaultWriter = vaultWriter;
    }

    public List<Topic> list() {
        return topicRepository.findAll();
    }

    public Topic create(Topic topic) {
        long start = System.currentTimeMillis();
        log.info("topic create start name={} slug={}", topic.getName(), topic.getSlug());
        if (topic.getSlug() == null || topic.getSlug().isEmpty()) {
            topic.setSlug(slug(topic.getName()));
        }
        if (topic.getTerms() == null || topic.getTerms().isEmpty()) {
            TopicExtraction extraction = topicExtractor.extract(topic.getName() + " " + topic.getDescription());
            topic.setTerms(joinComma(extraction.getTerms()));
            topic.setLearningQuestions(joinLines(extraction.getLearningQuestions()));
            if (topic.getDescription() == null || topic.getDescription().isEmpty()) {
                topic.setDescription(extraction.getDescription());
            }
        }
        try {
            Path markdown = vaultWriter.writeTopic(topic.getSlug(),
                    renderTopic(topic, new ArrayList<Article>(), new ArrayList<Brief>(), Collections.<Long, InsightCard>emptyMap()));
            topic.setMarkdownPath(markdown.toString());
            Topic saved = topicRepository.upsertBySlug(topic);
            log.info("topic create success topicId={} slug={} durationMs={}", saved.getId(), saved.getSlug(), System.currentTimeMillis() - start);
            return saved;
        } catch (Exception ex) {
            log.error("topic create failed name={} durationMs={}", topic.getName(), System.currentTimeMillis() - start, ex);
            throw new IllegalStateException("Failed to create topic", ex);
        }
    }

    public Topic createFromArticle(Long articleId) {
        log.info("topic create from article start articleId={}", articleId);
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + articleId));
        TopicExtraction extraction = extractFromArticle(article);
        Topic topic = createTopicFromExtraction(extraction);
        topicRepository.linkArticle(topic.getId(), article.getId());
        Topic refreshed = refreshMarkdown(topic.getId());
        log.info("topic create from article success articleId={} topicId={}", articleId, refreshed.getId());
        return refreshed;
    }

    public List<Topic> createFromBrief(LocalDate date) {
        log.info("topic create from brief start date={}", date);
        Brief brief = briefRepository.findByDate(date)
                .orElseThrow(() -> new IllegalArgumentException("Brief not found: " + date));
        TopicExtraction extraction = topicExtractor.extract(brief.getContent());
        Topic topic = createTopicFromExtraction(extraction);
        topicRepository.linkBrief(topic.getId(), brief.getId());
        List<Topic> topics = new ArrayList<Topic>();
        topics.add(refreshMarkdown(topic.getId()));
        log.info("topic create from brief success date={} topicId={}", date, topic.getId());
        return topics;
    }

    public TopicDetail detail(Long id) {
        log.info("topic detail start topicId={}", id);
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + id));
        try {
            return new TopicDetail(topic, topicRepository.findLinkedArticles(id), topicRepository.findLinkedBriefs(id),
                    vaultWriter.readTopic(topic.getSlug()));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read topic markdown", ex);
        }
    }

    public Topic appendNote(Long id, String status, String note) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + id));
        try {
            Path path = vaultWriter.appendTopicNote(topic.getSlug(), note);
            if (status != null && !status.isEmpty()) {
                topic.setStatus(status);
            }
            topic.setMarkdownPath(path.toString());
            return topicRepository.update(topic);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to append topic note", ex);
        }
    }

    private Topic createTopicFromExtraction(TopicExtraction extraction) {
        Topic topic = new Topic();
        topic.setName(extraction.getPrimaryTopicName());
        topic.setSlug(slug(extraction.getPrimaryTopicName()));
        topic.setDescription(extraction.getDescription());
        topic.setStatus("LEARNING");
        topic.setTerms(joinComma(extraction.getTerms()));
        topic.setLearningQuestions(joinLines(extraction.getLearningQuestions()));
        try {
            Path markdown = vaultWriter.writeTopic(topic.getSlug(),
                    renderTopic(topic, new ArrayList<Article>(), new ArrayList<Brief>(), Collections.<Long, InsightCard>emptyMap()));
            topic.setMarkdownPath(markdown.toString());
            return topicRepository.upsertBySlug(topic);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write topic markdown", ex);
        }
    }

    private TopicExtraction extractFromArticle(Article article) {
        ArticleInterpretation interpretation = articleInterpretationAgent.interpret(article);
        return new TopicExtraction(
                interpretation.getTopicName(),
                interpretation.getTopicDescription(),
                interpretation.getKeyTerms(),
                interpretation.getLearningQuestions());
    }

    private Topic refreshMarkdown(Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));
        List<Article> articles = topicRepository.findLinkedArticles(topicId);
        List<Brief> briefs = topicRepository.findLinkedBriefs(topicId);
        Map<Long, InsightCard> insightCards = insightCardRepository.findByArticleIds(articleIds(articles));
        try {
            Path markdown = vaultWriter.writeTopic(topic.getSlug(), renderTopic(topic, articles, briefs, insightCards));
            topic.setMarkdownPath(markdown.toString());
            return topicRepository.update(topic);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to refresh topic markdown", ex);
        }
    }

    private String renderTopic(Topic topic, List<Article> articles, List<Brief> briefs, Map<Long, InsightCard> insightCards) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(topic.getName()).append("\n\n");
        markdown.append("- 状态：").append(topic.getStatus()).append("\n");
        markdown.append("- 描述：").append(empty(topic.getDescription())).append("\n\n");
        markdown.append("## 关键术语\n\n");
        for (String term : splitValues(topic.getTerms(), ",")) {
            markdown.append("- ").append(term).append("\n");
        }
        markdown.append("\n## 学习问题\n\n");
        for (String question : splitValues(topic.getLearningQuestions(), "\n")) {
            markdown.append("- ").append(question).append("\n");
        }
        markdown.append("\n## 关联文章\n\n");
        if (articles.isEmpty()) {
            markdown.append("- 暂无关联文章。\n");
        } else {
            for (Article article : articles) {
                markdown.append("- [").append(article.getTitle()).append("](").append(article.getUrl()).append(")\n");
            }
        }
        markdown.append("\n## 文章解读\n\n");
        if (articles.isEmpty()) {
            markdown.append("- 暂无文章解读。\n");
        } else {
            for (Article article : articles) {
                InsightCard card = insightCards.get(article.getId());
                if (card != null && !empty(card.getCardMarkdown()).isEmpty()) {
                    markdown.append(card.getCardMarkdown().trim()).append("\n\n");
                } else {
                    markdown.append("### ").append(article.getTitle()).append("\n\n");
                    markdown.append(empty(article.getSummary())).append("\n\n");
                }
            }
        }
        markdown.append("\n## 关联简报\n\n");
        if (briefs.isEmpty()) {
            markdown.append("- 暂无关联简报。\n");
        } else {
            for (Brief brief : briefs) {
                markdown.append("- ").append(brief.getTitle()).append(" ").append(brief.getMarkdownPath()).append("\n");
            }
        }
        return markdown.toString();
    }

    private List<Long> articleIds(List<Article> articles) {
        List<Long> ids = new ArrayList<Long>();
        for (Article article : articles) {
            if (article.getId() != null) {
                ids.add(article.getId());
            }
        }
        return ids;
    }

    private String slug(String name) {
        String value = name == null ? "untitled" : name.trim().toLowerCase().replaceAll("\\s+", "-");
        return value.isEmpty() ? "untitled" : value;
    }

    private String joinComma(List<String> values) {
        return values.stream().collect(Collectors.joining(","));
    }

    private String joinLines(List<String> values) {
        return values.stream().collect(Collectors.joining("\n"));
    }

    private List<String> splitValues(String value, String separator) {
        List<String> values = new ArrayList<String>();
        if (value == null || value.trim().isEmpty()) {
            return values;
        }
        for (String item : value.split(separator)) {
            if (!item.trim().isEmpty()) {
                values.add(item.trim());
            }
        }
        return values;
    }

    private String empty(String value) {
        return value == null ? "" : value;
    }
}
