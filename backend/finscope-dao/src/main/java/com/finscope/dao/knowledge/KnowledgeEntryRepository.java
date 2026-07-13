package com.finscope.dao.knowledge;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.knowledge.KnowledgeEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeEntryRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<KnowledgeEntry> mapper = (resultSet, rowNumber) -> {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(resultSet.getLong("id"));
        entry.setTopicId(resultSet.getLong("topic_id"));
        entry.setLearningTaskId(nullableLong(resultSet, "learning_task_id"));
        entry.setEntryType(resultSet.getString("entry_type"));
        entry.setEntryStatus(resultSet.getString("entry_status"));
        entry.setQuestionSnapshot(resultSet.getString("question_snapshot"));
        entry.setContentMarkdown(resultSet.getString("content_markdown"));
        entry.setConfidence(resultSet.getString("confidence"));
        entry.setRevision(resultSet.getLong("revision"));
        entry.setCreatedAt(TimeUtil.localDateTime(resultSet, "created_at"));
        entry.setUpdatedAt(TimeUtil.localDateTime(resultSet, "updated_at"));
        return entry;
    };

    public KnowledgeEntryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public KnowledgeEntry saveDraft(KnowledgeEntry entry) {
        LocalDateTime now = LocalDateTime.now();
        entry.setEntryStatus("DRAFT");
        entry.setRevision(0L);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO knowledge_entry(topic_id,learning_task_id,entry_type,entry_status," +
                            "question_snapshot,content_markdown,confidence,revision,created_at,updated_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, entry.getTopicId());
            if (entry.getLearningTaskId() == null) {
                statement.setObject(2, null);
            } else {
                statement.setLong(2, entry.getLearningTaskId());
            }
            statement.setString(3, entry.getEntryType());
            statement.setString(4, entry.getEntryStatus());
            statement.setString(5, entry.getQuestionSnapshot());
            statement.setString(6, entry.getContentMarkdown());
            statement.setString(7, entry.getConfidence());
            statement.setLong(8, entry.getRevision());
            statement.setString(9, TimeUtil.text(now));
            statement.setString(10, TimeUtil.text(now));
            return statement;
        }, keys);
        if (keys.getKey() != null) {
            entry.setId(keys.getKey().longValue());
        }
        return findById(entry.getId()).orElse(entry);
    }

    public Optional<KnowledgeEntry> findById(Long id) {
        List<KnowledgeEntry> values = jdbcTemplate.query(
                "SELECT * FROM knowledge_entry WHERE id=?", mapper, id);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public Optional<KnowledgeEntry> findDraftByTaskId(Long taskId) {
        List<KnowledgeEntry> values = jdbcTemplate.query(
                "SELECT * FROM knowledge_entry WHERE learning_task_id=? " +
                        "AND entry_type='ANSWER' AND entry_status='DRAFT' ORDER BY id DESC LIMIT 1",
                mapper,
                taskId
        );
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public boolean updateDraft(Long id, String markdown, String confidence, long expectedRevision) {
        return jdbcTemplate.update(
                "UPDATE knowledge_entry SET content_markdown=?,confidence=?,revision=revision+1,updated_at=? " +
                        "WHERE id=? AND entry_status='DRAFT' AND revision=?",
                markdown, confidence, TimeUtil.text(LocalDateTime.now()), id, expectedRevision
        ) == 1;
    }

    public boolean finalizeDraft(Long id, long expectedRevision) {
        return jdbcTemplate.update(
                "UPDATE knowledge_entry SET entry_status='FINAL',revision=revision+1,updated_at=? " +
                        "WHERE id=? AND entry_status='DRAFT' AND revision=?",
                TimeUtil.text(LocalDateTime.now()), id, expectedRevision
        ) == 1;
    }

    public List<KnowledgeEntry> findFinalByTopicId(Long topicId, int limit, int offset) {
        if (limit < 1 || limit > 200 || offset < 0) {
            throw new IllegalArgumentException("Invalid knowledge entry page request");
        }
        return jdbcTemplate.query(
                "SELECT * FROM knowledge_entry WHERE topic_id=? AND entry_status='FINAL' " +
                        "ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                mapper, topicId, limit, offset
        );
    }

    public void linkEvidence(Long entryId, List<Long> evidenceIds) {
        if (evidenceIds == null) {
            return;
        }
        String now = TimeUtil.text(LocalDateTime.now());
        for (Long evidenceId : evidenceIds) {
            jdbcTemplate.update(
                    "INSERT OR IGNORE INTO knowledge_entry_evidence(" +
                            "knowledge_entry_id,evidence_id,created_at) VALUES(?,?,?)",
                    entryId, evidenceId, now
            );
        }
    }

    public List<Long> findEvidenceIds(Long entryId) {
        return new ArrayList<>(jdbcTemplate.query(
                "SELECT evidence_id FROM knowledge_entry_evidence " +
                        "WHERE knowledge_entry_id=? ORDER BY evidence_id",
                (resultSet, rowNumber) -> resultSet.getLong("evidence_id"),
                entryId
        ));
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
