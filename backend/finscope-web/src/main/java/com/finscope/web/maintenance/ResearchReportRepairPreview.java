package com.finscope.web.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.research.ResearchThesisRepository;
import com.finscope.dao.research.ResearchRunOutputRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.dao.research.ResearchReportRepository;
import com.finscope.domain.research.ResearchReport;
import com.finscope.service.research.report.ResearchArchivedEventSource;
import com.finscope.service.research.report.RunScopedResearchContext;
import com.finscope.service.research.report.RunScopedResearchContextService;
import com.finscope.service.research.report.ResearchEvidenceCard;
import com.finscope.service.research.report.ResearchEvidenceSelector;
import com.finscope.service.research.report.ResearchReportGenerator;
import com.finscope.service.research.report.EvidenceSufficiency;
import com.finscope.service.research.report.GeneratedResearchReport;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only, offline application assembly: no scheduler, model, HTTP or schema initialization. */
public final class ResearchReportRepairPreview {
    private ResearchReportRepairPreview() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Expected database path, output JSON path and optional comma-separated run IDs");
        }
        Path database = Path.of(args[0]).toRealPath();
        Path output = Path.of(args[1]).toAbsolutePath();
        java.util.Set<Long> requestedRuns = new java.util.HashSet<>();
        if (args.length == 3) {
            for (String value : args[2].split(",")) {
                requestedRuns.add(Long.parseLong(value));
            }
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:sqlite:file:" + database + "?mode=ro");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JdbcTemplate.class, () -> jdbc);
            context.register(MajorEventRepository.class, ResearchArchivedEventSource.class, ArticleRepository.class, ResearchRunRepository.class, ResearchThesisRepository.class,
                    ResearchRunOutputRepository.class, EventClusterRepository.class, EvidenceItemRepository.class,
                    ResearchSearchEvidenceRepository.class, ResearchReportRepository.class,
                    RunScopedResearchContextService.class, ResearchEvidenceSelector.class, ResearchReportGenerator.class);
            context.refresh();
            List<Map<String, Object>> plans = new ArrayList<>();
            List<Long> runIds = reportRunIds(jdbc);
            for (Long runId : runIds) {
                RunScopedResearchContext source = context.getBean(RunScopedResearchContextService.class).load(runId);
                if (source.getThesis() == null || "RUNNING".equals(source.getRun().getStatus())) {
                    continue;
                }
                ResearchReport previous = context.getBean(ResearchReportRepository.class).findByRunId(runId).orElseThrow();
                List<ResearchEvidenceCard> cards = context.getBean(ResearchEvidenceSelector.class).select(
                        source.getThesis(), context.getBean(ResearchArchivedEventSource.class).articlesFor(source), source.getEvidenceItems(),
                        context.getBean(ResearchSearchEvidenceRepository.class).findByRunId(runId));
                boolean needsRepair = requestedRuns.isEmpty()
                        ? contaminated(previous.getContentMarkdown()) || contaminated(previous.getConclusion())
                        : requestedRuns.contains(runId);
                if (!needsRepair) {
                    continue;
                }
                Map<String, Object> plan = new LinkedHashMap<>();
                plan.put("original", jdbc.queryForMap("SELECT * FROM research_report WHERE id=?", previous.getId()));
                Map<String, Object> replacement = new LinkedHashMap<>();
                if (cards.isEmpty()) {
                    replacement.put("status", "INVALIDATED");
                    replacement.put("conclusion", "旧报告未通过证据质量校验，请重新运行研究。");
                    replacement.put("conclusion_direction", "INSUFFICIENT");
                    replacement.put("confidence", "LOW");
                    replacement.put("executive_summary", "清理导航和无关材料后，本次运行没有可引用的对象相关证据。");
                    replacement.put("content_markdown", "# " + previous.getTitle()
                            + "\n\n## 证据校验结果\n\n旧报告已失效，不应继续使用原结论。"
                            + "\n\n清理网页导航和无关材料后，没有可引用的对象相关证据。请重新运行研究补充材料。");
                } else {
                    EvidenceSufficiency sufficiency = EvidenceSufficiency.assess(cards);
                    GeneratedResearchReport generated = context.getBean(ResearchReportGenerator.class)
                            .generate(source.getThesis(), cards, sufficiency);
                    replacement.put("status", sufficiency.isSufficient() ? "COMPLETED" : "COMPLETED_WITH_GAPS");
                    replacement.put("conclusion", generated.getConclusion());
                    replacement.put("conclusion_direction", generated.getConclusionDirection());
                    replacement.put("confidence", generated.getConfidence());
                    replacement.put("executive_summary", generated.getExecutiveSummary());
                    replacement.put("content_markdown", generated.getMarkdown());
                }
                replacement.put("generation_mode", "DETERMINISTIC");
                replacement.put("warning_message", "已基于原始留存材料离线修复，未执行新的搜索或模型调用；"
                        + "旧版本已备份。" + (cards.isEmpty() ? "当前无有效证据，原结论已失效。"
                        : String.join("；", EvidenceSufficiency.assess(cards).getWarnings())));
                replacement.put("evidence_count", cards.size());
                replacement.put("source_count", cards.stream().map(ResearchEvidenceCard::getSourceIdentity).distinct().count());
                replacement.put("character_count", replacement.get("content_markdown").toString().length());
                plan.put("replacement", replacement);
                plan.put("findings", cards.stream().map(card -> {
                    Map<String, Object> finding = new LinkedHashMap<>();
                    finding.put("stance", "NEUTRAL".equals(card.getStance()) ? "UNKNOWN" : card.getStance());
                    finding.put("summary", card.getClaim());
                    finding.put("evidence_id", card.getEvidenceItem() == null ? null : card.getEvidenceItem().getId());
                    return finding;
                }).collect(Collectors.toList()));
                plans.add(plan);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("database", database.toString());
            result.put("plans", plans);
            Files.writeString(output, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }

    private static List<Long> reportRunIds(JdbcTemplate jdbc) {
        List<Long> result = new ArrayList<>();
        long cursor = 0;
        while (true) {
            List<Map<String, Object>> page = jdbc.queryForList(
                    "SELECT id,research_run_id FROM research_report WHERE id>? ORDER BY id LIMIT 100", cursor);
            if (page.isEmpty()) {
                return result;
            }
            for (Map<String, Object> row : page) {
                result.add(((Number) row.get("research_run_id")).longValue());
                cursor = ((Number) row.get("id")).longValue();
            }
        }
    }

    private static boolean contaminated(String value) {
        String text = value == null ? "" : value;
        return text.contains("您当前的位置") || text.contains("Image 1: qrcode")
                || text.contains("移动版 网页版") || text.matches("(?s).*\\[\\s*]\\s*\\(.*")
                || text.matches("(?s).*\\.{8,}.*")
                || text.matches("(?s).*\\d+\\s*/\\s*\\d+\\s+#{1,3}.*")
                || text.contains("字号 超大 大 标准 小") || text.contains("缩小字体 放大字体 收藏");
    }
}
