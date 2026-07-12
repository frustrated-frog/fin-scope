package com.finscope.dao.strategy;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.strategy.StrategyReview;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class StrategyReviewRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<StrategyReview> mapper = (rs, rowNum) -> {
        StrategyReview value = new StrategyReview();
        value.setId(rs.getLong("id"));
        value.setReviewDate(TimeUtil.localDate(rs, "review_date"));
        value.setFacts(rs.getString("facts"));
        value.setReasoning(rs.getString("reasoning"));
        value.setNextAction(rs.getString("next_action"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return value;
    };

    public StrategyReview save(StrategyReview value) {
        LocalDateTime now = LocalDateTime.now();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO strategy_review(review_date,facts,reasoning,next_action,created_at) "
                            + "VALUES(?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, TimeUtil.text(value.getReviewDate()));
            statement.setString(2, value.getFacts());
            statement.setString(3, value.getReasoning());
            statement.setString(4, value.getNextAction());
            statement.setString(5, TimeUtil.text(now));
            return statement;
        }, keys);
        if (keys.getKey() != null) {
            value.setId(keys.getKey().longValue());
        }
        value.setCreatedAt(now);
        return value;
    }

    public List<StrategyReview> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM strategy_review ORDER BY review_date DESC,id DESC", mapper);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM strategy_review WHERE id=?", id);
    }
}
