package com.finscope.dao.news;

import com.finscope.domain.news.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class NewsReportRepository {
    private static final String FROM = " FROM news_report n LEFT JOIN news_report_read r ON r.report_id=n.id "
            + "LEFT JOIN news_ai_report a ON a.report_id=n.id AND a.content_version=n.content_version ";
    private static final String GROUP_KEY = "CASE WHEN a.event_key IS NULL THEN 'report:'||n.id ELSE 'event:'||a.event_key END";
    private static final String SELECT = "SELECT n.*,COALESCE(r.read_version,0) read_version,a.event_key,1 merged_report_count" + FROM;
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private ObjectMapper mapper;

    /** 一个批次的正文与版本原子保存；重复抓取不增加内容版本。 */
    @Transactional(rollbackFor = Exception.class)
    public void ingest(List<NewsReport> reports) {
        saveBatch(reports, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean ingestFetchBatch(List<NewsReport> reports, String batchId) {
        return saveBatch(reports, batchId);
    }

    private boolean saveBatch(List<NewsReport> reports, String batchId) {
        if (reports.size() > 250) {
            throw new IllegalArgumentException("新闻保存批次不能超过250条");
        }
        Map<String, VersionCounts> counts = new LinkedHashMap<>();
        for (NewsReport report : reports) {
            int changed = jdbc.update("INSERT INTO news_report(id,provider_code,source_name,source_tier,kind,title,content,url,"
                            + "published_at,first_seen_at,last_seen_at,category_code,category_name,classification_reason,rule_version) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                            + "content_version=news_report.content_version+CASE WHEN news_report.title<>excluded.title OR "
                            + "news_report.content<>excluded.content THEN 1 ELSE 0 END,title=excluded.title,content=excluded.content,"
                            + "url=excluded.url,published_at=COALESCE(news_report.published_at,excluded.published_at),last_seen_at=excluded.last_seen_at,"
                            + "category_code=CASE WHEN (news_report.manually_reviewed=1 OR (news_report.rule_version LIKE 'news-understanding-%' AND news_report.title=excluded.title AND news_report.content=excluded.content)) THEN news_report.category_code ELSE excluded.category_code END,"
                            + "category_name=CASE WHEN (news_report.manually_reviewed=1 OR (news_report.rule_version LIKE 'news-understanding-%' AND news_report.title=excluded.title AND news_report.content=excluded.content)) THEN news_report.category_name ELSE excluded.category_name END,"
                            + "classification_reason=CASE WHEN news_report.title=excluded.title AND news_report.content=excluded.content AND news_report.rule_version LIKE 'news-understanding-%' THEN news_report.classification_reason ELSE excluded.classification_reason END,"
                            + "rule_version=CASE WHEN news_report.title=excluded.title AND news_report.content=excluded.content AND news_report.rule_version LIKE 'news-understanding-%' THEN news_report.rule_version ELSE excluded.rule_version END",
                    report.getId(), report.getProviderCode(), report.getSourceName(), report.getSourceTier(), report.getKind(),
                    report.getTitle(), report.getContent(), report.getUrl(), time(report.getPublishedAt()), time(report.getFirstSeenAt()),
                    time(report.getLastSeenAt()), report.getCategoryCode(), report.getCategoryName(), report.getClassificationReason(), report.getRuleVersion());
            if (changed != 1) {
                throw new IllegalStateException("新闻保存影响行数异常");
            }
            var versions = jdbc.queryForList("INSERT INTO news_report_version(report_id,version,title,content,detected_at) "
                    + "SELECT id,content_version,title,content,last_seen_at FROM news_report WHERE id=? "
                    + "ON CONFLICT(report_id,version) DO NOTHING RETURNING version", Integer.class, report.getId());
            VersionCounts source = counts.computeIfAbsent(report.getProviderCode(), ignored -> new VersionCounts());
            if (versions.isEmpty()) {
                source.unchanged++;
            } else if (versions.getFirst() == 1) {
                source.added++;
            } else {
                source.revised++;
            }
        }
        if (batchId != null) {
            counts.forEach((provider, value) -> {
                if (jdbc.update("UPDATE news_fetch_batch SET added=added+?, revised=revised+?, unchanged=unchanged+? WHERE batch_id=? AND provider_code=?",
                        value.added, value.revised, value.unchanged, batchId, provider) != 1) {
                    throw new IllegalStateException("抓取批次缺少对应来源：" + provider);
                }
            });
        }
        return counts.values().stream().anyMatch(value -> value.added + value.revised > 0);
    }

    private static class VersionCounts {
        private int added;
        private int revised;
        private int unchanged;
    }

    @Transactional(rollbackFor = Exception.class)
    public void beginFetch(String batchId, LocalDateTime started, List<NewsFetchCount> sources) {
        for (NewsFetchCount source : sources) {
            jdbc.update("INSERT INTO news_fetch_batch(batch_id,provider_code,started_at,source_status,fetched,cached) VALUES(?,?,?,?,?,?)",
                    batchId, source.providerCode(), time(started), source.status().name(), source.fetched(), source.cached());
        }
        jdbc.update("DELETE FROM news_fetch_batch WHERE started_at<?", time(started.minusDays(30)));
    }

    public void finishFetch(String batchId, LocalDateTime completed) {
        jdbc.update("UPDATE news_fetch_batch SET completed_at=? WHERE batch_id=?", time(completed), batchId);
    }

    public List<NewsReport> scanAnnouncements(LocalDateTime since, LocalDateTime until, long after, int size) {
        return jdbc.query(SELECT + "WHERE n.provider_code=? AND n.published_at>=? "
                        + "AND n.published_at<=? AND n.arrival_sequence>? ORDER BY n.arrival_sequence LIMIT ?",
                row(), com.finscope.common.constant.ContentConstants.PROVIDER_EASTMONEY_ANNOUNCEMENT,
                time(since), time(until), after, Math.max(1, Math.min(250, size)));
    }

    /** 模型结果只能写回它实际读到的原文版本，且不能覆盖人工修订。 */
    public boolean applyAi(String id, String title, String content, com.finscope.domain.news.ai.NewsAiAnalysis analysis,
                           List<NewsCategory> categories, String version) {
        String name = categories.stream().filter(category -> category.getCode().equals(analysis.getCategoryCode()))
                .map(NewsCategory::getName).findFirst().orElse(null);
        if (name == null) {
            return false;
        }
        return jdbc.update("UPDATE news_report SET category_code=?,category_name=?,classification_reason=?,rule_version=? "
                        + "WHERE id=? AND title=? AND content=? AND manually_reviewed=0 AND (category_code IS NOT ? OR rule_version IS NOT ?)",
                analysis.getCategoryCode(), name, "AI统一理解：" + analysis.getEventType(), version, id, title, content, analysis.getCategoryCode(), version) == 1;
    }

    public Optional<NewsReport> find(String id) {
        return jdbc.query(SELECT + "WHERE n.id=?", row(), id).stream().findFirst();
    }

    public List<NewsReport> related(String id) {
        return jdbc.query(SELECT + "WHERE n.id<>? AND n.id IN (SELECT s.origin_key FROM investment_reaction_source s "
                + "WHERE s.origin_type='NEWS_ITEM' AND s.event_key IN (SELECT event_key FROM investment_reaction_source "
                + "WHERE origin_type='NEWS_ITEM' AND origin_key=?)) OR (n.id<>? AND a.event_key IN "
                + "(SELECT own.event_key FROM news_ai_report own JOIN news_report original ON original.id=own.report_id "
                + "AND original.content_version=own.content_version WHERE own.report_id=?)) "
                + "ORDER BY n.published_at,n.arrival_sequence LIMIT 100", row(), id, id, id, id);
    }

    public long latestSequence() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(arrival_sequence),0) FROM news_report", Long.class);
    }

    public long count(NewsWindowQuery query, LocalDateTime since, LocalDateTime until) {
        List<Object> args = new ArrayList<>();
        String where = where(query, since, until, args);
        String expression = query.isGroupEvents() ? "COUNT(DISTINCT " + GROUP_KEY + ")" : "COUNT(*)";
        return jdbc.queryForObject("SELECT " + expression + FROM + where, Long.class, args.toArray());
    }

    public NewsWindowPage query(NewsWindowQuery query, LocalDateTime since, LocalDateTime until) {
        List<Object> args = new ArrayList<>();
        String where = where(query, since, until, args);
        NewsWindowPage page = new NewsWindowPage();
        page.setTotal(count(query, since, until));
        args.add(query.getSize());
        args.add((long) query.getPage() * query.getSize());
        String sql = query.isGroupEvents() ? ranked(where) + "SELECT * FROM ranked WHERE event_rank=1 "
                + "ORDER BY COALESCE(published_at,first_seen_at) DESC,arrival_sequence DESC LIMIT ? OFFSET ?"
                : SELECT + where + " ORDER BY COALESCE(n.published_at,n.first_seen_at) DESC,n.arrival_sequence DESC LIMIT ? OFFSET ?";
        page.setItems(jdbc.query(sql, row(), args.toArray()));
        page.setAsOfSequence(query.getAsOfSequence());
        page.setPage(query.getPage());
        page.setSize(query.getSize());
        page.setSources(jdbc.queryForList("SELECT DISTINCT source_name FROM news_report WHERE COALESCE(published_at,first_seen_at)>=? "
                + "AND COALESCE(published_at,first_seen_at)<=? AND arrival_sequence<=? ORDER BY source_name", String.class,
                time(since), time(until), query.getAsOfSequence()));
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<Object> countArgs = new ArrayList<>();
        String categoryWhere = where(query, since, until, countArgs, false);
        String countSql = query.isGroupEvents() ? ranked(categoryWhere) + "SELECT COALESCE(category_code,'UNCLASSIFIED') category,COUNT(*) total FROM ranked WHERE event_rank=1 GROUP BY category_code"
                : "SELECT COALESCE(n.category_code,'UNCLASSIFIED') category,COUNT(*) total" + FROM + categoryWhere + " GROUP BY n.category_code";
        jdbc.query(countSql, rs -> {
            counts.put(rs.getString("category"), rs.getInt("total"));
        }, countArgs.toArray());
        counts.put("ALL", counts.values().stream().mapToInt(Integer::intValue).sum());
        page.setCategoryCounts(counts);
        return page;
    }

    /** 筛选与分页快照先于聚合，原文不丢失；优先展示非转载报道的最新进展。 */
    private String ranked(String where) {
        return "WITH ranked AS (SELECT n.*,COALESCE(r.read_version,0) read_version,a.event_key,"
                + "COUNT(*) OVER(PARTITION BY " + GROUP_KEY + ") merged_report_count,"
                + "ROW_NUMBER() OVER(PARTITION BY " + GROUP_KEY + " ORDER BY CASE WHEN a.relation='DUPLICATE' THEN 1 ELSE 0 END,"
                + "COALESCE(n.published_at,n.first_seen_at) DESC,n.arrival_sequence DESC) event_rank" + FROM + where + ") ";
    }

    /** 后台按到达序号遍历完整窗口，不复用页面的条数限制。 */
    public List<NewsReport> scan(LocalDateTime since, LocalDateTime until, long after, int size) {
        return jdbc.query(SELECT + "WHERE COALESCE(n.published_at,n.first_seen_at)>=? AND COALESCE(n.published_at,n.first_seen_at)<=? "
                        + "AND n.arrival_sequence>? ORDER BY n.arrival_sequence LIMIT ?", row(), time(since), time(until), after,
                Math.max(1, Math.min(size, 250)));
    }

    public List<NewsReportVersion> versions(String id) {
        return jdbc.query("SELECT * FROM news_report_version WHERE report_id=? ORDER BY version DESC LIMIT 100", versionRow(), id);
    }

    public Optional<NewsReportVersion> findVersion(String id, int version) {
        return jdbc.query("SELECT * FROM news_report_version WHERE report_id=? AND version=?", versionRow(), id, version)
                .stream().findFirst();
    }

    private RowMapper<NewsReportVersion> versionRow() {
        return (rs, index) -> {
            NewsReportVersion value = new NewsReportVersion();
            value.setReportId(rs.getString("report_id"));
            value.setVersion(rs.getInt("version"));
            value.setTitle(rs.getString("title"));
            value.setContent(rs.getString("content"));
            value.setDetectedAt(LocalDateTime.parse(rs.getString("detected_at")));
            return value;
        };
    }

    public boolean markRead(String id, int version) {
        return jdbc.update("INSERT INTO news_report_read(report_id,read_version) SELECT id,? FROM news_report "
                + "WHERE id=? AND content_version>=? AND ?>0 ON CONFLICT(report_id) DO UPDATE SET "
                + "read_version=MAX(news_report_read.read_version,excluded.read_version)", version, id, version, version) == 1;
    }

    public boolean review(String id, String category, String categoryName, String reason) {
        return jdbc.update("UPDATE news_report SET category_code=?,category_name=?,manually_reviewed=1,manual_reason=? WHERE id=?",
                category, categoryName, reason, id) == 1;
    }

    public List<NewsSavedFilter> filters() {
        return jdbc.query("SELECT * FROM news_saved_filter ORDER BY name,id LIMIT 50", (rs, index) -> {
            NewsSavedFilter value = new NewsSavedFilter();
            value.setId(rs.getString("id"));
            value.setName(rs.getString("name"));
            try {
                value.setQuery(mapper.readValue(rs.getString("query_json"), NewsWindowQuery.class));
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("订阅筛选条件读取失败", error);
            }
            return value;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveFilter(NewsSavedFilter value) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM news_saved_filter", Integer.class) >= 50) {
            throw new IllegalArgumentException("最多保存50个筛选");
        }
        try {
            if (jdbc.update("INSERT INTO news_saved_filter VALUES(?,?,?)", value.getId(), value.getName(),
                    mapper.writeValueAsString(value.getQuery())) != 1) {
                throw new IllegalStateException("筛选保存失败");
            }
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("筛选序列化失败", error);
        }
    }

    public boolean deleteFilter(String id) {
        return jdbc.update("DELETE FROM news_saved_filter WHERE id=?", id) == 1;
    }

    /** 过期正文仅在没有投资观察或大事记引用时清理；已登记依据长期保留。 */
    @Transactional(rollbackFor = Exception.class)
    public void prune(LocalDateTime before) {
        jdbc.update("DELETE FROM news_report WHERE COALESCE(published_at,first_seen_at)<? "
                + "AND NOT EXISTS(SELECT 1 FROM investment_reaction_source s WHERE s.origin_type='NEWS_ITEM' AND s.origin_key=news_report.id) "
                + "AND NOT EXISTS(SELECT 1 FROM major_event m WHERE m.origin_type='NEWS_ITEM' AND m.origin_key=news_report.id)", time(before));
        jdbc.update("DELETE FROM news_report_version WHERE NOT EXISTS(SELECT 1 FROM news_report n WHERE n.id=report_id)");
        jdbc.update("DELETE FROM news_report_read WHERE NOT EXISTS(SELECT 1 FROM news_report n WHERE n.id=report_id)");
        jdbc.update("DELETE FROM news_ai_report WHERE NOT EXISTS(SELECT 1 FROM news_report n WHERE n.id=report_id)");
        jdbc.update("DELETE FROM news_report_interpretation WHERE NOT EXISTS(SELECT 1 FROM news_report n WHERE n.id=report_id)");
    }

    private String where(NewsWindowQuery query, LocalDateTime since, LocalDateTime until, List<Object> args) {
        return where(query, since, until, args, true);
    }

    private String where(NewsWindowQuery query, LocalDateTime since, LocalDateTime until, List<Object> args, boolean includeCategory) {
        StringBuilder sql = new StringBuilder("WHERE COALESCE(n.published_at,n.first_seen_at)>=? AND COALESCE(n.published_at,n.first_seen_at)<=? AND n.arrival_sequence<=? ");
        args.add(time(since));
        args.add(time(until));
        args.add(query.getAsOfSequence());
        if (!query.getQuery().isBlank()) {
            sql.append("AND instr(lower(n.title || ' ' || n.content),lower(?))>0 ");
            args.add(query.getQuery());
        }
        if (!query.getExclude().isBlank()) {
            sql.append("AND instr(lower(n.title || ' ' || n.content),lower(?))=0 ");
            args.add(query.getExclude());
        }
        if (!"ALL".equals(query.getSource())) {
            sql.append("AND n.source_name=? ");
            args.add(query.getSource());
        }
        if (includeCategory && !"ALL".equals(query.getCategory())) {
            sql.append("AND COALESCE(n.category_code,'UNCLASSIFIED')=? ");
            args.add(query.getCategory());
        }
        if (!"ALL".equals(query.getKind())) {
            sql.append("AND n.kind=? ");
            args.add(query.getKind());
        }
        if (query.isUnreadOnly()) {
            sql.append("AND COALESCE(r.read_version,0)<n.content_version ");
        }
        return sql.toString();
    }

    private RowMapper<NewsReport> row() {
        return (rs, index) -> {
            NewsReport value = new NewsReport();
            value.setId(rs.getString("id"));
            value.setArrivalSequence(rs.getLong("arrival_sequence"));
            value.setProviderCode(rs.getString("provider_code"));
            value.setSourceName(rs.getString("source_name"));
            value.setSourceTier(rs.getString("source_tier"));
            value.setKind(rs.getString("kind"));
            value.setTitle(rs.getString("title"));
            value.setContent(rs.getString("content"));
            value.setUrl(rs.getString("url"));
            value.setPublishedAt(date(rs.getString("published_at")));
            value.setFirstSeenAt(date(rs.getString("first_seen_at")));
            value.setLastSeenAt(date(rs.getString("last_seen_at")));
            value.setContentVersion(rs.getInt("content_version"));
            value.setReadVersion(rs.getInt("read_version"));
            value.setCategoryCode(rs.getString("category_code"));
            value.setCategoryName(rs.getString("category_name"));
            value.setClassificationReason(rs.getString("classification_reason"));
            value.setRuleVersion(rs.getString("rule_version"));
            value.setManuallyReviewed(rs.getInt("manually_reviewed") == 1);
            value.setManualReason(rs.getString("manual_reason"));
            value.setEventKey(rs.getString("event_key"));
            value.setMergedReportCount(rs.getInt("merged_report_count"));
            return value;
        };
    }

    private String time(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private LocalDateTime date(String value) {
        return value == null ? null : LocalDateTime.parse(value);
    }
}
