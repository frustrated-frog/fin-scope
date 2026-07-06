package com.finscope.dao.topic;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.article.Article;
import com.finscope.domain.brief.Brief;
import com.finscope.domain.topic.Topic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TopicRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    private final RowMapper<Topic> mapper = (rs, rowNum) -> {
        Topic topic = new Topic();
        topic.setId(rs.getLong("id"));
        topic.setName(rs.getString("name"));
        topic.setSlug(rs.getString("slug"));
        topic.setDescription(rs.getString("description"));
        topic.setStatus(rs.getString("status"));
        topic.setMarkdownPath(rs.getString("markdown_path"));
        topic.setTerms(rs.getString("terms"));
        topic.setLearningQuestions(rs.getString("learning_questions"));
        topic.setArticleCount(readInt(rs, "article_count"));
        topic.setBriefCount(readInt(rs, "brief_count"));
        topic.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        topic.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return topic;
    };
    private final RowMapper<Article> articleMapper = (rs, rowNum) -> {
        Article article = new Article();
        article.setId(rs.getLong("id"));
        article.setSourceId(rs.getLong("source_id"));
        article.setSourceName(rs.getString("source_name"));
        article.setTitle(rs.getString("title"));
        article.setUrl(rs.getString("url"));
        article.setPublishedAt(TimeUtil.localDateTime(rs, "published_at"));
        article.setSummary(rs.getString("summary"));
        article.setBody(rs.getString("body"));
        article.setCategory(rs.getString("category"));
        article.setNoveltyType(rs.getString("novelty_type"));
        article.setNoveltyReason(rs.getString("novelty_reason"));
        article.setFetchedAt(TimeUtil.localDateTime(rs, "fetched_at"));
        return article;
    };
    private final RowMapper<Brief> briefMapper = (rs, rowNum) -> {
        Brief brief = new Brief();
        brief.setId(rs.getLong("id"));
        brief.setBriefDate(LocalDate.parse(rs.getString("brief_date")));
        brief.setTitle(rs.getString("title"));
        brief.setContent(rs.getString("content"));
        brief.setMarkdownPath(rs.getString("markdown_path"));
        brief.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return brief;
    };

    public Topic save(Topic topic) {
        LocalDateTime now = LocalDateTime.now();
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);
        if (topic.getStatus() == null || topic.getStatus().isEmpty()) {
            topic.setStatus("LEARNING");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO topic(name,slug,description,status,markdown_path,terms,learning_questions,created_at,updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, topic.getName());
            ps.setString(2, topic.getSlug());
            ps.setString(3, topic.getDescription());
            ps.setString(4, topic.getStatus());
            ps.setString(5, topic.getMarkdownPath());
            ps.setString(6, topic.getTerms());
            ps.setString(7, topic.getLearningQuestions());
            ps.setString(8, TimeUtil.text(topic.getCreatedAt()));
            ps.setString(9, TimeUtil.text(topic.getUpdatedAt()));
            return ps;
        }, keyHolder);
        topic.setId(keyHolder.getKey().longValue());
        return topic;
    }

    public Topic upsertBySlug(Topic topic) {
        Optional<Topic> existing = findBySlug(topic.getSlug());
        if (existing.isPresent()) {
            Topic current = existing.get();
            current.setDescription(nonBlank(topic.getDescription(), current.getDescription()));
            current.setMarkdownPath(nonBlank(topic.getMarkdownPath(), current.getMarkdownPath()));
            current.setTerms(mergeCsv(current.getTerms(), topic.getTerms()));
            current.setLearningQuestions(mergeLines(current.getLearningQuestions(), topic.getLearningQuestions()));
            update(current);
            return findById(current.getId()).orElse(current);
        }
        return save(topic);
    }

    public Topic update(Topic topic) {
        topic.setUpdatedAt(LocalDateTime.now());
        jdbcTemplate.update("UPDATE topic SET name=?, slug=?, description=?, status=?, markdown_path=?, terms=?, "
                        + "learning_questions=?, updated_at=? WHERE id=?",
                topic.getName(), topic.getSlug(), topic.getDescription(), topic.getStatus(), topic.getMarkdownPath(),
                topic.getTerms(), topic.getLearningQuestions(), TimeUtil.text(topic.getUpdatedAt()), topic.getId());
        return findById(topic.getId()).orElse(topic);
    }

    public List<Topic> findAll() {
        return jdbcTemplate.query(selectTopicSql() + " ORDER BY t.updated_at DESC", mapper);
    }

    public Optional<Topic> findById(Long id) {
        List<Topic> topics = jdbcTemplate.query(selectTopicSql() + " WHERE t.id = ?", mapper, id);
        return topics.isEmpty() ? Optional.empty() : Optional.of(topics.get(0));
    }

    public Optional<Topic> findBySlug(String slug) {
        List<Topic> topics = jdbcTemplate.query(selectTopicSql() + " WHERE t.slug = ?", mapper, slug);
        return topics.isEmpty() ? Optional.empty() : Optional.of(topics.get(0));
    }

    public void linkArticle(Long topicId, Long articleId) {
        jdbcTemplate.update("INSERT OR IGNORE INTO topic_article(topic_id,article_id,created_at) VALUES(?,?,?)",
                topicId, articleId, TimeUtil.text(LocalDateTime.now()));
    }

    public void linkBrief(Long topicId, Long briefId) {
        jdbcTemplate.update("INSERT OR IGNORE INTO topic_brief(topic_id,brief_id,created_at) VALUES(?,?,?)",
                topicId, briefId, TimeUtil.text(LocalDateTime.now()));
    }

    public List<Article> findLinkedArticles(Long topicId) {
        return jdbcTemplate.query("SELECT a.* FROM article a "
                        + "JOIN topic_article ta ON ta.article_id = a.id "
                        + "WHERE ta.topic_id = ? ORDER BY a.fetched_at DESC, a.id DESC",
                articleMapper, topicId);
    }

    public List<Brief> findLinkedBriefs(Long topicId) {
        return jdbcTemplate.query("SELECT b.* FROM brief b "
                        + "JOIN topic_brief tb ON tb.brief_id = b.id "
                        + "WHERE tb.topic_id = ? ORDER BY b.brief_date DESC",
                briefMapper, topicId);
    }

    public int deleteById(Long topicId) {
        jdbcTemplate.update("DELETE FROM topic_article WHERE topic_id = ?", topicId);
        jdbcTemplate.update("DELETE FROM topic_brief WHERE topic_id = ?", topicId);
        return jdbcTemplate.update("DELETE FROM topic WHERE id = ?", topicId);
    }

    private String selectTopicSql() {
        return "SELECT t.*, "
                + "(SELECT COUNT(*) FROM topic_article ta WHERE ta.topic_id = t.id) AS article_count, "
                + "(SELECT COUNT(*) FROM topic_brief tb WHERE tb.topic_id = t.id) AS brief_count "
                + "FROM topic t";
    }

    private int readInt(java.sql.ResultSet rs, String column) {
        try {
            return rs.getInt(column);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String nonBlank(String candidate, String fallback) {
        return candidate == null || candidate.trim().isEmpty() ? fallback : candidate;
    }

    private String mergeCsv(String first, String second) {
        if (first == null || first.trim().isEmpty()) {
            return second;
        }
        if (second == null || second.trim().isEmpty()) {
            return first;
        }
        String result = first;
        for (String value : second.split(",")) {
            if (!result.contains(value)) {
                result += "," + value;
            }
        }
        return result;
    }

    private String mergeLines(String first, String second) {
        if (first == null || first.trim().isEmpty()) {
            return second;
        }
        if (second == null || second.trim().isEmpty() || first.contains(second)) {
            return first;
        }
        return first + "\n" + second;
    }
}
