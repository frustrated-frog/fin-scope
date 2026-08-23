package com.finscope.dao.marketpulse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.util.TimeUtil;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.SectorRotationItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class MarketPulseRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void saveWorkspace(MarketPulseWorkspace workspace) {
        String date = workspace.getBusinessDate().toString();
        jdbcTemplate.update("INSERT INTO market_regime_snapshot(business_date,confidence_score,quality_status,"
                        + "source_fingerprint,snapshot_json,calculated_at) VALUES(?,?,?,?,?,?) "
                        + "ON CONFLICT(business_date) DO UPDATE SET confidence_score=excluded.confidence_score,"
                        + "quality_status=excluded.quality_status,source_fingerprint=excluded.source_fingerprint,"
                        + "snapshot_json=excluded.snapshot_json,calculated_at=excluded.calculated_at",
                date, workspace.getRegime().getConfidenceScore(), workspace.getRegime().getQualityStatus().name(),
                workspace.getRegime().getSourceFingerprint(), json(workspace.getRegime()),
                TimeUtil.text(workspace.getRegime().getCalculatedAt()));
        if (workspace.getBreadth() != null) {
            jdbcTemplate.update("INSERT INTO market_breadth_snapshot(business_date,quality_status,source_code,"
                            + "snapshot_json,retrieved_at) VALUES(?,?,?,?,?) ON CONFLICT(business_date) DO UPDATE SET "
                            + "quality_status=excluded.quality_status,source_code=excluded.source_code,"
                            + "snapshot_json=excluded.snapshot_json,retrieved_at=excluded.retrieved_at",
                    date, workspace.getBreadth().getQualityStatus(), workspace.getBreadth().getSourceCode(),
                    json(workspace.getBreadth()), TimeUtil.text(workspace.getBreadth().getRetrievedAt()));
        }
        jdbcTemplate.update("INSERT INTO sector_rotation_snapshot(business_date,quality_status,source_fingerprint,"
                        + "calculated_at) VALUES(?,?,?,?) ON CONFLICT(business_date) DO UPDATE SET "
                        + "quality_status=excluded.quality_status,source_fingerprint=excluded.source_fingerprint,"
                        + "calculated_at=excluded.calculated_at", date, workspace.getQualityStatus().name(),
                workspace.getRegime().getSourceFingerprint(), TimeUtil.text(workspace.getGeneratedAt()));
        Long sectorSnapshotId = jdbcTemplate.queryForObject(
                "SELECT id FROM sector_rotation_snapshot WHERE business_date=?", Long.class, date);
        jdbcTemplate.update("DELETE FROM sector_rotation_item WHERE snapshot_id=?", sectorSnapshotId);
        for (SectorRotationItem item : workspace.getSectors()) {
            jdbcTemplate.update("INSERT INTO sector_rotation_item(snapshot_id,sector_code,sector_name,rotation_score,"
                            + "stage,item_json) VALUES(?,?,?,?,?,?)", sectorSnapshotId, item.getSectorCode(),
                    item.getSectorName(), item.getRotationScore(), item.getStage().name(), json(item));
        }
        jdbcTemplate.update("INSERT INTO market_opportunity_run(business_date,status,quality_status,workspace_json,"
                        + "generated_at) VALUES(?,'SUCCEEDED',?,?,?) ON CONFLICT(business_date) DO UPDATE SET "
                        + "status='SUCCEEDED',quality_status=excluded.quality_status,workspace_json=excluded.workspace_json,"
                        + "generated_at=excluded.generated_at", date, workspace.getQualityStatus().name(), json(workspace),
                TimeUtil.text(workspace.getGeneratedAt()));
    }

    public Optional<MarketPulseWorkspace> findWorkspace(LocalDate businessDate) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT workspace_json FROM market_opportunity_run WHERE business_date=?",
                String.class, businessDate.toString());
        return values.isEmpty() ? Optional.empty() : Optional.of(workspace(values.get(0)));
    }

    public Optional<MarketPulseWorkspace> findLatestWorkspace() {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT workspace_json FROM market_opportunity_run ORDER BY business_date DESC,id DESC LIMIT 1",
                String.class);
        return values.isEmpty() ? Optional.empty() : Optional.of(workspace(values.get(0)));
    }

    public Optional<MarketPulseWorkspace> findLatestWorkspace(LocalDate maximumBusinessDate) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT workspace_json FROM market_opportunity_run WHERE business_date<=? "
                        + "ORDER BY business_date DESC,id DESC LIMIT 1",
                String.class, maximumBusinessDate.toString());
        return values.isEmpty() ? Optional.empty() : Optional.of(workspace(values.get(0)));
    }

    public List<LocalDate> findRecentDates(int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("SELECT business_date FROM market_opportunity_run "
                        + "ORDER BY business_date DESC,id DESC LIMIT ?",
                (rs, rowNum) -> LocalDate.parse(rs.getString("business_date")), bounded);
    }

    public List<LocalDate> findRecentDates(int limit, LocalDate maximumBusinessDate) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("SELECT business_date FROM market_opportunity_run WHERE business_date<=? "
                        + "ORDER BY business_date DESC,id DESC LIMIT ?",
                (rs, rowNum) -> LocalDate.parse(rs.getString("business_date")),
                maximumBusinessDate.toString(), bounded);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("市场机会快照序列化失败", error);
        }
    }

    private MarketPulseWorkspace workspace(String value) {
        try {
            return objectMapper.readValue(value, MarketPulseWorkspace.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("市场机会快照解析失败", error);
        }
    }
}
