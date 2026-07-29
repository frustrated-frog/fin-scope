package com.finscope.service.research.evaluation;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.common.exception.BusinessConflictException;
import com.finscope.dao.research.ResearchReportRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.research.ResearchRunOutputRepository;
import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.dao.research.agent.ResearchAgentRepository;
import com.finscope.dao.research.evaluation.ResearchEvaluationRepository;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchSearchEvidence;
import com.finscope.domain.research.evaluation.ResearchEvaluation;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.domain.research.runtime.ResearchRuntimeEvent;
import com.finscope.domain.research.agent.ResearchAgentTraceView;
import com.finscope.domain.research.agent.ResearchAgentTrajectoryMetrics;
import com.finscope.service.research.agent.ResearchTrajectoryEvaluator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Resource;

@Service
public class ResearchEvaluationService {
    private final ResearchRunRepository runRepository;
    private final ResearchReportRepository reportRepository;
    private final ResearchRuntimeRepository runtimeRepository;
    private final ResearchRunOutputRepository outputRepository;
    private final ResearchSearchEvidenceRepository searchEvidenceRepository;
    private final ResearchEvaluationRepository evaluationRepository;
    private final ResearchEvaluationScorer scorer;
    @Resource
    private ResearchAgentRepository researchAgentRepository;
    @Resource
    private ResearchTrajectoryEvaluator trajectoryEvaluator;

    public ResearchEvaluationService(ResearchRunRepository runRepository,
                                     ResearchReportRepository reportRepository,
                                     ResearchRuntimeRepository runtimeRepository,
                                     ResearchRunOutputRepository outputRepository,
                                     ResearchSearchEvidenceRepository searchEvidenceRepository,
                                     ResearchEvaluationRepository evaluationRepository,
                                     ResearchEvaluationScorer scorer) {
        this.runRepository = runRepository;
        this.reportRepository = reportRepository;
        this.runtimeRepository = runtimeRepository;
        this.outputRepository = outputRepository;
        this.searchEvidenceRepository = searchEvidenceRepository;
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
        List<ResearchSearchEvidence> searchEvidence = searchEvidenceRepository.findByRunId(runId);
        int evidenceCount = outputRepository.countByRunIdAndType(runId, "EVIDENCE") + searchEvidence.size();
        int sourceCount = actualSourceCount(runId, searchEvidence);
        ResearchEvaluation evaluation = scorer.score(
                new ResearchEvaluationSnapshot(run, report, checkpoint, events, evidenceCount, sourceCount));
        ResearchAgentTrajectoryMetrics trajectory = trajectory(runId);
        scorer.appendTrajectoryMetric(evaluation, trajectory);
        evaluation.setInputFingerprint(fingerprint(run, report, checkpoint, events, trajectory,
                evidenceCount, sourceCount));
        return evaluationRepository.save(evaluation);
    }

    public Optional<ResearchEvaluation> findLatest(Long runId) {
        return evaluationRepository.findLatestByRunId(runId);
    }

    public List<ResearchEvaluation> findAll(Long runId) {
        return evaluationRepository.findAllByRunId(runId);
    }

    private String fingerprint(ResearchRun run, ResearchReport report,
                               ResearchRuntimeCheckpoint checkpoint, List<ResearchRuntimeEvent> events,
                               ResearchAgentTrajectoryMetrics trajectory,
                               int actualEvidenceCount,
                               int actualSourceCount) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("v=").append(ResearchEvaluationScorer.VERSION)
                .append("|run=").append(run.getId()).append(':').append(run.getStatus())
                .append(':').append(run.getFetchedSourceCount()).append(':').append(run.getEvidenceCount())
                .append("|actual=").append(actualEvidenceCount).append(':').append(actualSourceCount)
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
        if (trajectory != null) {
            canonical.append("|trajectory=").append(trajectory.getDecisionCount()).append(':')
                    .append(trajectory.getObservationCount()).append(':')
                    .append(trajectory.getQualityScore()).append(':')
                    .append(trajectory.getDuplicateActionRate()).append(':')
                    .append(trajectory.getNoProgressRate()).append(':')
                    .append(trajectory.getFallbackRate());
        }
        return sha256(canonical.toString());
    }

    private int actualSourceCount(Long runId, List<ResearchSearchEvidence> searchEvidence) {
        Set<String> identities = new HashSet<String>();
        for (String identity : outputRepository.findDistinctArticleSourceIdentities(runId)) {
            if (hasText(identity)) identities.add(identity.trim().toLowerCase(Locale.ROOT));
        }
        for (ResearchSearchEvidence item : searchEvidence) {
            String identity = hasText(item.getSourceDomain()) ? item.getSourceDomain() : item.getProvider();
            if (hasText(identity)) identities.add(identity.trim().toLowerCase(Locale.ROOT));
        }
        return identities.size();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private ResearchAgentTrajectoryMetrics trajectory(Long runId) {
        if (researchAgentRepository == null || trajectoryEvaluator == null
                || !researchAgentRepository.findState(runId).isPresent()) {
            return null;
        }
        ResearchAgentTraceView trace = researchAgentRepository.findTrace(runId);
        return trajectoryEvaluator.evaluate(trace);
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
