package com.finscope.dao.marketintel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.marketintel.CapitalHypothesis;
import com.finscope.domain.marketintel.CapitalInterpretation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class CapitalInterpretationRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public CapitalInterpretationRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public CapitalInterpretation save(CapitalInterpretation value) {
        try {
            if (value.getCreatedAt() == null) value.setCreatedAt(LocalDateTime.now());
            value.setUpdatedAt(LocalDateTime.now());
            final String factsJson = json.writeValueAsString(value.getFacts());
            final String hypothesesJson = json.writeValueAsString(value.getHypotheses());
            final String dataGapsJson = json.writeValueAsString(value.getDataGaps());
            final String observationPointsJson = json.writeValueAsString(value.getObservationPoints());
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(c -> {
                PreparedStatement ps = c.prepareStatement("INSERT INTO market_capital_interpretation(" +
                        "instrument_id,snapshot_id,interpretation_type,status,plain_summary,facts_json,hypotheses_json," +
                        "data_gaps_json,observation_points_json,disclaimer,fallback_reason,rule_version,model_name," +
                        "prompt_version,input_hash,output_hash,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                Object[] args = {value.getInstrumentId(), value.getSnapshotId(), value.getInterpretationType(), value.getStatus(),
                        value.getPlainSummary(), factsJson, hypothesesJson, dataGapsJson, observationPointsJson,
                        value.getDisclaimer(), value.getFallbackReason(), value.getRuleVersion(), value.getModelName(),
                        value.getPromptVersion(), value.getInputHash(), value.getOutputHash(), value.getCreatedAt().toString(),
                        value.getUpdatedAt().toString()};
                for (int i=0;i<args.length;i++) ps.setObject(i+1,args[i]); return ps;
            }, keys);
            value.setId(keys.getKey().longValue()); return value;
        } catch (Exception e) { throw new IllegalStateException("cannot persist capital interpretation", e); }
    }
    public void update(CapitalInterpretation value) {
        try {
            value.setUpdatedAt(LocalDateTime.now());
            jdbc.update("UPDATE market_capital_interpretation SET status=?,plain_summary=?,facts_json=?," +
                            "hypotheses_json=?,data_gaps_json=?,observation_points_json=?,disclaimer=?,fallback_reason=?," +
                            "output_hash=?,rule_version=?,model_name=?,prompt_version=?,updated_at=? WHERE id=?", value.getStatus(), value.getPlainSummary(),
                    json.writeValueAsString(value.getFacts()), json.writeValueAsString(value.getHypotheses()),
                    json.writeValueAsString(value.getDataGaps()), json.writeValueAsString(value.getObservationPoints()),
                    value.getDisclaimer(), value.getFallbackReason(), value.getOutputHash(), value.getRuleVersion(), value.getModelName(), value.getPromptVersion(), value.getUpdatedAt().toString(), value.getId());
        } catch (Exception e) { throw new IllegalStateException("cannot update capital interpretation id=" + value.getId(), e); }
    }
    public Optional<CapitalInterpretation> findById(Long id) {
        List<CapitalInterpretation> rows = jdbc.query("SELECT * FROM market_capital_interpretation WHERE id=?", (rs,n)-> {
            try {
                CapitalInterpretation v=new CapitalInterpretation(); v.setId(rs.getLong("id"));
                v.setInstrumentId(rs.getLong("instrument_id")); v.setSnapshotId(rs.getLong("snapshot_id"));
                v.setInterpretationType(rs.getString("interpretation_type")); v.setStatus(rs.getString("status"));
                v.setPlainSummary(rs.getString("plain_summary"));
                v.setFacts(json.readValue(rs.getString("facts_json"), new TypeReference<List<String>>(){}));
                v.setHypotheses(json.readValue(rs.getString("hypotheses_json"), new TypeReference<List<CapitalHypothesis>>(){}));
                v.setDataGaps(json.readValue(rs.getString("data_gaps_json"), new TypeReference<List<String>>(){}));
                v.setObservationPoints(json.readValue(rs.getString("observation_points_json"), new TypeReference<List<String>>(){}));
                v.setDisclaimer(rs.getString("disclaimer")); v.setFallbackReason(rs.getString("fallback_reason"));
                v.setRuleVersion(rs.getString("rule_version")); v.setModelName(rs.getString("model_name"));
                v.setPromptVersion(rs.getString("prompt_version")); v.setInputHash(rs.getString("input_hash"));
                v.setOutputHash(rs.getString("output_hash")); v.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
                v.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at"))); return v;
            } catch(Exception e){ throw new IllegalStateException("cannot read capital interpretation id="+id,e); }
        },id); return rows.isEmpty()?Optional.empty():Optional.of(rows.get(0));
    }
    public Optional<CapitalInterpretation> findByAction(Long snapshotId,String type,String hash){
        List<Long> ids=jdbc.query("SELECT id FROM market_capital_interpretation WHERE snapshot_id=? AND interpretation_type=? AND input_hash=?",(rs,n)->rs.getLong(1),snapshotId,type,hash);
        return ids.isEmpty()?Optional.empty():findById(ids.get(0));
    }
}
