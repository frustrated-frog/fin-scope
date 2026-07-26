package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.ResearchRunOutput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ResearchRunOutputRepository {
    @Resource private JdbcTemplate jdbcTemplate;
    public void record(Long runId, String type, Long outputId) {
        jdbcTemplate.update("INSERT OR IGNORE INTO research_run_output(research_run_id,output_type,output_id,created_at) VALUES(?,?,?,?)",
                runId, type, outputId, TimeUtil.text(LocalDateTime.now()));
    }
    public int countByRunIdAndType(Long runId, String type) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM research_run_output WHERE research_run_id=? AND output_type=?", Integer.class, runId, type);
        return count == null ? 0 : count;
    }
    public int countDistinctArticleSources(Long runId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT CASE WHEN a.source_id IS NOT NULL "
                        + "THEN 'id:' || a.source_id ELSE 'name:' || COALESCE(a.source_name,'unknown') END) "
                        + "FROM research_run_output o JOIN article a ON a.id=o.output_id "
                        + "WHERE o.research_run_id=? AND o.output_type='ARTICLE'",
                Integer.class, runId);
        return count == null ? 0 : count;
    }
    public List<ResearchRunOutput> findByRunId(Long runId) {
        return jdbcTemplate.query("SELECT * FROM research_run_output WHERE research_run_id=? ORDER BY id ASC", (rs, row) -> {
            ResearchRunOutput item = new ResearchRunOutput(); item.setId(rs.getLong("id")); item.setResearchRunId(rs.getLong("research_run_id")); item.setOutputType(rs.getString("output_type")); item.setOutputId(rs.getLong("output_id")); item.setCreatedAt(TimeUtil.localDateTime(rs, "created_at")); return item;
        }, runId);
    }

    public int deleteByRunIdAndType(Long runId, String type) {
        return jdbcTemplate.update("DELETE FROM research_run_output WHERE research_run_id=? AND output_type=?",
                runId, type);
    }
}
