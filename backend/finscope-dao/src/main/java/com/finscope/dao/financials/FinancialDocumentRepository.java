package com.finscope.dao.financials;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.financials.FinancialDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class FinancialDocumentRepository {
    private final JdbcTemplate jdbc;

    public FinancialDocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<FinancialDocument> mapper = (rs, rowNum) -> {
        FinancialDocument value = new FinancialDocument();
        value.setId(rs.getLong("id"));
        value.setInstrumentId(rs.getLong("instrument_id"));
        Object reportId = rs.getObject("report_id");
        value.setReportId(reportId == null ? null : rs.getLong("report_id"));
        value.setOriginalFileName(rs.getString("original_file_name"));
        value.setRelativePath(rs.getString("relative_path"));
        value.setMimeType(rs.getString("mime_type"));
        value.setFileSize(rs.getLong("file_size"));
        value.setFileHash(rs.getString("file_hash"));
        Object pageCount = rs.getObject("page_count");
        value.setPageCount(pageCount == null ? null : rs.getInt("page_count"));
        value.setParseStatus(rs.getString("parse_status"));
        value.setExtractedText(rs.getString("extracted_text"));
        value.setErrorMessage(rs.getString("error_message"));
        value.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        return value;
    };

    public Optional<FinancialDocument> findByHash(String hash) {
        List<FinancialDocument> values = jdbc.query(
                "SELECT * FROM financial_document WHERE file_hash=?", mapper, hash);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public Optional<FinancialDocument> findById(Long id) {
        List<FinancialDocument> values = jdbc.query(
                "SELECT * FROM financial_document WHERE id=?", mapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<FinancialDocument> findByReport(Long reportId) {
        return jdbc.query("SELECT * FROM financial_document WHERE report_id=? " +
                "ORDER BY created_at DESC,id DESC", mapper, reportId);
    }

    public FinancialDocument save(FinancialDocument document) {
        document.setCreatedAt(LocalDateTime.now());
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO financial_document(instrument_id,report_id,original_file_name," +
                            "relative_path,mime_type,file_size,file_hash,page_count,parse_status," +
                            "extracted_text,error_message,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, document.getInstrumentId());
            if (document.getReportId() == null) {
                statement.setObject(2, null);
            } else {
                statement.setLong(2, document.getReportId());
            }
            statement.setString(3, document.getOriginalFileName());
            statement.setString(4, document.getRelativePath());
            statement.setString(5, document.getMimeType());
            statement.setLong(6, document.getFileSize());
            statement.setString(7, document.getFileHash());
            statement.setObject(8, document.getPageCount());
            statement.setString(9, document.getParseStatus());
            statement.setString(10, document.getExtractedText());
            statement.setString(11, document.getErrorMessage());
            statement.setString(12, TimeUtil.text(document.getCreatedAt()));
            return statement;
        }, keys);
        if (keys.getKey() != null) {
            document.setId(keys.getKey().longValue());
        }
        return document;
    }
}
