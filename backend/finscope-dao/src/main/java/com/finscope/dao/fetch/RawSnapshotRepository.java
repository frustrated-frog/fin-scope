package com.finscope.dao.fetch;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.fetch.RawSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

@Repository
public class RawSnapshotRepository {
    private final JdbcTemplate jdbcTemplate;

    public RawSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<RawSnapshot> mapper = (rs, rowNum) -> {
        RawSnapshot value = new RawSnapshot();
        value.setId(rs.getLong("id"));
        value.setFetchRunId(nullableLong(rs, "fetch_run_id"));
        value.setSourceId(nullableLong(rs, "source_id"));
        value.setPurpose(rs.getString("purpose"));
        value.setMethod(rs.getString("method"));
        value.setRequestUrl(rs.getString("request_url"));
        value.setFinalUrl(rs.getString("final_url"));
        value.setRequestHeadersJson(rs.getString("request_headers_json"));
        value.setStatus(rs.getString("status"));
        value.setHttpStatus(nullableInteger(rs, "http_status"));
        value.setErrorType(rs.getString("error_type"));
        value.setErrorMessage(rs.getString("error_message"));
        value.setContentType(rs.getString("content_type"));
        value.setCharsetName(rs.getString("charset_name"));
        value.setBodyBytes(rs.getInt("body_bytes"));
        value.setBodySha256(rs.getString("body_sha256"));
        value.setBodyPath(rs.getString("body_path"));
        value.setAttemptCount(rs.getInt("attempt_count"));
        value.setDurationMs(rs.getLong("duration_ms"));
        value.setPolicyVersion(rs.getString("policy_version"));
        value.setParserVersion(rs.getString("parser_version"));
        value.setFetchedAt(TimeUtil.localDateTime(rs, "fetched_at"));
        value.setParsedAt(TimeUtil.localDateTime(rs, "parsed_at"));
        return value;
    };

    public RawSnapshot save(RawSnapshot value) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO raw_snapshot(fetch_run_id,source_id,purpose,method,request_url,final_url,"
                            + "request_headers_json,status,http_status,error_type,error_message,content_type,"
                            + "charset_name,body_bytes,body_sha256,body_path,attempt_count,duration_ms,"
                            + "policy_version,parser_version,fetched_at,parsed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, value.getFetchRunId());
            statement.setObject(2, value.getSourceId());
            statement.setString(3, value.getPurpose());
            statement.setString(4, value.getMethod());
            statement.setString(5, value.getRequestUrl());
            statement.setString(6, value.getFinalUrl());
            statement.setString(7, value.getRequestHeadersJson());
            statement.setString(8, value.getStatus());
            statement.setObject(9, value.getHttpStatus());
            statement.setString(10, value.getErrorType());
            statement.setString(11, value.getErrorMessage());
            statement.setString(12, value.getContentType());
            statement.setString(13, value.getCharsetName());
            statement.setInt(14, value.getBodyBytes());
            statement.setString(15, value.getBodySha256());
            statement.setString(16, value.getBodyPath());
            statement.setInt(17, value.getAttemptCount());
            statement.setLong(18, value.getDurationMs());
            statement.setString(19, value.getPolicyVersion());
            statement.setString(20, value.getParserVersion());
            statement.setString(21, TimeUtil.text(value.getFetchedAt()));
            statement.setString(22, TimeUtil.text(value.getParsedAt()));
            return statement;
        }, keys);
        value.setId(keys.getKey().longValue());
        return value;
    }

    public Optional<RawSnapshot> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM raw_snapshot WHERE id=?", mapper, id).stream().findFirst();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
