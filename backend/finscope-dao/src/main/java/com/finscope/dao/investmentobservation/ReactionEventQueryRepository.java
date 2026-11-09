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
        List<String> filters = new ArrayList<>();
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        if (query.getEventType() != null) {
            filters.add("(CASE WHEN s.case_subtype GLOB 'EARNINGS_*' THEN 'EARNINGS' "
                    + "WHEN s.case_subtype GLOB 'CONTRACT_*' THEN 'CONTRACT' ELSE json_extract(s.snapshot_json,'$.eventType') END)=?");
            params.add(query.getEventType().name());
        }
        if (query.getEventSubtype() != null) {
            filters.add("COALESCE(s.case_subtype,json_extract(s.snapshot_json,'$.eventSubtype'))=?");
            params.add(query.getEventSubtype().name());
        }
        if (!query.getQuery().isBlank()) {
            String company = "COALESCE(json_extract(s.snapshot_json,'$.instrumentName'),'')";
            String text = query.isCompanyOnly() ? company : "COALESCE(json_extract(s.snapshot_json,'$.title'),'') || ' ' "
                    + "|| COALESCE(json_extract(s.snapshot_json,'$.summary'),'') || ' ' || " + company;
            filters.add("(instr(lower(" + text + "),lower(?))>0 OR instr(lower(s.instrument_code),lower(?))>0)");
            params.add(query.getQuery());
            params.add(query.getQuery());
        }
        if (query.isFollowed()) {
            filters.add("s.followed=1");
        }
        if (query.isChangedToday()) {
            filters.add("EXISTS(SELECT 1 FROM investment_reaction_change c WHERE c.event_key=s.source_identity AND c.detected_at>=?)");
            params.add(today);
        }
        if (query.getResolutionStatus() != null) {
            filters.add("COALESCE(json_extract(s.snapshot_json,'$.resolutionStatus'),'PENDING')=?");
            params.add(query.getResolutionStatus().name());
        }
        String matches = filters.isEmpty() ? "1" : String.join(" AND ", filters);
        params.add(query.getAnchor());
        String grouped = "WITH source_events AS (SELECT s.source_identity AS event_key,"
                + "COALESCE(MIN(CASE WHEN s.excluded=0 AND s.state='OBSERVING' THEN s.id END),MIN(s.id)) AS representative,"
                + "MIN(CASE WHEN s.excluded=1 THEN s.id END) AS invalid_representative,MAX(s.id) AS newest,"
                + "COUNT(DISTINCT CASE WHEN s.excluded=0 THEN NULLIF(s.instrument_code,'') END) AS stock_count,"
                + "GROUP_CONCAT(DISTINCT CASE WHEN s.excluded=0 THEN json_extract(s.snapshot_json,'$.instrumentName') END) AS stock_names,"
                + "SUM(s.excluded) AS invalid_count,MIN(s.case_key) AS case_key,"
                + "MIN(CASE WHEN s.excluded=0 THEN json_extract(s.snapshot_json,'$.publishedAt') END) AS published_at,"
                + "MAX(CASE WHEN " + matches + " THEN 1 ELSE 0 END) AS matches,"
                + "CASE WHEN MIN(s.excluded)=1 THEN 'EXCLUDED' "
                + "WHEN MAX(CASE WHEN s.state='OBSERVING' AND s.excluded=0 AND COALESCE(json_extract(s.calculation_json,'$.profile.windowEnded'),0)=0 AND s.completed=0 THEN 1 ELSE 0 END)=1 THEN 'TRACKING' "
                + "WHEN MAX(CASE WHEN s.state='DRAFT' AND s.excluded=0 THEN 1 ELSE 0 END)=1 THEN 'PENDING' ELSE 'HISTORY' END AS view "
                + "FROM investment_reaction_sample s WHERE s.id<=? GROUP BY s.source_identity), "
                + "case_events AS (SELECT *,CASE WHEN view='HISTORY' AND stock_count=1 AND invalid_count=0 AND case_key IS NOT NULL "
                + "THEN 'CASE:' || case_key ELSE 'SOURCE:' || event_key END AS grouping_key FROM source_events), "
                + "ranked AS (SELECT *,ROW_NUMBER() OVER(PARTITION BY grouping_key ORDER BY published_at IS NULL,published_at,representative) AS rank,"
                + "COUNT(*) OVER(PARTITION BY grouping_key) AS report_count,MAX(matches) OVER(PARTITION BY grouping_key) AS case_matches,"
                + "MAX(newest) OVER(PARTITION BY grouping_key) AS case_newest FROM case_events), "
                + "events AS (SELECT * FROM ranked WHERE rank=1 AND case_matches=1) ";
        ReactionEventPage result = new ReactionEventPage();
        var counts = new LinkedHashMap<String, Long>();
        jdbcTemplate.query(grouped + "SELECT view,COUNT(*) AS total FROM events WHERE view<>'EXCLUDED' GROUP BY view "
                + "UNION ALL SELECT 'EXCLUDED',COUNT(*) FROM events WHERE invalid_count>0", rs -> {
            counts.put(rs.getString("view"), rs.getLong("total"));
        }, params.toArray());
        result.setCounts(counts);
        result.setTotal(counts.getOrDefault(query.getView().name(), 0L));
        List<Object> pageParams = new ArrayList<>(params);
        boolean excludedView = query.getView() == com.finscope.common.enums.investmentobservation.ReactionWorkspaceView.EXCLUDED;
        if (!excludedView) {
            pageParams.add(query.getView().name());
        }
        pageParams.add(query.getSize());
        pageParams.add((query.getPage() - 1L) * query.getSize());
        var rows = jdbcTemplate.queryForList(grouped + "SELECT * FROM events WHERE " + (excludedView ? "invalid_count>0" : "view=?") + " ORDER BY case_newest DESC LIMIT ? OFFSET ?", pageParams.toArray());
        var items = new ArrayList<com.finscope.domain.investmentobservation.ReactionSample>();
        var stockCounts = new LinkedHashMap<String, Integer>();
        var stockNames = new LinkedHashMap<String, String>();
        var caseReportCounts = new LinkedHashMap<String, Integer>();
        for (var row : rows) {
            String key = (String) row.get("event_key");
            long id = ((Number) row.get(excludedView ? "invalid_representative" : "representative")).longValue();
            var representative = samples.findById(id).orElseThrow(() ->
                    new com.finscope.common.exception.BusinessException(com.finscope.common.exception.ErrorCode.DATA_INTEGRITY_ERROR));
            items.add(representative);
            stockCounts.put(key, ((Number) row.get("stock_count")).intValue());
            stockNames.put(key, (String) row.get("stock_names"));
            caseReportCounts.put(key, ((Number) row.get("report_count")).intValue());
        }
        result.setItems(items);
        result.setStockCounts(stockCounts);
        result.setStockNames(stockNames);
        result.setCaseReportCounts(caseReportCounts);
        result.setAnchor(query.getAnchor() == Long.MAX_VALUE ? jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id),0) FROM investment_reaction_sample", Long.class) : query.getAnchor());
        result.setRevision(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(id+revision+followed+excluded),0) FROM investment_reaction_sample", Long.class));
        var reasons = new LinkedHashMap<String, Long>();
        jdbcTemplate.query("SELECT COALESCE(json_extract(snapshot_json,'$.resolutionStatus'),'PENDING') AS reason,COUNT(DISTINCT source_identity) AS total "
                + "FROM investment_reaction_sample WHERE state='DRAFT' AND excluded=0 GROUP BY reason", rs -> {
            reasons.put(rs.getString("reason"), rs.getLong("total"));
        });
        result.setPendingReasons(reasons);
        var metrics = jdbcTemplate.queryForMap("SELECT COUNT(DISTINCT source_identity) AS total,"
                + "COUNT(DISTINCT CASE WHEN instrument_code<>'' THEN source_identity END) AS linked,"
                + "MIN(CASE WHEN state='DRAFT' THEN registered_at END) AS oldest "
                + "FROM investment_reaction_sample WHERE excluded=0 AND json_extract(snapshot_json,'$.automatic')=1");
        result.setAutomaticEvents(((Number) metrics.get("total")).longValue());
        result.setLinkedEvents(((Number) metrics.get("linked")).longValue());
        result.setOldestPendingAt((String) metrics.get("oldest"));
        result.setPage(query.getPage());
        result.setSize(query.getSize());
        return result;
    }
}
