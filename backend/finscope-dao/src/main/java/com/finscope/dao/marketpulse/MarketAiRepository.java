package com.finscope.dao.marketpulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.marketpulse.MarketAiStatus;
import com.finscope.domain.marketpulse.ai.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@DependsOn("databaseInitializer")
public class MarketAiRepository implements InitializingBean {
    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private ObjectMapper mapper;

    @Override
    public void afterPropertiesSet() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS market_ai_review(cache_key TEXT PRIMARY KEY,business_date TEXT NOT NULL,"
                + "status TEXT NOT NULL,owner TEXT NOT NULL,sent INTEGER NOT NULL DEFAULT 0,payload TEXT NOT NULL,updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_market_ai_date ON market_ai_review(business_date,updated_at)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS market_ai_attempt(owner TEXT NOT NULL,cache_key TEXT NOT NULL,model TEXT,effort TEXT,"
                + "status TEXT NOT NULL,input_tokens INTEGER,output_tokens INTEGER,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,PRIMARY KEY(cache_key,owner))");
    }

    public Optional<MarketAiView> find(String key) {
        return jdbc.query("SELECT payload FROM market_ai_review WHERE cache_key=?", (rs, n) -> read(rs.getString(1)), key).stream().findFirst();
    }

    /** 仅选择本交易日前已生成的相邻日情景，避免事后生成的情景参与验证。 */
    public Optional<MarketAiView> findReviewSource(String date, LocalDateTime before) {
        return jdbc.query("SELECT payload FROM market_ai_review WHERE business_date=? AND status=? ORDER BY updated_at DESC,cache_key DESC LIMIT 20",
                (rs, n) -> read(rs.getString(1)), date, MarketAiStatus.READY.name()).stream()
                .filter(v -> v.getGeneratedAt() != null && v.getGeneratedAt().isBefore(before) && v.getReport() != null && !v.getReport().getScenarios().isEmpty())
                .max(java.util.Comparator.comparing(MarketAiView::getGeneratedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean claim(String key, String owner, MarketAiView view, LocalDateTime now) {
        int changed = jdbc.update("INSERT OR IGNORE INTO market_ai_review VALUES(?,?,?,?,0,?,?)", key,
                view.getBusinessDate().toString(), MarketAiStatus.QUEUED.name(), owner, json(view), now.toString());
        if (changed != 1) {
            return false;
        }
        jdbc.update("INSERT INTO market_ai_attempt(owner,cache_key,model,effort,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
                owner, key, view.getModel(), view.getEffort(), MarketAiStatus.QUEUED.name(), now.toString(), now.toString());
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markSent(String key, String owner, MarketAiView view, LocalDateTime now) {
        int changed = jdbc.update("UPDATE market_ai_review SET status=?,sent=1,payload=?,updated_at=? WHERE cache_key=? AND owner=? AND status=?",
                MarketAiStatus.RUNNING.name(), json(view), now.toString(), key, owner, MarketAiStatus.QUEUED.name());
        if (changed == 1) {
            jdbc.update("UPDATE market_ai_attempt SET status=?,updated_at=? WHERE owner=? AND cache_key=?", MarketAiStatus.RUNNING.name(), now.toString(), owner, key);
        }
        return changed == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public void finish(String key, String owner, MarketAiView view, LocalDateTime now) {
        if (jdbc.update("UPDATE market_ai_review SET status=?,payload=?,updated_at=? WHERE cache_key=? AND owner=?",
                view.getStatus().name(), json(view), now.toString(), key, owner) != 1) {
            throw new IllegalStateException("市场研判保存状态冲突");
        }
        jdbc.update("UPDATE market_ai_attempt SET status=?,input_tokens=?,output_tokens=?,updated_at=? WHERE owner=? AND cache_key=?",
                view.getStatus().name(), view.getInputTokens(), view.getOutputTokens(), now.toString(), owner, key);
    }

    public void retryFailed(String key) {
        jdbc.update("DELETE FROM market_ai_review WHERE cache_key=? AND status=?", key, MarketAiStatus.FAILED.name());
    }

    /** 重启后未发出的任务可以删除重领；已发送结果未知的请求禁止自动重发。 */
    @Transactional(rollbackFor = Exception.class)
    public void recover(LocalDateTime before) {
        jdbc.update("DELETE FROM market_ai_review WHERE updated_at<? AND status=? AND sent=0", before.toString(), MarketAiStatus.QUEUED.name());
        // 已发出的请求保持可观测的UNKNOWN状态，不隐藏已发生的费用风险。
        var pending = jdbc.queryForList("SELECT cache_key,owner,payload FROM market_ai_review WHERE updated_at<? AND status=?",
                before.toString(), MarketAiStatus.RUNNING.name());
        for (var row : pending) {
            MarketAiView view = read((String) row.get("payload"));
            view.setStatus(MarketAiStatus.UNKNOWN);
            view.setMessage("上次研判请求未确认完成，当前保留规则速读");
            int changed = jdbc.update("UPDATE market_ai_review SET status=?,payload=?,updated_at=? WHERE cache_key=? AND owner=? AND status=? AND updated_at<?",
                    MarketAiStatus.UNKNOWN.name(), json(view), before.toString(), row.get("cache_key"), row.get("owner"),
                    MarketAiStatus.RUNNING.name(), before.toString());
            if (changed == 1) {
                jdbc.update("UPDATE market_ai_attempt SET status=?,updated_at=? WHERE owner=? AND cache_key=?", MarketAiStatus.UNKNOWN.name(), before.toString(), row.get("owner"), row.get("cache_key"));
            }
        }
    }

    private String json(MarketAiView view) {
        try {
            return mapper.writeValueAsString(view);
        } catch (Exception error) {
            throw new IllegalStateException("市场研判序列化失败", error);
        }
    }
    private MarketAiView read(String text) {
        try {
            return mapper.readValue(text, MarketAiView.class);
        } catch (Exception error) {
            throw new IllegalStateException("市场研判读取失败", error);
        }
    }
}
