package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarRankPoint;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@DependsOn("databaseInitializer")
public class RadarRankHistoryRepository implements InitializingBean {
    @Resource
    private JdbcTemplate jdbc;

    @Override
    public void afterPropertiesSet() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS radar_rank_history (event_key TEXT NOT NULL,observed_at TEXT NOT NULL,"
                + "rank_position INTEGER NOT NULL,hotspot_score INTEGER NOT NULL,report_count INTEGER NOT NULL,"
                + "source_count INTEGER NOT NULL,PRIMARY KEY(event_key,observed_at))");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_radar_rank_time ON radar_rank_history(observed_at)");
    }

    /** 每个半小时保留该桶内最近一次成功生产的排名。 */
    @Transactional(rollbackFor = Exception.class)
    public void save(List<RadarRankPoint> points) {
        if (points.size() > 250) {
            throw new IllegalArgumentException("排名写入批次不能超过250条");
        }
        for (RadarRankPoint point : points) {
            if (jdbc.update("INSERT INTO radar_rank_history VALUES(?,?,?,?,?,?) ON CONFLICT(event_key,observed_at) "
                    + "DO UPDATE SET rank_position=excluded.rank_position,hotspot_score=excluded.hotspot_score,"
                    + "report_count=excluded.report_count,source_count=excluded.source_count", point.getEventKey(),
                    point.getObservedAt().toString(), point.getRankPosition(), point.getHotspotScore(),
                    point.getReportCount(), point.getSourceCount()) != 1) {
                throw new IllegalStateException("雷达排名历史保存失败");
            }
        }
    }

    public List<RadarRankPoint> history(String eventKey, LocalDateTime since) {
        return jdbc.query("SELECT * FROM radar_rank_history WHERE event_key=? AND observed_at>=? "
                + "ORDER BY observed_at DESC LIMIT 336", (rs, index) -> {
                    RadarRankPoint point = new RadarRankPoint();
                    point.setEventKey(rs.getString("event_key"));
                    point.setObservedAt(LocalDateTime.parse(rs.getString("observed_at")));
                    point.setRankPosition(rs.getInt("rank_position"));
                    point.setHotspotScore(rs.getInt("hotspot_score"));
                    point.setReportCount(rs.getInt("report_count"));
                    point.setSourceCount(rs.getInt("source_count"));
                    return point;
                }, eventKey, since.toString());
    }

    public void prune(LocalDateTime before) {
        jdbc.update("DELETE FROM radar_rank_history WHERE observed_at<?", before.toString());
    }
}
