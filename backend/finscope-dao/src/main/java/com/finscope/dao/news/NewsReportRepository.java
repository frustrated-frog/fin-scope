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
    private static final String SELECT = "SELECT n.*,COALESCE(r.read_version,0) read_version FROM news_report n "
            + "LEFT JOIN news_report_read r ON r.report_id=n.id ";
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private ObjectMapper mapper;

    /** 一个批次的正文与版本原子保存；重复抓取不增加内容版本。 */
    @Transactional(rollbackFor = Exception.class)
    public void ingest(List<NewsReport> reports) {
        if (reports.size() > 250) {
            throw new IllegalArgumentException("新闻保存批次不能超过250条");
        }
        for (NewsReport report : reports) {
            int changed = jdbc.update("INSERT INTO news_report(id,provider_code,source_name,source_tier,kind,title,content,url,"
                            + "published_at,first_seen_at,last_seen_at,category_code,category_name,classification_reason,rule_version) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                            + "content_version=news_report.content_version+CASE WHEN news_report.title<>excluded.title OR "
                            + "news_report.content<>excluded.content THEN 1 ELSE 0 END,title=excluded.title,content=excluded.content,"
                            + "url=excluded.url,published_at=COALESCE(news_report.published_at,excluded.published_at),last_seen_at=excluded.last_seen_at,"
                            + "category_code=CASE WHEN news_report.manually_reviewed=1 THEN news_report.category_code ELSE excluded.category_code END,"
                            + "category_name=CASE WHEN news_report.manually_reviewed=1 THEN news_report.category_name ELSE excluded.category_name END,"
                            + "classification_reason=excluded.classification_reason,rule_version=excluded.rule_version",
                    report.getId(), report.getProviderCode(), report.getSourceName(), report.getSourceTier(), report.getKind(),
                    report.getTitle(), report.getContent(), report.getUrl(), time(report.getPublishedAt()), time(report.getFirstSeenAt()),
                    time(report.getLastSeenAt()), report.getCategoryCode(), report.getCategoryName(), report.getClassificationReason(), report.getRuleVersion());
            if (changed != 1) {
                throw new IllegalStateException("新闻保存影响行数异常");
            }
            jdbc.update("INSERT INTO news_report_version(report_id,version,title,content,detected_at) "
                    + "SELECT id,content_version,title,content,last_seen_at FROM news_report WHERE id=? "
                    + "ON CONFLICT(report_id,version) DO NOTHING", report.getId());
        }
    }

    public Optional<NewsReport> find(String id) {
        return jdbc.query(SELECT + "WHERE n.id=?", row(), id).stream().findFirst();
    }

    public List<NewsReport> related(String id) {
        return jdbc.query(SELECT + "WHERE n.id<>? AND n.id IN (SELECT s.origin_key FROM investment_reaction_source s "
                + "WHERE s.origin_type='NEWS_ITEM' AND s.event_key IN (SELECT event_key FROM investment_reaction_source "
                + "WHERE origin_type='NEWS_ITEM' AND origin_key=?)) ORDER BY n.published_at,n.arrival_sequence LIMIT 100",
                row(), id, id);
    }

    public long latestSequence() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(arrival_sequence),0) FROM news_report", Long.class);
    }

    public long count(NewsWindowQuery query, LocalDateTime since, LocalDateTime until) {
        List<Object> args = new ArrayList<>();
        String where = where(query, since, until, args);
        return jdbc.queryForObject("SELECT COUNT(*) FROM news_report n LEFT JOIN news_report_read r ON r.report_id=n.id " + where,
                Long.class, args.toArray());
    }

    public NewsWindowPage query(NewsWindowQuery query, LocalDateTime since, LocalDateTime until) {
        List<Object> args = new ArrayList<>();
        String where = where(query, since, until, args);
        NewsWindowPage page = new NewsWindowPage();
        page.setTotal(jdbc.queryForObject("SELECT COUNT(*) FROM news_report n LEFT JOIN news_report_read r ON r.report_id=n.id " + where,
                Long.class, args.toArray()));
        args.add(query.getSize());
        args.add((long) query.getPage() * query.getSize());
        page.setItems(jdbc.query(SELECT + where + " ORDER BY COALESCE(n.published_at,n.first_seen_at) DESC,n.arrival_sequence DESC LIMIT ? OFFSET ?",
                row(), args.toArray()));
        page.setAsOfSequence(query.getAsOfSequence());
        page.setPage(query.getPage());
        page.setSize(query.getSize());
        page.setSources(jdbc.queryForList("SELECT DISTINCT source_name FROM news_report WHERE COALESCE(published_at,first_seen_at)>=? "
                + "AND COALESCE(published_at,first_seen_at)<=? ORDER BY source_name", String.class, time(since), time(until)));
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbc.query("SELECT COALESCE(category_code,'UNCLASSIFIED') category,COUNT(*) total FROM news_report "
                + "WHERE COALESCE(published_at,first_seen_at)>=? AND COALESCE(published_at,first_seen_at)<=? "
                + "GROUP BY category_code", rs -> {
                    counts.put(rs.getString("category"), rs.getInt("total"));
                }, time(since), time(until));
        counts.put("ALL", counts.values().stream().mapToInt(Integer::intValue).sum());
        page.setCategoryCounts(counts);
        return page;
    }

    /** 后台按到达序号遍历完整窗口，不复用页面的条数限制。 */
    public List<NewsReport> scan(LocalDateTime since, LocalDateTime until, long after, int size) {
        return jdbc.query(SELECT + "WHERE COALESCE(n.published_at,n.first_seen_at)>=? AND COALESCE(n.published_at,n.first_seen_at)<=? "
                        + "AND n.arrival_sequence>? ORDER BY n.arrival_sequence LIMIT ?", row(), time(since), time(until), after,
                Math.max(1, Math.min(size, 250)));
    }

    public List<NewsReportVersion> versions(String id) {
        return jdbc.query("SELECT * FROM news_report_version WHERE report_id=? ORDER BY version DESC LIMIT 100", (rs, index) -> {
            NewsReportVersion value = new NewsReportVersion();
            value.setReportId(rs.getString("report_id"));
            value.setVersion(rs.getInt("version"));
            value.setTitle(rs.getString("title"));
            value.setContent(rs.getString("content"));
            value.setDetectedAt(LocalDateTime.parse(rs.getString("detected_at")));
            return value;
        }, id);
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
    }

    private String where(NewsWindowQuery query, LocalDateTime since, LocalDateTime until, List<Object> args) {
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
        if (!"ALL".equals(query.getCategory())) {
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
