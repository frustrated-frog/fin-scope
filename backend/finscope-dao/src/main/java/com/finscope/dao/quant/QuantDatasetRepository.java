package com.finscope.dao.quant;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.quant.data.QuantDataset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class QuantDatasetRepository {
    @Resource private JdbcTemplate jdbcTemplate;

    private final RowMapper<QuantDataset> mapper = (rs, rowNum) -> {
        QuantDataset value = new QuantDataset();
        value.setId(rs.getLong("id"));
        value.setName(rs.getString("name"));
        value.setMarket(rs.getString("market"));
        value.setUniverseType(rs.getString("universe_type"));
        value.setSourceType(rs.getString("source_type"));
        value.setDataKind(rs.getString("data_kind"));
        value.setDatasetLevel(rs.getString("dataset_level"));
        value.setAsOfTime(TimeUtil.localDateTime(rs, "as_of_time"));
        value.setFingerprintVersion(rs.getString("fingerprint_version"));
        value.setPartitionManifest(rs.getString("partition_manifest"));
        value.setStartDate(date(rs.getString("start_date")));
        value.setEndDate(date(rs.getString("end_date")));
        value.setStatus(rs.getString("status"));
        value.setFingerprint(rs.getString("fingerprint"));
        value.setQualitySummary(rs.getString("quality_summary"));
        value.setRevision(rs.getLong("revision"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        value.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return value;
    };

    public QuantDataset save(QuantDataset value) {
        LocalDateTime now = LocalDateTime.now();
        String datasetLevel = defaultDatasetLevel(value.getDatasetLevel(), value.getDataKind());
        String fingerprintVersion = defaultText(value.getFingerprintVersion(), "quant-dataset-v1");
        String partitionManifest = defaultText(value.getPartitionManifest(), "[]");
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO quant_dataset("
                    + "name,market,universe_type,source_type,data_kind,dataset_level,as_of_time,fingerprint_version,"
                    + "partition_manifest,start_date,end_date,status,fingerprint,quality_summary,revision,created_at,updated_at) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, value.getName()); ps.setString(2, value.getMarket());
            ps.setString(3, value.getUniverseType()); ps.setString(4, value.getSourceType());
            ps.setString(5, value.getDataKind()); ps.setString(6, datasetLevel);
            ps.setString(7, TimeUtil.text(value.getAsOfTime())); ps.setString(8, fingerprintVersion);
            ps.setString(9, partitionManifest); ps.setString(10, text(value.getStartDate()));
            ps.setString(11, text(value.getEndDate())); ps.setString(12, value.getStatus());
            ps.setString(13, value.getFingerprint()); ps.setString(14, value.getQualitySummary());
            ps.setLong(15, 0); ps.setString(16, TimeUtil.text(now)); ps.setString(17, TimeUtil.text(now));
            return ps;
        }, keys);
        value.setId(keys.getKey().longValue());
        return findById(value.getId()).orElse(value);
    }

    public Optional<QuantDataset> findById(Long id) {
        List<QuantDataset> values = jdbcTemplate.query("SELECT * FROM quant_dataset WHERE id=?", mapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<QuantDataset> findAll() {
        return jdbcTemplate.query("SELECT * FROM quant_dataset ORDER BY id DESC", mapper);
    }

    public boolean updateSummary(Long id, LocalDate start, LocalDate end, String status,
                                 String fingerprint, String qualitySummary, long revision) {
        return jdbcTemplate.update("UPDATE quant_dataset SET start_date=?,end_date=?,status=?,fingerprint=?,"
                        + "quality_summary=?,revision=revision+1,updated_at=? WHERE id=? AND revision=?",
                text(start), text(end), status, fingerprint, qualitySummary, TimeUtil.text(LocalDateTime.now()), id, revision) == 1;
    }

    public boolean updateResearchState(Long id, LocalDate start, LocalDate end, String status,
                                       LocalDateTime asOfTime, String fingerprintVersion,
                                       String partitionManifest, String fingerprint,
                                       String qualitySummary, long revision) {
        return jdbcTemplate.update("UPDATE quant_dataset SET start_date=?,end_date=?,status=?,as_of_time=?,"
                        + "fingerprint_version=?,partition_manifest=?,fingerprint=?,quality_summary=?,"
                        + "revision=revision+1,updated_at=? WHERE id=? AND revision=?",
                text(start), text(end), status, TimeUtil.text(asOfTime), fingerprintVersion,
                partitionManifest, fingerprint, qualitySummary, TimeUtil.text(LocalDateTime.now()),
                id, revision) == 1;
    }

    private static String text(LocalDate value) { return value == null ? null : value.toString(); }
    private static LocalDate date(String value) { return value == null ? null : LocalDate.parse(value); }
    private static String defaultDatasetLevel(String datasetLevel, String dataKind) {
        if (datasetLevel != null && !datasetLevel.trim().isEmpty()) return datasetLevel;
        return "REAL".equals(dataKind) ? "RESEARCH" : "LEARNING";
    }
    private static String defaultText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
