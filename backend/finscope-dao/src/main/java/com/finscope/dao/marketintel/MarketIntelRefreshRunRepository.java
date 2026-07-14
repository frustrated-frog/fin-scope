package com.finscope.dao.marketintel;

import com.finscope.domain.marketintel.MarketIntelRefreshRun;
import com.finscope.domain.marketintel.MarketIntelRefreshStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MarketIntelRefreshRunRepository {
    private final JdbcTemplate jdbc;
    public MarketIntelRefreshRunRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public MarketIntelRefreshRun createRun(Long instrumentId,String trigger){
        LocalDateTime now=LocalDateTime.now(); KeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO market_intel_refresh_run("+
                "instrument_id,trigger_type,status,started_at) VALUES(?,?,'PENDING',?)",Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,instrumentId);ps.setString(2,trigger);ps.setString(3,now.toString());return ps;},keys);
        MarketIntelRefreshRun run=new MarketIntelRefreshRun();run.setId(keys.getKey().longValue());run.setInstrumentId(instrumentId);
        run.setTriggerType(trigger);run.setStatus(MarketIntelRefreshRun.Status.PENDING);run.setStartedAt(now);return run;
    }
    public MarketIntelRefreshStep createStep(Long runId,String dimension,String provider,int attempt){
        LocalDateTime now=LocalDateTime.now();KeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement("INSERT INTO market_intel_refresh_step("+
                "run_id,dimension,provider_code,attempt,status,started_at) VALUES(?,?,?,?,'PENDING',?)",Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,runId);ps.setString(2,dimension);ps.setString(3,provider);ps.setInt(4,attempt);ps.setString(5,now.toString());return ps;},keys);
        MarketIntelRefreshStep step=new MarketIntelRefreshStep();step.setId(keys.getKey().longValue());step.setRunId(runId);
        step.setDimension(dimension);step.setProviderCode(provider);step.setAttempt(attempt);step.setStatus(MarketIntelRefreshStep.Status.PENDING);step.setStartedAt(now);return step;
    }
    public void updateStep(Long id,MarketIntelRefreshStep.Status status,int output,String errorType,String errorMessage){
        jdbc.update("UPDATE market_intel_refresh_step SET status=?,output_count=?,error_type=?,error_message=?,finished_at=? WHERE id=?",
                status.name(),output,errorType,errorMessage,status.isTerminal()?LocalDateTime.now().toString():null,id);
    }
    public void finishRun(Long id,MarketIntelRefreshRun.Status status,int success,int failure){
        jdbc.update("UPDATE market_intel_refresh_run SET status=?,success_count=?,failure_count=?,finished_at=? WHERE id=?",
                status.name(),success,failure,LocalDateTime.now().toString(),id);
    }
    public Optional<MarketIntelRefreshRun> findRunById(Long id){
        return jdbc.query("SELECT r.*,s.error_type AS step_error_type,s.error_message AS step_error_message " +
                "FROM market_intel_refresh_run r LEFT JOIN market_intel_refresh_step s ON s.id=(" +
                "SELECT id FROM market_intel_refresh_step WHERE run_id=r.id ORDER BY id DESC LIMIT 1) WHERE r.id=?",(rs,rowNum)->{
            MarketIntelRefreshRun run=new MarketIntelRefreshRun();run.setId(rs.getLong("id"));run.setInstrumentId(rs.getLong("instrument_id"));
            run.setTriggerType(rs.getString("trigger_type"));run.setStatus(MarketIntelRefreshRun.Status.valueOf(rs.getString("status")));
            run.setSuccessCount(rs.getInt("success_count"));run.setFailureCount(rs.getInt("failure_count"));run.setStartedAt(LocalDateTime.parse(rs.getString("started_at")));
            run.setErrorType(rs.getString("step_error_type"));run.setErrorMessage(rs.getString("step_error_message"));
            String finished=rs.getString("finished_at");run.setFinishedAt(finished==null?null:LocalDateTime.parse(finished));return run;
        },id).stream().findFirst();
    }
}
