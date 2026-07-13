package com.finscope.dao.knowledge;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.knowledge.TopicReviewState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TopicReviewStateRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<TopicReviewState> mapper = (resultSet, rowNumber) -> {
        TopicReviewState state = new TopicReviewState();
        state.setTopicId(resultSet.getLong("topic_id"));
        state.setLastReviewedAt(TimeUtil.localDateTime(resultSet, "last_reviewed_at"));
        state.setNextReviewAt(TimeUtil.localDateTime(resultSet, "next_review_at"));
        state.setIntervalDays(resultSet.getInt("interval_days"));
        state.setReviewCount(resultSet.getInt("review_count"));
        state.setRevision(resultSet.getLong("revision"));
        return state;
    };

    public TopicReviewStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TopicReviewState createIfAbsent(Long topicId) {
        jdbcTemplate.update(
                "INSERT OR IGNORE INTO topic_review_state(" +
                        "topic_id,interval_days,review_count,revision) VALUES(?,7,0,0)",
                topicId
        );
        return findByTopicId(topicId)
                .orElseThrow(() -> new IllegalStateException("Unable to create topic review state: " + topicId));
    }

    public Optional<TopicReviewState> findByTopicId(Long topicId) {
        List<TopicReviewState> states = jdbcTemplate.query(
                "SELECT * FROM topic_review_state WHERE topic_id=?", mapper, topicId);
        return states.isEmpty() ? Optional.empty() : Optional.of(states.get(0));
    }

    public boolean recordReview(Long topicId, LocalDateTime reviewedAt, LocalDateTime nextReviewAt,
                                int intervalDays, long expectedRevision) {
        return jdbcTemplate.update(
                "UPDATE topic_review_state SET last_reviewed_at=?,next_review_at=?,interval_days=?," +
                        "review_count=review_count+1,revision=revision+1 WHERE topic_id=? AND revision=?",
                TimeUtil.text(reviewedAt), TimeUtil.text(nextReviewAt), intervalDays,
                topicId, expectedRevision
        ) == 1;
    }
}
