package com.finscope.dao.research;

import com.finscope.common.util.TimeUtil;
import com.finscope.domain.research.ResearchReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ResearchReportRepository {
    @Resource
    private JdbcTemplate jdbcTemplate;

    public ResearchReportRepository() {
    }

    ResearchReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ResearchReport> rowMapper = (rs, rowNum) -> {
        ResearchReport report = new ResearchReport();
        report.setId(rs.getLong("id"));
        report.setResearchRunId(rs.getLong("research_run_id"));
        long thesisId = rs.getLong("thesis_id");
        report.setThesisId(rs.wasNull() ? null : thesisId);
        report.setReportType(rs.getString("report_type"));
        report.setStatus(rs.getString("status"));
        report.setTitle(rs.getString("title"));
        report.setConclusion(rs.getString("conclusion"));
        report.setConclusionDirection(rs.getString("conclusion_direction"));
        report.setConfidence(rs.getString("confidence"));
        report.setExecutiveSummary(rs.getString("executive_summary"));
        report.setContentMarkdown(rs.getString("content_markdown"));
        report.setMarkdownPath(rs.getString("markdown_path"));
        report.setGenerationMode(rs.getString("generation_mode"));
        report.setWarningMessage(rs.getString("warning_message"));
        report.setEvidenceCount(rs.getInt("evidence_count"));
        report.setSourceCount(rs.getInt("source_count"));
        report.setCharacterCount(rs.getInt("character_count"));
        report.setCreatedAt(TimeUtil.localDateTime(rs, "created_at"));
        report.setUpdatedAt(TimeUtil.localDateTime(rs, "updated_at"));
        return report;
    };

    public ResearchReport upsert(ResearchReport report) {
        LocalDateTime now = LocalDateTime.now();
        if (report.getCreatedAt() == null) {
            report.setCreatedAt(now);
        }
        report.setUpdatedAt(now);
        jdbcTemplate.update("INSERT INTO research_report(research_run_id,thesis_id,report_type,status,title,"
                        + "conclusion,conclusion_direction,confidence,executive_summary,content_markdown,markdown_path,"
                        + "generation_mode,warning_message,evidence_count,source_count,character_count,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(research_run_id) DO UPDATE SET "
                        + "thesis_id=excluded.thesis_id,report_type=excluded.report_type,status=excluded.status,"
                        + "title=excluded.title,conclusion=excluded.conclusion,"
                        + "conclusion_direction=excluded.conclusion_direction,confidence=excluded.confidence,"
                        + "executive_summary=excluded.executive_summary,content_markdown=excluded.content_markdown,"
                        + "markdown_path=excluded.markdown_path,generation_mode=excluded.generation_mode,"
                        + "warning_message=excluded.warning_message,evidence_count=excluded.evidence_count,"
                        + "source_count=excluded.source_count,character_count=excluded.character_count,"
                        + "updated_at=excluded.updated_at",
                report.getResearchRunId(), report.getThesisId(), report.getReportType(), report.getStatus(),
                report.getTitle(), report.getConclusion(), report.getConclusionDirection(), report.getConfidence(),
                report.getExecutiveSummary(), report.getContentMarkdown(), report.getMarkdownPath(),
                report.getGenerationMode(), report.getWarningMessage(), report.getEvidenceCount(),
                report.getSourceCount(), report.getCharacterCount(), TimeUtil.text(report.getCreatedAt()),
                TimeUtil.text(report.getUpdatedAt()));
        return findByRunId(report.getResearchRunId())
                .orElseThrow(() -> new IllegalStateException("Research report upsert failed"));
    }

    public Optional<ResearchReport> findById(Long id) {
        List<ResearchReport> reports = jdbcTemplate.query("SELECT * FROM research_report WHERE id = ?", rowMapper, id);
        return reports.isEmpty() ? Optional.<ResearchReport>empty() : Optional.of(reports.get(0));
    }

    public Optional<ResearchReport> findByRunId(Long runId) {
        List<ResearchReport> reports = jdbcTemplate.query(
                "SELECT * FROM research_report WHERE research_run_id = ?", rowMapper, runId);
        return reports.isEmpty() ? Optional.<ResearchReport>empty() : Optional.of(reports.get(0));
    }

    public List<ResearchReport> findByThesisId(Long thesisId) {
        return jdbcTemplate.query("SELECT * FROM research_report WHERE thesis_id = ? ORDER BY created_at DESC, id DESC",
                rowMapper, thesisId);
    }
}
