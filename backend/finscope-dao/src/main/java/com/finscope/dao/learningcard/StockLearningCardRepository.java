package com.finscope.dao.learningcard;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.learningcard.StockLearningCard;
import com.finscope.domain.learningcard.StockLearningCardClaim;
import com.finscope.domain.learningcard.StockLearningCardEvidence;
import com.finscope.domain.learningcard.StockLearningCardRun;
import com.finscope.domain.learningcard.StockLearningCardSummary;
import com.finscope.domain.learningcard.StockLearningCardWatchItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class StockLearningCardRepository {
    private static final String CARD_SELECT = "SELECT c.*,i.code,i.name FROM stock_learning_card c JOIN instrument i ON i.id=c.instrument_id ";
    @Resource private JdbcTemplate jdbcTemplate;

    private final RowMapper<StockLearningCard> cardMapper = (rs, row) -> {
        StockLearningCard value = new StockLearningCard();
        value.setId(rs.getLong("id")); value.setInstrumentId(rs.getLong("instrument_id"));
        value.setFrameworkCode(rs.getString("framework_code"));
        long latest = rs.getLong("latest_run_id"); value.setLatestRunId(rs.wasNull() ? null : latest);
        value.setStatus(rs.getString("status")); value.setRevision(rs.getLong("revision"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at")); value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        value.setCode(rs.getString("code")); value.setName(rs.getString("name")); return value;
    };
    private final RowMapper<StockLearningCardRun> runMapper = (rs, row) -> {
        StockLearningCardRun value = new StockLearningCardRun();
        value.setId(rs.getLong("id")); value.setCardId(rs.getLong("card_id"));
        long research = rs.getLong("research_run_id"); value.setResearchRunId(rs.wasNull() ? null : research);
        value.setFrameworkCode(rs.getString("framework_code")); value.setStatus(rs.getString("status"));
        value.setStage(rs.getString("stage")); value.setFailedStage(rs.getString("failed_stage"));
        value.setErrorCode(rs.getString("error_code")); value.setUserMessage(rs.getString("user_message"));
        value.setRetryable(rs.getInt("retryable") == 1);
        value.setConclusionStatus(rs.getString("conclusion_status")); value.setSummary(rs.getString("summary"));
        value.setEvidenceCompleteness(rs.getString("evidence_completeness")); value.setWarningMessage(rs.getString("warning_message"));
        value.setSourceFingerprint(rs.getString("source_fingerprint")); value.setGenerationMode(rs.getString("generation_mode"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at")); value.setCompletedAt(TimeUtil.localDateTime(rs, "completed_at")); return value;
    };

    public StockLearningCard findOrCreate(Long instrumentId, String frameworkCode) {
        Optional<StockLearningCard> existing = findByInstrumentId(instrumentId);
        if (existing.isPresent()) return existing.get();
        LocalDateTime now = LocalDateTime.now(); GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> { PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO stock_learning_card(instrument_id,framework_code,status,revision,created_at,updated_at) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, instrumentId); ps.setString(2, frameworkCode); ps.setString(3, "IDLE"); ps.setLong(4, 0);
            ps.setString(5, TimeUtil.text(now)); ps.setString(6, TimeUtil.text(now)); return ps; }, keys);
        return findByInstrumentId(instrumentId).orElseThrow(() -> new IllegalStateException("学习卡保存失败"));
    }
    public Optional<StockLearningCard> findByInstrumentId(Long instrumentId) {
        List<StockLearningCard> values = jdbcTemplate.query(CARD_SELECT + "WHERE c.instrument_id=?", cardMapper, instrumentId);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }
    public StockLearningCardRun appendRun(StockLearningCardRun run, List<StockLearningCardClaim> claims, List<StockLearningCardWatchItem> watches) {
        LocalDateTime now = LocalDateTime.now(); GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> { PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO stock_learning_card_run(card_id,research_run_id,framework_code,status,stage,failed_stage,error_code,user_message,retryable,conclusion_status,summary,evidence_completeness,warning_message,source_fingerprint,generation_mode,created_at,completed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, run.getCardId()); if (run.getResearchRunId() == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setLong(2, run.getResearchRunId());
            ps.setString(3, run.getFrameworkCode()); ps.setString(4, run.getStatus()); ps.setString(5, run.getStage());
            ps.setString(6, run.getFailedStage()); ps.setString(7, run.getErrorCode()); ps.setString(8, run.getUserMessage()); ps.setInt(9, run.isRetryable() ? 1 : 0);
            ps.setString(10, run.getConclusionStatus()); ps.setString(11, run.getSummary()); ps.setString(12, run.getEvidenceCompleteness());
            ps.setString(13, run.getWarningMessage()); ps.setString(14, run.getSourceFingerprint()); ps.setString(15, run.getGenerationMode());
            ps.setString(16, TimeUtil.text(now));
            if ("RUNNING".equals(run.getStatus())) ps.setNull(17, java.sql.Types.VARCHAR); else ps.setString(17, TimeUtil.text(now));
            return ps; }, keys);
        run.setId(keys.getKey().longValue());
        for (StockLearningCardClaim claim : claims == null ? Collections.<StockLearningCardClaim>emptyList() : claims) saveClaim(run.getId(), claim);
        for (StockLearningCardWatchItem watch : watches == null ? Collections.<StockLearningCardWatchItem>emptyList() : watches) saveWatch(run.getId(), watch);
        jdbcTemplate.update("UPDATE stock_learning_card SET latest_run_id=?,status=?,revision=revision+1,updated_at=? WHERE id=?", run.getId(), run.getStatus(), TimeUtil.text(now), run.getCardId());
        return findRun(run.getId()).orElseThrow(() -> new IllegalStateException("学习卡运行保存失败"));
    }
    @Transactional
    public StockLearningCardRun updateRun(StockLearningCardRun run, List<StockLearningCardClaim> claims,
                                          List<StockLearningCardWatchItem> watches) {
        if (run == null || run.getId() == null) throw new IllegalArgumentException("学习卡运行不能为空");
        LocalDateTime now = LocalDateTime.now();
        int changed = jdbcTemplate.update("UPDATE stock_learning_card_run SET status=?,stage=?,failed_stage=?,error_code=?,user_message=?,retryable=?,"
                        + "conclusion_status=?,summary=?,evidence_completeness=?,warning_message=?,source_fingerprint=?,generation_mode=?,completed_at=? WHERE id=?",
                run.getStatus(), run.getStage(), run.getFailedStage(), run.getErrorCode(), run.getUserMessage(), run.isRetryable() ? 1 : 0,
                run.getConclusionStatus(), run.getSummary(), run.getEvidenceCompleteness(), run.getWarningMessage(), run.getSourceFingerprint(),
                run.getGenerationMode(), "RUNNING".equals(run.getStatus()) ? null : TimeUtil.text(now), run.getId());
        if (changed != 1) throw new IllegalStateException("学习卡运行不存在：" + run.getId());
        jdbcTemplate.update("DELETE FROM stock_learning_card_claim WHERE run_id=?", run.getId());
        jdbcTemplate.update("DELETE FROM stock_learning_card_watch_item WHERE run_id=?", run.getId());
        for (StockLearningCardClaim claim : claims == null ? Collections.<StockLearningCardClaim>emptyList() : claims) saveClaim(run.getId(), claim);
        for (StockLearningCardWatchItem watch : watches == null ? Collections.<StockLearningCardWatchItem>emptyList() : watches) saveWatch(run.getId(), watch);
        jdbcTemplate.update("UPDATE stock_learning_card SET status=?,revision=revision+1,updated_at=? WHERE id=?",
                run.getStatus(), TimeUtil.text(now), run.getCardId());
        return findRun(run.getId()).orElseThrow(() -> new IllegalStateException("学习卡运行更新失败"));
    }
    private void saveClaim(Long runId, StockLearningCardClaim claim) {
        jdbcTemplate.update("INSERT INTO stock_learning_card_claim(run_id,dimension_code,status,failure_message,judgment,rationale,counterargument,unknowns,confidence,sort_order) VALUES(?,?,?,?,?,?,?,?,?,?)",
                runId, claim.getDimensionCode(), claim.getStatus(), claim.getFailureMessage(), claim.getJudgment(), claim.getRationale(), claim.getCounterargument(), claim.getUnknowns(), claim.getConfidence(), claim.getSortOrder());
    }
    private void saveWatch(Long runId, StockLearningCardWatchItem watch) {
        jdbcTemplate.update("INSERT INTO stock_learning_card_watch_item(run_id,metric,baseline,frequency,upgrade_condition,downgrade_condition,next_review_at,sort_order) VALUES(?,?,?,?,?,?,?,?)",
                runId, watch.getMetric(), watch.getBaseline(), watch.getFrequency(), watch.getUpgradeCondition(), watch.getDowngradeCondition(), TimeUtil.text(watch.getNextReviewAt()), watch.getSortOrder());
    }
    @Transactional
    public void replaceEvidence(Long runId, List<StockLearningCardEvidence> evidence) {
        jdbcTemplate.update("DELETE FROM stock_learning_card_evidence WHERE run_id=?", runId);
        for (StockLearningCardEvidence item : evidence == null ? Collections.<StockLearningCardEvidence>emptyList() : evidence) {
            jdbcTemplate.update("INSERT INTO stock_learning_card_evidence(run_id,dimension_code,evidence_code,title,url,source,published_at,content,content_origin,sort_order) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    runId, item.getDimensionCode(), item.getEvidenceCode(), item.getTitle(), item.getUrl(), item.getSource(),
                    item.getPublishedAt(), item.content(), item.getContentOrigin(), item.getSortOrder());
        }
    }
    public Optional<StockLearningCardRun> findRun(Long id) {
        List<StockLearningCardRun> values = jdbcTemplate.query("SELECT * FROM stock_learning_card_run WHERE id=?", runMapper, id);
        if (values.isEmpty()) return Optional.empty(); StockLearningCardRun value = values.get(0); value.setClaims(claims(id)); value.setEvidence(evidence(id)); value.setWatchItems(watches(id)); return Optional.of(value);
    }
    public Optional<StockLearningCardRun> latest(Long cardId) {
        List<Long> ids = jdbcTemplate.query("SELECT id FROM stock_learning_card_run WHERE card_id=? ORDER BY id DESC LIMIT 1", (rs,row)->rs.getLong(1), cardId);
        return ids.isEmpty() ? Optional.empty() : findRun(ids.get(0));
    }
    public Optional<StockLearningCardRun> active(Long cardId) {
        List<Long> ids = jdbcTemplate.query("SELECT id FROM stock_learning_card_run WHERE card_id=? AND status='RUNNING' ORDER BY id DESC LIMIT 1",
                (rs, row) -> rs.getLong(1), cardId);
        return ids.isEmpty() ? Optional.empty() : findRun(ids.get(0));
    }
    public List<StockLearningCardSummary> summaries() {
        return jdbcTemplate.query("SELECT i.code,i.name,r.status,r.stage,r.summary,c.updated_at,r.completed_at,"
                        + "(SELECT COUNT(*) FROM stock_learning_card_claim cl WHERE cl.run_id=r.id AND cl.status='READY') completed_dimensions "
                        + "FROM stock_learning_card c JOIN instrument i ON i.id=c.instrument_id "
                        + "JOIN stock_learning_card_run r ON r.id=c.latest_run_id ORDER BY c.updated_at DESC,c.id DESC",
                (rs, row) -> {
                    StockLearningCardSummary value = new StockLearningCardSummary();
                    value.setCode(rs.getString("code")); value.setName(rs.getString("name"));
                    value.setStatus(rs.getString("status")); value.setStage(rs.getString("stage"));
                    value.setSummary(rs.getString("summary")); value.setCompletedDimensions(rs.getInt("completed_dimensions"));
                    value.setTotalDimensions(6); value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
                    value.setCompletedAt(TimeUtil.localDateTime(rs, "completed_at")); return value;
                });
    }
    private List<StockLearningCardClaim> claims(Long runId) { return jdbcTemplate.query("SELECT * FROM stock_learning_card_claim WHERE run_id=? ORDER BY sort_order,id", (rs,row)-> { StockLearningCardClaim value=new StockLearningCardClaim(); value.setId(rs.getLong("id")); value.setRunId(rs.getLong("run_id")); value.setDimensionCode(rs.getString("dimension_code")); value.setStatus(rs.getString("status")); value.setFailureMessage(rs.getString("failure_message")); value.setJudgment(rs.getString("judgment")); value.setRationale(rs.getString("rationale")); value.setCounterargument(rs.getString("counterargument")); value.setUnknowns(rs.getString("unknowns")); value.setConfidence(rs.getString("confidence")); value.setSortOrder(rs.getInt("sort_order")); return value; }, runId); }
    private List<StockLearningCardEvidence> evidence(Long runId) { return jdbcTemplate.query("SELECT * FROM stock_learning_card_evidence WHERE run_id=? ORDER BY dimension_code,sort_order,id", (rs,row)-> { StockLearningCardEvidence value=new StockLearningCardEvidence(); value.setDatabaseId(rs.getLong("id")); value.setRunId(rs.getLong("run_id")); value.setDimensionCode(rs.getString("dimension_code")); value.setEvidenceCode(rs.getString("evidence_code")); value.setTitle(rs.getString("title")); value.setUrl(rs.getString("url")); value.setSource(rs.getString("source")); value.setPublishedAt(rs.getString("published_at")); value.content(rs.getString("content")); value.setContentOrigin(rs.getString("content_origin")); value.setSortOrder(rs.getInt("sort_order")); return value; }, runId); }
    private List<StockLearningCardWatchItem> watches(Long runId) { return jdbcTemplate.query("SELECT * FROM stock_learning_card_watch_item WHERE run_id=? ORDER BY sort_order,id", (rs,row)-> { StockLearningCardWatchItem value=new StockLearningCardWatchItem(); value.setId(rs.getLong("id")); value.setRunId(rs.getLong("run_id")); value.setMetric(rs.getString("metric")); value.setBaseline(rs.getString("baseline")); value.setFrequency(rs.getString("frequency")); value.setUpgradeCondition(rs.getString("upgrade_condition")); value.setDowngradeCondition(rs.getString("downgrade_condition")); value.setNextReviewAt(TimeUtil.localDateTime(rs,"next_review_at")); value.setSortOrder(rs.getInt("sort_order")); return value; }, runId); }
}
