package com.finscope.service.research.evaluation;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.common.exception.BusinessConflictException;
import com.finscope.dao.research.ResearchReportRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.research.ResearchRunOutputRepository;
import com.finscope.dao.research.evaluation.ResearchEvaluationRepository;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.evaluation.ResearchEvaluation;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.domain.research.runtime.ResearchRuntimeEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

@Service
public class ResearchEvaluationService {
    private final ResearchRunRepository runRepository;
    private final ResearchReportRepository reportRepository;
    private final ResearchRuntimeRepository runtimeRepository;
    private final ResearchRunOutputRepository outputRepository;
    private final ResearchEvaluationRepository evaluationRepository;
    private final ResearchEvaluationScorer scorer;

    public ResearchEvaluationService(ResearchRunRepository runRepository,
                                     ResearchReportRepository reportRepository,
                                     ResearchRuntimeRepository runtimeRepository,
                                     ResearchRunOutputRepository outputRepository,
                                     ResearchEvaluationRepository evaluationRepository,
                                     ResearchEvaluationScorer scorer) {
        this.runRepository = runRepository;
        this.reportRepository = reportRepository;
        this.runtimeRepository = runtimeRepository;
        this.outputRepository = outputRepository;
        this.evaluationRepository = evaluationRepository;
        this.scorer = scorer;
    }

    @Transactional
    public ResearchEvaluation evaluate(Long runId) {
        ResearchRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("研究运行不存在：" + runId));
        if (ResearchEnums.RUN_STATUS_RUNNING.equals(run.getStatus())) {
            throw new BusinessConflictException("研究运行仍在执行，结束后才能进行离线评测：" + runId);
        }
        ResearchReport report = reportRepository.findByRunId(runId).orElse(null);
        ResearchRuntimeCheckpoint checkpoint = runtimeRepository.findCheckpoint(runId).orElse(null);
        List<ResearchRuntimeEvent> events = runtimeRepository.findEvents(runId);
        int evidenceCount = outputRepository.countByRunIdAndType(runId, "EVIDENCE");
        int sourceCount = outputRepository.countDistinctArticleSources(runId);
        ResearchEvaluation evaluation = scorer.score(
                new ResearchEvaluationSnapshot(run, report, checkpoint, events, evidenceCount, sourceCount));
        evaluation.setInputFingerprint(fingerprint(run, report, checkpoint, events));
        return evaluationRepository.save(evaluation);
    }

    public Optional<ResearchEvaluation> findLatest(Long runId) {
        return evaluationRepository.findLatestByRunId(runId);
    }

    public List<ResearchEvaluation> findAll(Long runId) {
        return evaluationRepository.findAllByRunId(runId);
    }

    private String fingerprint(ResearchRun run, ResearchReport report,
                               ResearchRuntimeCheckpoint checkpoint, List<ResearchRuntimeEvent> events) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("v=").append(ResearchEvaluationScorer.VERSION)
                .append("|run=").append(run.getId()).append(':').append(run.getStatus())
                .append(':').append(run.getFetchedSourceCount()).append(':').append(run.getEvidenceCount())
                .append("|report=").append(report == null ? "missing" : report.getId() + ":"
                        + report.getEvidenceCount() + ":" + report.getSourceCount() + ":" + report.getCharacterCount()
                        + ":" + report.getStatus() + ":" + report.getContentMarkdown())
                .append("|checkpoint=");
        if (checkpoint == null) {
            canonical.append("missing");
        } else {
            canonical.append(checkpoint.getStateVersion()).append(':')
                    .append(checkpoint.getStatus()).append(':').append(checkpoint.getConsumedActions()).append(':')
                    .append(checkpoint.getMaxActions()).append(':').append(checkpoint.getResumeCount());
        }
        for (ResearchRuntimeEvent event : events) {
            canonical.append("|event=").append(event.getSequenceNo()).append(':')
                    .append(event.getEventType()).append(':').append(event.getNodeId()).append(':')
                    .append(event.getStatus()).append(':').append(event.getStateHash()).append(':')
                    .append(event.getProgressDelta()).append(':').append(event.getErrorType());
        }
        return sha256(canonical.toString());
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("JDK does not provide SHA-256", error);
        }
    }
}
