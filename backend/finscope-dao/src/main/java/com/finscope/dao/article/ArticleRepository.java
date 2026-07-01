package com.finscope.dao.article;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.article.Article;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class ArticleRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    private final RowMapper<Article> mapper = (rs, rowNum) -> {
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

    public Article save(Article article, String urlFingerprint, String titleFingerprint, long bodySimhash) {
        if (article.getFetchedAt() == null) {
            article.setFetchedAt(LocalDateTime.now());
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO article(source_id,source_name,title,url,published_at,summary,body,category,novelty_type,"
                            + "novelty_reason,url_fingerprint,title_fingerprint,body_simhash,fetched_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            if (article.getSourceId() == null) {
                ps.setObject(1, null);
            } else {
                ps.setLong(1, article.getSourceId());
            }
            ps.setString(2, article.getSourceName());
            ps.setString(3, article.getTitle());
            ps.setString(4, article.getUrl());
            ps.setString(5, TimeUtil.text(article.getPublishedAt()));
            ps.setString(6, article.getSummary());
            ps.setString(7, article.getBody());
            ps.setString(8, article.getCategory());
            ps.setString(9, article.getNoveltyType());
            ps.setString(10, article.getNoveltyReason());
            ps.setString(11, urlFingerprint);
            ps.setString(12, titleFingerprint);
            ps.setLong(13, bodySimhash);
            ps.setString(14, TimeUtil.text(article.getFetchedAt()));
            return ps;
        }, keyHolder);
        article.setId(keyHolder.getKey().longValue());
        return article;
    }

    public List<Article> findAll() {
        return jdbcTemplate.query("SELECT * FROM article ORDER BY fetched_at DESC, id DESC", mapper);
    }

    public Optional<Article> findById(Long id) {
        List<Article> articles = jdbcTemplate.query("SELECT * FROM article WHERE id = ?", mapper, id);
        return articles.isEmpty() ? Optional.empty() : Optional.of(articles.get(0));
    }

    public List<Article> findByDate(LocalDate date) {
        String start = date.atStartOfDay().toString();
        String end = date.plusDays(1).atStartOfDay().toString();
        return jdbcTemplate.query("SELECT * FROM article WHERE fetched_at >= ? AND fetched_at < ? ORDER BY id DESC",
                mapper, start, end);
    }

    public List<ArticleRecord> findRecentRecords(int limit) {
        return jdbcTemplate.query("SELECT id,title,url_fingerprint,title_fingerprint,body_simhash FROM article "
                        + "ORDER BY id DESC LIMIT ?",
                (rs, rowNum) -> new ArticleRecord(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("url_fingerprint"),
                        rs.getString("title_fingerprint"),
                        rs.getLong("body_simhash")),
                limit);
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM article", Integer.class);
        return count == null ? 0 : count;
    }

    public List<Article> findAllPaged(int page, int pageSize) {
        int offset = page * pageSize;
        return jdbcTemplate.query(
            "SELECT * FROM article ORDER BY fetched_at DESC, id DESC LIMIT ? OFFSET ?",
            mapper, pageSize, offset
        );
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM article WHERE id = ?", id);
    }

    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.update(
            "DELETE FROM article WHERE id IN (" + placeholders + ")",
            ids.toArray()
        );
    }

    public static class ArticleRecord {
        private final long id;
        private final String title;
        private final String urlFingerprint;
        private final String titleFingerprint;
        private final long bodySimhash;

        public ArticleRecord(long id, String title, String urlFingerprint, String titleFingerprint, long bodySimhash) {
            this.id = id;
            this.title = title;
            this.urlFingerprint = urlFingerprint;
            this.titleFingerprint = titleFingerprint;
            this.bodySimhash = bodySimhash;
        }

        public long getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getUrlFingerprint() {
            return urlFingerprint;
        }

        public String getTitleFingerprint() {
            return titleFingerprint;
        }

        public long getBodySimhash() {
            return bodySimhash;
        }
    }
}
