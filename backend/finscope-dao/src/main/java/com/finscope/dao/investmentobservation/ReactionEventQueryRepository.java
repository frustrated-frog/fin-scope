package com.finscope.dao.investmentobservation;

import com.finscope.domain.investmentobservation.ReactionEventPage;
import com.finscope.domain.investmentobservation.ReactionEventQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** 先按稳定事件分组，再过滤分页；数量来自完整数据库，不依赖客户端已加载记录。 */
@Repository
public class ReactionEventQueryRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ReactionSampleRepository samples;

    public ReactionEventPage query(ReactionEventQuery query) {
        if (query.getAnchor() == Long.MAX_VALUE) {
            query.setAnchor(jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id),0) FROM investment_reaction_sample", Long.class));
        }
        List<Object> params = new ArrayList<>();
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        params.add(query.getAnchor());
        String where = " WHERE s.id<=?";
        if (query.getEventType() != null) {
            where += " AND json_extract(s.snapshot_json,'$.eventType')=?";
            params.add(query.getEventType().name());
        }
        if (!query.getQuery().isBlank()) {
            where += " AND (instr(lower(COALESCE(json_extract(s.snapshot_json,'$.title'),'') || ' ' || COALESCE(json_extract(s.snapshot_json,'$.summary'),'') || ' ' || COALESCE(json_extract(s.snapshot_json,'$.instrumentName'),'')),lower(?))>0 OR instr(lower(s.instrument_code),lower(?))>0)";
            params.add(query.getQuery());
            params.add(query.getQuery());
        }
        if (query.isFollowed()) {
            where += " AND s.followed=1";
        }
        if (query.isChangedToday()) {
            where += " AND EXISTS(SELECT 1 FROM investment_reaction_change c WHERE c.event_key=s.source_identity AND c.detected_at>=?)";
            params.add(today);
        }
        if (query.getResolutionStatus() != null) {
            where += " AND COALESCE(json_extract(s.snapshot_json,'$.resolutionStatus'),'PENDING')=?";
            params.add(query.getResolutionStatus().name());
        }
        String grouped = "WITH events AS (SELECT s.source_identity AS event_key,MIN(s.id) AS representative,MAX(s.id) AS newest,"
                + "COUNT(DISTINCT NULLIF(s.instrument_code,'')) AS stock_count,"
                + "GROUP_CONCAT(DISTINCT json_extract(s.snapshot_json,'$.instrumentName')) AS stock_names,"
                + "CASE WHEN MIN(s.excluded)=1 THEN 'EXCLUDED' "
                + "WHEN MAX(CASE WHEN s.state='OBSERVING' AND s.excluded=0 AND COALESCE(json_extract(s.calculation_json,'$.profile.windowEnded'),0)=0 AND s.completed=0 THEN 1 ELSE 0 END)=1 THEN 'TRACKING' "
                + "WHEN MAX(CASE WHEN s.state='DRAFT' AND s.excluded=0 THEN 1 ELSE 0 END)=1 THEN 'PENDING' ELSE 'HISTORY' END AS view "
                + "FROM investment_reaction_sample s" + where + " GROUP BY s.source_identity) ";
        ReactionEventPage result = new ReactionEventPage();
        var counts = new LinkedHashMap<String, Long>();
        jdbcTemplate.query(grouped + "SELECT view,COUNT(*) AS total FROM events GROUP BY view", rs -> {
            counts.put(rs.getString("view"), rs.getLong("total"));
        }, params.toArray());
        result.setCounts(counts);
        result.setTotal(counts.getOrDefault(query.getView().name(), 0L));
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(query.getView().name());
        pageParams.add(query.getSize());
        pageParams.add((query.getPage() - 1L) * query.getSize());
        var rows = jdbcTemplate.queryForList(grouped + "SELECT * FROM events WHERE view=? ORDER BY newest DESC LIMIT ? OFFSET ?", pageParams.toArray());
        var items = new ArrayList<com.finscope.domain.investmentobservation.ReactionSample>();
        var stockCounts = new LinkedHashMap<String, Integer>();
        var stockNames = new LinkedHashMap<String, String>();
        for (var row : rows) {
            String key = (String) row.get("event_key");
            // 原始入口可能对应已归档/作废成员，选择同事件中的有效成员作为摘要。
            var peers = samples.findByIdentity(key);
            var representative = peers.stream().filter(value -> !value.isExcluded() && value.getState() == com.finscope.common.enums.investmentobservation.ReactionSampleState.OBSERVING)
                    .findFirst().orElse(peers.get(0));
            items.add(representative);
            stockCounts.put(key, ((Number) row.get("stock_count")).intValue());
            stockNames.put(key, (String) row.get("stock_names"));
        }
        result.setItems(items);
        result.setStockCounts(stockCounts);
        result.setStockNames(stockNames);
        result.setAnchor(query.getAnchor() == Long.MAX_VALUE ? jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id),0) FROM investment_reaction_sample", Long.class) : query.getAnchor());
        result.setRevision(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(id+revision+followed+excluded),0) FROM investment_reaction_sample", Long.class));
        var reasons = new LinkedHashMap<String, Long>();
        jdbcTemplate.query("SELECT COALESCE(json_extract(snapshot_json,'$.resolutionStatus'),'PENDING') AS reason,COUNT(DISTINCT source_identity) AS total "
                + "FROM investment_reaction_sample WHERE state='DRAFT' AND excluded=0 GROUP BY reason", rs -> {
            reasons.put(rs.getString("reason"), rs.getLong("total"));
        });
        result.setPendingReasons(reasons);
        result.setPage(query.getPage());
        result.setSize(query.getSize());
        return result;
    }
}
