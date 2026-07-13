package com.finscope.dao.knowledge;

import com.finscope.common.util.TimeUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TopicEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public TopicEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean link(Long topicId, Long eventId, String linkType) {
        return jdbcTemplate.update(
                "INSERT OR IGNORE INTO topic_event(topic_id,event_id,link_type,created_at) VALUES(?,?,?,?)",
                topicId, eventId, linkType, TimeUtil.text(LocalDateTime.now())
        ) == 1;
    }

    public List<Long> findEventIds(Long topicId) {
        return jdbcTemplate.query(
                "SELECT event_id FROM topic_event WHERE topic_id=? ORDER BY created_at DESC,event_id DESC",
                (resultSet, rowNumber) -> resultSet.getLong("event_id"),
                topicId
        );
    }

    public boolean isLinked(Long topicId, Long eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM topic_event WHERE topic_id=? AND event_id=?",
                Integer.class, topicId, eventId);
        return count != null && count > 0;
    }
}
