package com.finscope.dao.insight;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.insight.InsightCard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class InsightCardRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    private final RowMapper<InsightCard> mapper = (rs, rowNum) -> {
        InsightCard card = new InsightCard();
        card.setId(rs.getLong("id"));
        card.setArticleId(rs.getLong("article_id"));
        card.setTitle(rs.getString("title"));
        card.setSourceName(rs.getString("source_name"));
        card.setSourceUrl(rs.getString("source_url"));
        card.setPublishedAt(TimeUtil.localDateTime(rs, "published_at"));
        card.setOneSentenceSummary(rs.getString("one_sentence_summary"));
        card.setCoreEvent(rs.getString("core_event"));
        card.setImportance(rs.getString("importance"));
        card.setImpactTargets(rs.getString("impact_targets"));
        card.setNoveltyType(rs.getString("novelty_type"));
        card.setNoveltyReason(rs.getString("novelty_reason"));
        card.setFollowUpQuestions(rs.getString("follow_up_questions"));
        card.setCardMarkdown(rs.getString("card_markdown"));

        // 深度解读字段
        card.setBackground(rs.getString("background"));
        card.setKeyData(rs.getString("key_data"));
        card.setTimeline(rs.getString("timeline"));
        card.setRelatedParties(rs.getString("related_parties"));
        card.setRiskFactors(rs.getString("risk_factors"));
        card.setFutureOutlook(rs.getString("future_outlook"));
        card.setImpactOnInvestment(rs.getString("impact_on_investment"));
        card.setImpactOnStartup(rs.getString("impact_on_startup"));
        card.setProfessionalInsight(rs.getString("professional_insight"));
        card.setFacts(rs.getString("facts"));
        card.setReasoning(rs.getString("reasoning"));
        card.setOpinions(rs.getString("opinions"));

        card.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        card.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return card;
    };

    public InsightCard save(InsightCard card) {
        LocalDateTime now = LocalDateTime.now();
        if (card.getCreatedAt() == null) {
            card.setCreatedAt(now);
        }
        card.setUpdatedAt(now);
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO insight_card("
                    + "article_id,title,source_name,source_url,published_at,one_sentence_summary,core_event,"
                    + "importance,impact_targets,novelty_type,novelty_reason,follow_up_questions,card_markdown,"
                    + "background,key_data,timeline,related_parties,risk_factors,future_outlook,"
                    + "impact_on_investment,impact_on_startup,professional_insight,facts,reasoning,opinions,"
                    + "created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            int i = 1;
            ps.setLong(i++, card.getArticleId());
            ps.setString(i++, card.getTitle());
            ps.setString(i++, card.getSourceName());
            ps.setString(i++, card.getSourceUrl());
            ps.setString(i++, TimeUtil.text(card.getPublishedAt()));
            ps.setString(i++, card.getOneSentenceSummary());
            ps.setString(i++, card.getCoreEvent());
            ps.setString(i++, card.getImportance());
            ps.setString(i++, card.getImpactTargets());
            ps.setString(i++, card.getNoveltyType());
            ps.setString(i++, card.getNoveltyReason());
            ps.setString(i++, card.getFollowUpQuestions());
            ps.setString(i++, card.getCardMarkdown());

            // 深度解读字段
            ps.setString(i++, card.getBackground());
            ps.setString(i++, card.getKeyData());
            ps.setString(i++, card.getTimeline());
            ps.setString(i++, card.getRelatedParties());
            ps.setString(i++, card.getRiskFactors());
            ps.setString(i++, card.getFutureOutlook());
            ps.setString(i++, card.getImpactOnInvestment());
            ps.setString(i++, card.getImpactOnStartup());
            ps.setString(i++, card.getProfessionalInsight());
            ps.setString(i++, card.getFacts());
            ps.setString(i++, card.getReasoning());
            ps.setString(i++, card.getOpinions());

            ps.setString(i++, TimeUtil.text(card.getCreatedAt()));
            ps.setString(i++, TimeUtil.text(card.getUpdatedAt()));
            return ps;
        });
        return findByArticleId(card.getArticleId()).orElse(card);
    }

    public List<InsightCard> findAll() {
        return jdbcTemplate.query("SELECT * FROM insight_card ORDER BY created_at DESC, id DESC", mapper);
    }

    public Optional<InsightCard> findById(Long id) {
        List<InsightCard> cards = jdbcTemplate.query("SELECT * FROM insight_card WHERE id = ?", mapper, id);
        return cards.isEmpty() ? Optional.empty() : Optional.of(cards.get(0));
    }

    public Optional<InsightCard> findByArticleId(Long articleId) {
        List<InsightCard> cards = jdbcTemplate.query("SELECT * FROM insight_card WHERE article_id = ?", mapper, articleId);
        return cards.isEmpty() ? Optional.empty() : Optional.of(cards.get(0));
    }

    public Map<Long, InsightCard> findByArticleIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(articleIds.size(), "?"));
        List<InsightCard> cards = jdbcTemplate.query("SELECT * FROM insight_card WHERE article_id IN (" + placeholders + ")",
                mapper, articleIds.toArray());
        Map<Long, InsightCard> byArticleId = new HashMap<Long, InsightCard>();
        for (InsightCard card : cards) {
            byArticleId.put(card.getArticleId(), card);
        }
        return byArticleId;
    }

    public int deleteByArticleId(Long articleId) {
        return jdbcTemplate.update("DELETE FROM insight_card WHERE article_id = ?", articleId);
    }

    public int deleteByArticleIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", Collections.nCopies(articleIds.size(), "?"));
        return jdbcTemplate.update(
            "DELETE FROM insight_card WHERE article_id IN (" + placeholders + ")",
            articleIds.toArray()
        );
    }
}
