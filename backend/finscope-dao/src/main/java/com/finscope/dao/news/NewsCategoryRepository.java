package com.finscope.dao.news;

import com.finscope.domain.news.NewsCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class NewsCategoryRepository {
    private final JdbcTemplate jdbc;

    public NewsCategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<NewsCategory> findEnabled() {
        return jdbc.query("SELECT code,name,classification_guidance,enabled,display_order "
                        + "FROM news_category WHERE enabled=1 ORDER BY display_order,code",
                (rs, rowNum) -> new NewsCategory(rs.getString("code"), rs.getString("name"),
                        rs.getString("classification_guidance"), rs.getInt("enabled") == 1,
                        rs.getInt("display_order")));
    }

    public Optional<NewsCategory> findEnabledByCode(String code) {
        List<NewsCategory> values = jdbc.query("SELECT code,name,classification_guidance,enabled,display_order "
                        + "FROM news_category WHERE enabled=1 AND code=?",
                (rs, rowNum) -> new NewsCategory(rs.getString("code"), rs.getString("name"),
                        rs.getString("classification_guidance"), true, rs.getInt("display_order")), code);
        return values.stream().findFirst();
    }
}
