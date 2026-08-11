package com.finscope.dao.industrychain;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.industrychain.IndustryChainEventImpact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/** 只持久化 Radar 事件与产业链之间的影响关系，不复制新闻内容。 */
@Repository
public class IndustryChainEventImpactRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Transactional(rollbackFor = Exception.class)
    public boolean upsert(IndustryChainEventImpact impact, LocalDateTime now) {
        boolean created = count(impact.getChainId(), impact.getRadarEventId()) == 0;
        String timestamp = TimeUtil.text(now);
        jdbcTemplate.update("INSERT INTO industry_chain_event_impact(chain_id,radar_event_id,direct_node_key,direction,"
                        + "mechanism,horizon,confidence,impact_summary,analysis_version,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(chain_id,radar_event_id) DO UPDATE SET "
                        + "direct_node_key=excluded.direct_node_key,direction=excluded.direction,mechanism=excluded.mechanism,"
                        + "horizon=excluded.horizon,confidence=excluded.confidence,impact_summary=excluded.impact_summary,"
                        + "analysis_version=excluded.analysis_version,updated_at=excluded.updated_at",
                impact.getChainId(), impact.getRadarEventId(), impact.getDirectNodeKey(), impact.getDirection(),
                impact.getMechanism(), impact.getHorizon(), impact.getConfidence(), impact.getImpactSummary(),
                impact.getAnalysisVersion(), timestamp, timestamp);
        Long impactId = jdbcTemplate.queryForObject("SELECT id FROM industry_chain_event_impact WHERE chain_id=? AND radar_event_id=?",
                Long.class, impact.getChainId(), impact.getRadarEventId());
        jdbcTemplate.update("DELETE FROM industry_chain_event_path WHERE impact_id=?", impactId);
        int position = 0;
        for (String nodeKey : impact.getPathNodeKeys()) {
            jdbcTemplate.update("INSERT INTO industry_chain_event_path(impact_id,node_key,position) VALUES(?,?,?)",
                    impactId, nodeKey, position++);
        }
        return created;
    }

    public List<IndustryChainEventImpact> findByChainId(Long chainId) {
        List<IndustryChainEventImpact> impacts = jdbcTemplate.query("SELECT * FROM industry_chain_event_impact "
                        + "WHERE chain_id=? ORDER BY updated_at DESC,id DESC",
                (rs, row) -> {
                    IndustryChainEventImpact value = new IndustryChainEventImpact();
                    value.setId(rs.getLong("id"));
                    value.setChainId(rs.getLong("chain_id"));
                    value.setRadarEventId(rs.getLong("radar_event_id"));
                    value.setDirectNodeKey(rs.getString("direct_node_key"));
                    value.setDirection(rs.getString("direction"));
                    value.setMechanism(rs.getString("mechanism"));
                    value.setHorizon(rs.getString("horizon"));
                    value.setConfidence(rs.getString("confidence"));
                    value.setImpactSummary(rs.getString("impact_summary"));
                    value.setAnalysisVersion(rs.getString("analysis_version"));
                    value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
                    value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
                    return value;
                }, chainId);
        for (IndustryChainEventImpact impact : impacts) {
            impact.setPathNodeKeys(readPath(impact.getId()));
        }
        return impacts;
    }

    public int countByChainId(Long chainId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM industry_chain_event_impact WHERE chain_id=?",
                Integer.class, chainId);
    }

    public Set<Long> findRadarEventIds(Long chainId) {
        return new LinkedHashSet<Long>(jdbcTemplate.query("SELECT radar_event_id FROM industry_chain_event_impact "
                        + "WHERE chain_id=? ORDER BY radar_event_id",
                (rs, row) -> rs.getLong("radar_event_id"), chainId));
    }

    private int count(Long chainId, Long eventId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM industry_chain_event_impact WHERE chain_id=? AND radar_event_id=?",
                Integer.class, chainId, eventId);
    }

    private List<String> readPath(Long impactId) {
        return new ArrayList<String>(jdbcTemplate.query("SELECT node_key FROM industry_chain_event_path "
                        + "WHERE impact_id=? ORDER BY position",
                (rs, row) -> rs.getString("node_key"), impactId));
    }
}
