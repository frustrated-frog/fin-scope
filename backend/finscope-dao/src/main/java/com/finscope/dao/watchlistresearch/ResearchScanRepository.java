package com.finscope.dao.watchlistresearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.watchlistresearch.ResearchScanStatus;
import com.finscope.common.enums.watchlistresearch.ResearchScanStage;
import com.finscope.domain.watchlistresearch.ResearchScanRun;
import com.finscope.domain.watchlistresearch.WatchlistResearchSnapshot;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import javax.annotation.Resource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@DependsOn("researchScanSchemaMigrator")
public class ResearchScanRepository {
    private static final String SUMMARY = "id,scan_date,membership_key,status,stage,processed_stocks,"
            + "total_stocks,message,started_at,finished_at";
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private ObjectMapper json;

    public boolean tryStart(ResearchScanRun run, boolean automatic) {
        return jdbc.update("INSERT INTO watchlist_research_scan(" + SUMMARY + ",active_slot,automatic_key) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,1,?) ON CONFLICT DO NOTHING", run.getId(), run.getScanDate().toString(),
                run.getMembershipKey(), ResearchScanStatus.RUNNING.name(), ResearchScanStage.MARKET.name(), 0, run.getTotalStocks(),
                run.getMessage(), run.getStartedAt().toString(), null,
                automatic ? run.getMembershipKey() + ":" + run.getScanDate() : null) == 1;
    }

    public void progress(String id, ResearchScanStage stage, int processed, String message) {
        jdbc.update("UPDATE watchlist_research_scan SET stage=?,processed_stocks=?,message=? "
                + "WHERE id=? AND status='RUNNING'", stage.name(), processed, message, id);
    }

    public boolean finish(ResearchScanRun run) {
        if (run.getStatus() == null || run.getStatus() == ResearchScanStatus.RUNNING
                || (run.getStatus().isCompleted() && run.getSnapshot() == null)) {
            throw new IllegalArgumentException("扫描终态必须包含对应的结果");
        }
        return jdbc.update("UPDATE watchlist_research_scan SET status=?,stage='COMPLETED',active_slot=NULL,"
                        + "processed_stocks=?,message=?,finished_at=?,snapshot_json=? WHERE id=? AND status='RUNNING'",
                run.getStatus().name(), run.getProcessedStocks(), run.getMessage(),
                run.getFinishedAt().toString(), encode(run.getSnapshot()), run.getId()) == 1;
    }

    public void recoverInterrupted() {
        jdbc.update("UPDATE watchlist_research_scan SET status='FAILED',stage='COMPLETED',active_slot=NULL,finished_at=?,"
                + "message='上次扫描被应用重启中断，可重新扫描' WHERE status='RUNNING'", Instant.now().toString());
    }

    public Optional<ResearchScanRun> active() {
        return first(jdbc.query("SELECT " + SUMMARY + " FROM watchlist_research_scan WHERE active_slot=1", mapper(false)));
    }

    public Optional<ResearchScanRun> latestAttempt(String membershipKey, LocalDate date) {
        return first(jdbc.query("SELECT " + SUMMARY + " FROM watchlist_research_scan "
                + "WHERE membership_key=? AND scan_date=? ORDER BY started_at DESC,id DESC LIMIT 1",
                mapper(false), membershipKey, date.toString()));
    }

    public Optional<ResearchScanRun> latestCompleted(String membershipKey) {
        return first(jdbc.query("SELECT " + SUMMARY + ",snapshot_json FROM watchlist_research_scan "
                + "WHERE membership_key=? AND status IN ('READY','PARTIAL') "
                + "ORDER BY started_at DESC,id DESC LIMIT 1", mapper(true), membershipKey));
    }

    public List<ResearchScanRun> history() {
        return jdbc.query("SELECT " + SUMMARY + " FROM watchlist_research_scan "
                + "WHERE status IN ('READY','PARTIAL') ORDER BY started_at DESC,id DESC LIMIT 7", mapper(false));
    }

    public Optional<ResearchScanRun> find(String id) {
        return first(jdbc.query("SELECT " + SUMMARY + ",snapshot_json FROM watchlist_research_scan WHERE id=?",
                mapper(true), id));
    }

    private RowMapper<ResearchScanRun> mapper(boolean includeSnapshot) {
        return (rs, row) -> {
            ResearchScanRun run = new ResearchScanRun();
            run.setId(rs.getString("id"));
            run.setScanDate(LocalDate.parse(rs.getString("scan_date")));
            run.setMembershipKey(rs.getString("membership_key"));
            run.setStatus(ResearchScanStatus.valueOf(rs.getString("status")));
            run.setStage(ResearchScanStage.valueOf(rs.getString("stage")));
            run.setProcessedStocks(rs.getInt("processed_stocks"));
            run.setTotalStocks(rs.getInt("total_stocks"));
            run.setMessage(rs.getString("message"));
            run.setStartedAt(Instant.parse(rs.getString("started_at")));
            String finishedAt = rs.getString("finished_at");
            run.setFinishedAt(finishedAt == null ? null : Instant.parse(finishedAt));
            if (includeSnapshot) {
                run.setSnapshot(decode(rs.getString("snapshot_json")));
            }
            return run;
        };
    }

    private String encode(WatchlistResearchSnapshot snapshot) {
        try {
            return snapshot == null ? null : json.writeValueAsString(snapshot);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("扫描快照序列化失败", error);
        }
    }

    private WatchlistResearchSnapshot decode(String payload) {
        try {
            return payload == null ? null : json.readValue(payload, WatchlistResearchSnapshot.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("扫描快照读取失败", error);
        }
    }

    private Optional<ResearchScanRun> first(List<ResearchScanRun> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
