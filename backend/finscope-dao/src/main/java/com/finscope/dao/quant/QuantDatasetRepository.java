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
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO quant_dataset("
                    + "name,market,universe_type,source_type,data_kind,start_date,end_date,status,fingerprint,quality_summary,revision,created_at,updated_at) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, value.getName()); ps.setString(2, value.getMarket());
            ps.setString(3, value.getUniverseType()); ps.setString(4, value.getSourceType());
            ps.setString(5, value.getDataKind()); ps.setString(6, text(value.getStartDate()));
            ps.setString(7, text(value.getEndDate())); ps.setString(8, value.getStatus());
            ps.setString(9, value.getFingerprint()); ps.setString(10, value.getQualitySummary());
            ps.setLong(11, 0); ps.setString(12, TimeUtil.text(now)); ps.setString(13, TimeUtil.text(now));
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

    private static String text(LocalDate value) { return value == null ? null : value.toString(); }
    private static LocalDate date(String value) { return value == null ? null : LocalDate.parse(value); }
}
