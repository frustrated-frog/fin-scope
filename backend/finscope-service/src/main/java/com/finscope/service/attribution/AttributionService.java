package com.finscope.service.attribution;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.util.StringUtils;
import com.finscope.dao.attribution.AttributionRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.attribution.AttributionEvidence;
import com.finscope.domain.attribution.AttributionReport;
import com.finscope.domain.instrument.Instrument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
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
    private AttributionAgent attributionAgent;
    @Resource
    private AttributionProgressPublisher progressPublisher;
    @Resource(name = "ingestTaskExecutor")
    private Executor executor;

    /**
     * 触发一次归因研究，返回 taskId（前端据此订阅 SSE 进度）。
     */
    public String startAttribution(String code, String type, String name, Double changePct) {
        if (StringUtils.isBlank(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标的代码不能为空");
        }
        String normalizedType = normalizeType(type);
        Instrument instrument = instrumentRepository.findByCodeAndType(code.trim(), normalizedType)
                .orElseGet(() -> transientInstrument(code.trim(), normalizedType, name));

        // 先建 GENERATING 报告
        AttributionReport report = new AttributionReport();
        report.setInstrumentCode(instrument.getCode());
        report.setInstrumentName(StringUtils.firstNonBlank(instrument.getName(), name, instrument.getCode()));
        report.setInstrumentType(instrument.getType());
        report.setReportDate(LocalDate.now());
        report.setChangePct(changePct);
        report.setStatus("GENERATING");
        AttributionReport saved = attributionRepository.createReport(report);

        String taskId = UUID.randomUUID().toString();
        executor.execute(() -> runResearch(taskId, saved, instrument, changePct));
        log.info("归因任务已提交 taskId={} code={} reportId={}", taskId, code, saved.getId());
        return taskId;
    }

    private void runResearch(String taskId, AttributionReport report, Instrument instrument, Double changePct) {
        long start = System.currentTimeMillis();
        try {
            attributionAgent.research(report, instrument, changePct, taskId, progressPublisher);
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
            progressPublisher.publish(taskId, AttributionProgressEvent.error(
                    StringUtils.firstNonBlank(ex.getMessage(), "归因研究失败")));
        } finally {
            progressPublisher.complete(taskId);
        }
    }

    public AttributionReport getReport(Long reportId) {
        AttributionReport report = attributionRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "归因报告不存在: " + reportId));
        report.setEvidences(attributionRepository.findEvidenceByReportId(reportId));
        return report;
    }

    /** 某标的最新归因报告（用于卡片摘要徽标），无则返回 null。 */
    public AttributionReport getLatestByCode(String code) {
        return attributionRepository.findLatestByCode(code).orElse(null);
    }

    public List<AttributionReport> getHistory(String code, int limit) {
        return attributionRepository.findHistoryByCode(code, limit <= 0 ? 10 : limit);
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
}