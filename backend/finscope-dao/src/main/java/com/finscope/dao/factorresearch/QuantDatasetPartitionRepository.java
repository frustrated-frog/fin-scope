package com.finscope.dao.factorresearch;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.quant.data.QuantDatasetPartition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

@Repository
public class QuantDatasetPartitionRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<QuantDatasetPartition> mapper = (rs, rowNum) -> {
        QuantDatasetPartition value = new QuantDatasetPartition();
        value.setDatasetId(rs.getLong("dataset_id"));
        value.setPartitionType(rs.getString("partition_type"));
        value.setRowCount(rs.getLong("row_count"));
        value.setMinDate(TimeUtil.localDate(rs, "min_date"));
        value.setMaxDate(TimeUtil.localDate(rs, "max_date"));
        value.setPartitionFingerprint(rs.getString("partition_fingerprint"));
        value.setQualityStatus(rs.getString("quality_status"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return value;
    };

    public void save(QuantDatasetPartition value) {
        value.validate();
        jdbcTemplate.update("INSERT INTO quant_dataset_partition("
                        + "dataset_id,partition_type,row_count,min_date,max_date,partition_fingerprint,"
                        + "quality_status,created_at) VALUES(?,?,?,?,?,?,?,?)",
                value.getDatasetId(), value.getPartitionType(), value.getRowCount(),
                TimeUtil.text(value.getMinDate()), TimeUtil.text(value.getMaxDate()),
                value.getPartitionFingerprint(), value.getQualityStatus(), TimeUtil.text(value.getCreatedAt()));
    }

    public List<QuantDatasetPartition> findByDatasetId(Long datasetId) {
        return jdbcTemplate.query("SELECT * FROM quant_dataset_partition WHERE dataset_id=? "
                + "ORDER BY partition_type", mapper, datasetId);
    }
}
