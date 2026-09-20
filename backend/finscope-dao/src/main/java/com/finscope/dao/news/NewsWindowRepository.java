package com.finscope.dao.news;

import com.finscope.domain.research.material.ResearchMaterial;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 有界资讯库；正文按页读取，不把窗口内容装入一个缓存对象。 */
@Repository
public class NewsWindowRepository {
    @Resource
    private JdbcTemplate jdbc;

    @Transactional
    public void merge(List<ResearchMaterial> materials, LocalDateTime now, LocalDateTime cutoff) {
        List<Object[]> rows = new ArrayList<>();
        for (ResearchMaterial material : materials) {
            if (material.getProviderCode() == null || material.getExternalId() == null
                    || material.getPublishedAt() == null || material.getPublishedAt().isBefore(cutoff)
                    || material.getPublishedAt().isAfter(now)) {
                continue;
            }
            rows.add(new Object[]{material.getProviderCode() + ":" + material.getExternalId(),
                    material.getExternalId(), material.getProviderCode(), material.getProviderFamily(),
                    material.getTitle(), material.getContent(), material.getUrl(), material.getSourceTier(),
                    timestamp(material.getPublishedAt()), timestamp(now)});
        }
        jdbc.batchUpdate("INSERT INTO news_window_item "
                + "(item_id,external_id,provider_code,provider_family,title,content,url,source_tier,published_at,first_seen_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(item_id) DO UPDATE SET "
                + "title=excluded.title,content=excluded.content,url=excluded.url,source_tier=excluded.source_tier,"
                + "provider_family=excluded.provider_family,published_at=excluded.published_at", rows);
        jdbc.update("DELETE FROM news_window_item WHERE published_at < ?", timestamp(cutoff));
    }

    public List<String> findIds(LocalDateTime from, LocalDateTime until, String provider, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT item_id FROM news_window_item "
                + "WHERE published_at >= ? AND published_at <= ? AND first_seen_at <= ?");
        List<Object> args = new ArrayList<>();
        args.add(timestamp(from));
        args.add(timestamp(until));
        args.add(timestamp(until));
        if (provider != null && !provider.isBlank() && !"ALL".equals(provider)) {
            sql.append(" AND provider_code = ?");
            args.add(provider);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (instr(lower(COALESCE(title,'')),lower(?)) > 0 "
                    + "OR instr(lower(COALESCE(content,'')),lower(?)) > 0)");
            args.add(keyword.trim());
            args.add(keyword.trim());
        }
        sql.append(" ORDER BY published_at DESC,item_id DESC");
        return jdbc.queryForList(sql.toString(), String.class, args.toArray());
    }

    public List<ResearchMaterial> findByIds(List<String> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbc.query("SELECT * FROM news_window_item WHERE item_id IN (" + placeholders
                + ") ORDER BY published_at DESC,item_id DESC", (rs, row) -> {
            ResearchMaterial material = new ResearchMaterial();
            material.setExternalId(rs.getString("external_id"));
            material.setProviderCode(rs.getString("provider_code"));
            material.setProviderFamily(rs.getString("provider_family"));
            material.setTitle(rs.getString("title"));
            material.setContent(rs.getString("content"));
            material.setUrl(rs.getString("url"));
            material.setSourceTier(rs.getString("source_tier"));
            material.setPublishedAt(LocalDateTime.parse(rs.getString("published_at")));
            return material;
        }, ids.toArray());
    }

    public List<String> providers(LocalDateTime from, LocalDateTime until) {
        return jdbc.queryForList("SELECT DISTINCT provider_code FROM news_window_item "
                + "WHERE published_at >= ? AND published_at <= ? AND first_seen_at <= ? ORDER BY provider_code",
                String.class, timestamp(from), timestamp(until), timestamp(until));
    }

    private String timestamp(LocalDateTime value) {
        return value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
