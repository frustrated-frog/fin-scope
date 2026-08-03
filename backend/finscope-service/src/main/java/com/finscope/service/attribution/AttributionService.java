package com.finscope.service.attribution;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.common.util.StringUtils;
import com.finscope.dao.attribution.AttributionRepository;
import com.finscope.dao.attribution.AttributionResearchRunRepository;
import com.finscope.domain.attribution.AttributionResearchRun;
import com.finscope.domain.attribution.AttributionResearchStep;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.attribution.AttributionEvidence;
import com.finscope.domain.attribution.AttributionReport;
import com.finscope.domain.instrument.Instrument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 归因服务：编排"手动深度归因"异步任务，串起 Agent 工作流 + 报告持久化 + SSE 进度。
 */
@Service
@Slf4j
public class AttributionService {
    @Resource
    private InstrumentRepository instrumentRepository;
    @Resource
    private AttributionRepository attributionRepository;
    @Resource
    private AttributionHarness attributionHarness;
    @Resource(name = "attributionResearchRunRepository")
    private AttributionResearchRunRepository researchRunRepository;
    @Resource
    private AttributionProgressPublisher progressPublisher;
    @Resource(name = "attributionTaskExecutor")
    private Executor executor;

    /**
     * 触发一次归因研究，返回 taskId（前端据此订阅 SSE 进度）。
     */
    public AttributionStartResult startAttribution(String code, String type, String name, Double changePct, String quoteDate) {
        if (StringUtils.isBlank(code)) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "标的代码不能为空");
        }
        String normalizedType = normalizeType(type);
        Instrument instrument = instrumentRepository.findByCodeAndType(code.trim(), normalizedType)
                .orElseGet(() -> transientInstrument(code.trim(), normalizedType, name));

        // 先建 GENERATING 报告
        AttributionReport report = new AttributionReport();
        report.setInstrumentCode(instrument.getCode());
        report.setInstrumentName(StringUtils.firstNonBlank(instrument.getName(), name, instrument.getCode()));
        report.setInstrumentType(instrument.getType());
        report.setReportDate(parseReportDate(quoteDate));
        report.setChangePct(changePct);
        report.setStatus("GENERATING");
        AttributionReport saved = attributionRepository.createReport(report);

        String taskId = UUID.randomUUID().toString();
        try {
            executor.execute(() -> runResearch(taskId, saved, instrument, changePct));
        } catch (RuntimeException ex) {
            reportSubmissionFailed(saved, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "归因任务提交失败，请稍后重试");
        }
        log.info("归因任务已提交 taskId={} code={} reportId={}", taskId, code, saved.getId());
        return new AttributionStartResult(taskId, saved.getId());
    }

    private void runResearch(String taskId, AttributionReport report, Instrument instrument, Double changePct) {
        long start = System.currentTimeMillis();
        try {
            attributionHarness.research(report, instrument, changePct, taskId, progressPublisher);
            // 持久化证据
            if (report.getEvidences() != null) {
                for (AttributionEvidence evidence : report.getEvidences()) {
                    evidence.setReportId(report.getId());
                    attributionRepository.saveEvidence(evidence);
                }
            }
            report.setStatus("COMPLETED");
            report.setDurationMs(System.currentTimeMillis() - start);
            attributionRepository.updateResult(report);
            attributionHarness.markPersisted(report);
            progressPublisher.publish(taskId, AttributionProgressEvent.done(report.getId(), "归因报告已生成"));
        } catch (Exception ex) {
            log.error("归因研究失败 taskId={} code={}", taskId, instrument.getCode(), ex);
            report.setStatus("FAILED");
            report.setErrorMessage(ex.getMessage());
            report.setDurationMs(System.currentTimeMillis() - start);
            try {
                attributionRepository.updateResult(report);
            } catch (Exception ignore) {
                // 忽略持久化失败
            }
            try {
                attributionHarness.markPersistenceFailed(report, ex);
            } catch (Exception stateEx) {
                log.error("归因运行失败状态写入失败 reportId={}", report.getId(), stateEx);
            }
            progressPublisher.publish(taskId, AttributionProgressEvent.error(
                    StringUtils.firstNonBlank(ex.getMessage(), "归因研究失败")));
        } finally {
            progressPublisher.complete(taskId);
        }
    }

    public AttributionReport getReport(Long reportId) {
        AttributionReport report = attributionRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "归因报告不存在: " + reportId));
        report.setEvidences(attributionRepository.findEvidenceByReportId(reportId));
        return report;
    }

    public AttributionResearchRunView getResearchRun(Long reportId) {
        AttributionResearchRun run = researchRunRepository.findByReportId(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "归因研究运行不存在: " + reportId));
        return new AttributionResearchRunView(run, researchRunRepository.findStepsByRunId(run.getId()));
    }

    /** 某标的最新归因报告（用于卡片摘要徽标），无则返回 null。 */
    public AttributionReport getLatestByIdentity(String code, String type) {
        return attributionRepository.findLatestByIdentity(normalizeCode(code), normalizeType(type)).orElse(null);
    }

    public List<AttributionReport> getHistory(String code, String type, int limit) {
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 50);
        return attributionRepository.findHistoryByIdentity(normalizeCode(code), normalizeType(type), safeLimit);
    }

    /** 删除一份已结束的归因报告，以及它关联的研究运行和证据。 */
    @Transactional
    public void deleteReport(Long reportId) {
        AttributionReport report = attributionRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("归因报告不存在: " + reportId));
        if ("GENERATING".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "归因研究仍在运行，暂不能删除");
        }
        researchRunRepository.deleteByReportId(reportId);
        attributionRepository.deleteById(reportId);
    }

    private void reportSubmissionFailed(AttributionReport report, RuntimeException ex) {
        log.error("归因任务提交被拒绝 reportId={}", report.getId(), ex);
        report.setStatus("FAILED");
        report.setErrorMessage("归因任务暂时繁忙，请稍后重试");
        try {
            attributionRepository.updateResult(report);
        } catch (Exception persistenceEx) {
            log.error("归因提交失败状态写入失败 reportId={}", report.getId(), persistenceEx);
        }
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private LocalDate parseReportDate(String quoteDate) {
        if (StringUtils.isBlank(quoteDate)) return LocalDate.now();
        try {
            return LocalDate.parse(quoteDate.trim());
        } catch (RuntimeException ex) {
            log.warn("行情交易日格式无效 quoteDate={}，回退当前日期", quoteDate);
            return LocalDate.now();
        }
    }

    private Instrument transientInstrument(String code, String type, String name) {
        Instrument instrument = new Instrument();
        instrument.setCode(code);
        instrument.setType(type);
        instrument.setName(StringUtils.isBlank(name) ? code : name.trim());
        instrument.setAliases(code + (StringUtils.isBlank(name) ? "" : "," + name.trim()));
        return instrument;
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if ("STOCK".equals(normalized) || "FUND".equals(normalized) || "SECTOR".equals(normalized)) {
            return normalized;
        }
        return "STOCK";
    }

    public static class AttributionStartResult {
        private final String taskId;
        private final Long reportId;

        public AttributionStartResult(String taskId, Long reportId) {
            this.taskId = taskId;
            this.reportId = reportId;
        }

        public String getTaskId() {
            return taskId;
        }

        public Long getReportId() {
            return reportId;
        }
    }

    public static class AttributionResearchRunView {
        private final AttributionResearchRun run;
        private final List<AttributionResearchStep> steps;
        private final AttributionResearchProgress progress;
        public AttributionResearchRunView(AttributionResearchRun run, List<AttributionResearchStep> steps) {
            this.run = run;
            this.steps = steps == null ? Collections.<AttributionResearchStep>emptyList() : steps;
            this.progress = new AttributionResearchProgress(run, this.steps);
        }
        public AttributionResearchRun getRun() { return run; }
        public List<AttributionResearchStep> getSteps() { return steps; }
        public AttributionResearchProgress getProgress() { return progress; }
    }

    /** 由服务端统一定义可展示的真实轨道进度，前端不得自行推断。 */
    public static class AttributionResearchProgress {
        private final int plannedTracks;
        private final int activatedTracks;
        private final int settledTracks;
        private final String currentTrack;
        private final String currentStep;

        AttributionResearchProgress(AttributionResearchRun run, List<AttributionResearchStep> steps) {
            plannedTracks = steps.size();
            int activated = 0;
            int settled = 0;
            String runningTrack = null;
            for (AttributionResearchStep step : steps) {
                String status = step.getStatus();
                if (!"PLANNED".equals(status) && !"PENDING".equals(status)) {
                    activated++;
                }
                if ("COMPLETED".equals(status) || "PARTIAL".equals(status)
                        || "FAILED".equals(status) || "SKIPPED".equals(status)) {
                    settled++;
                }
                if (runningTrack == null && "RUNNING".equals(status)) {
                    runningTrack = step.getTrack();
                }
            }
            activatedTracks = activated;
            settledTracks = settled;
            currentTrack = runningTrack;
            currentStep = run == null ? null : run.getCurrentStep();
        }

        public int getPlannedTracks() { return plannedTracks; }
        public int getActivatedTracks() { return activatedTracks; }
        public int getSettledTracks() { return settledTracks; }
        public String getCurrentTrack() { return currentTrack; }
        public String getCurrentStep() { return currentStep; }
    }
}
