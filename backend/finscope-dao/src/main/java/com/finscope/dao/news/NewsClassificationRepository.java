package com.finscope.dao.news;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.news.NewsItemClassification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class NewsClassificationRepository {
    private final JdbcTemplate jdbc;

    public NewsClassificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(String itemId, LocalDateTime now, LocalDateTime retryBefore) {
        String timestamp = TimeUtil.text(now);
        int inserted = jdbc.update("INSERT OR IGNORE INTO news_item_classification(item_id,status,created_at,updated_at) "
                + "VALUES(?,'PENDING',?,?)", itemId, timestamp, timestamp);
        if (inserted == 1) return true;
        return jdbc.update("UPDATE news_item_classification SET status='PENDING',category_code=NULL,confidence=NULL,"
                        + "reason=NULL,error_message=NULL,updated_at=? WHERE item_id=? AND status='FAILED' AND updated_at<=?",
                timestamp, itemId, TimeUtil.text(retryBefore)) == 1;
    }

    public void markClassified(String itemId, String categoryCode, double confidence, String reason,
                               String modelName, LocalDateTime now) {
        jdbc.update("UPDATE news_item_classification SET status='CLASSIFIED',category_code=?,confidence=?,reason=?,"
                        + "model_name=?,error_message=NULL,updated_at=? WHERE item_id=?",
                categoryCode, confidence, reason, modelName, TimeUtil.text(now), itemId);
    }

    public void markFailed(String itemId, String errorMessage, String modelName, LocalDateTime now) {
        jdbc.update("UPDATE news_item_classification SET status='FAILED',category_code=NULL,confidence=NULL,reason=NULL,"
                        + "model_name=?,error_message=?,updated_at=? WHERE item_id=?",
                modelName, errorMessage, TimeUtil.text(now), itemId);
    }

    public Map<String, NewsItemClassification> findByItemIds(Collection<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return Collections.emptyMap();
        String placeholders = String.join(",", Collections.nCopies(itemIds.size(), "?"));
        List<NewsItemClassification> values = jdbc.query("SELECT item_id,status,category_code,confidence,reason,"
                        + "model_name,error_message,updated_at FROM news_item_classification WHERE item_id IN ("
                        + placeholders + ")",
                itemIds.toArray(), (rs, rowNum) -> new NewsItemClassification(rs.getString("item_id"),
                        rs.getString("status"), rs.getString("category_code"), rs.getDouble("confidence"),
                        rs.getString("reason"), rs.getString("model_name"), rs.getString("error_message"),
                        TimeUtil.localDateTime(rs, "updated_at")));
        Map<String, NewsItemClassification> result = new LinkedHashMap<String, NewsItemClassification>();
        for (NewsItemClassification value : values) result.put(value.getItemId(), value);
        return result;
    }
}
