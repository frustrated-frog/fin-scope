package com.finscope.service.research.agent;

import com.finscope.dao.research.mission.ResearchMissionRepository;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.service.research.report.EvidenceSufficiency;
import com.finscope.service.research.report.ResearchReportService;
import com.finscope.service.research.method.ResearchMethodCompletionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ResearchFinishVerifier {
    private final ResearchReportService reportService;
    private final ResearchMissionRepository missionRepository;
    private final ResearchRuntimeRepository runtimeRepository;
    private final ResearchMethodCompletionPolicy methodCompletionPolicy;

    public ResearchFinishVerifier(ResearchReportService reportService,
                                  ResearchMissionRepository missionRepository,
                                  ResearchRuntimeRepository runtimeRepository) {
        this(reportService, missionRepository, runtimeRepository, new ResearchMethodCompletionPolicy());
    }

    @Autowired
    public ResearchFinishVerifier(ResearchReportService reportService,
                                  ResearchMissionRepository missionRepository,
                                  ResearchRuntimeRepository runtimeRepository,
                                  ResearchMethodCompletionPolicy methodCompletionPolicy) {
        this.reportService = reportService;
        this.missionRepository = missionRepository;
        this.runtimeRepository = runtimeRepository;
        this.methodCompletionPolicy = methodCompletionPolicy;
    }

    public ResearchFinishVerdict verify(Long runId) {
        EvidenceSufficiency evidence = reportService.assessSufficiency(runId);
        ResearchMission mission = missionRepository.findMission(runId)
                .orElseThrow(() -> new IllegalStateException("研究 Mission 不存在：" + runId));
        ResearchRuntimeCheckpoint runtime = runtimeRepository.findCheckpoint(runId)
                .orElseThrow(() -> new IllegalStateException("研究 Runtime 不存在：" + runId));
        List<String> missing = new ArrayList<String>(evidence.getWarnings());
        List<String> methodMissing = methodCompletionPolicy.missingConditions(
                mission, missionRepository.findTasks(runId));
        missing.addAll(methodMissing);
        if (mission.getActiveTaskKey() != null && !mission.getActiveTaskKey().trim().isEmpty()) {
            missing.add("仍有研究任务正在执行：" + mission.getActiveTaskKey());
        }
        if (invalidRuntime(runtime.getStatus())) {
            missing.add("研究运行时状态不允许主动完成：" + runtime.getStatus());
        }
        if (missing.isEmpty()) {
            return new ResearchFinishVerdict(true, "ACCEPTED", missing);
        }
        String reason = !methodMissing.isEmpty() ? "METHOD_INCOMPLETE"
                : !evidence.isSufficient() ? "EVIDENCE_INSUFFICIENT"
                : mission.getActiveTaskKey() != null ? "ACTIVE_TASK"
                : "RUNTIME_INCONSISTENT";
        return new ResearchFinishVerdict(false, reason, missing);
    }

    private boolean invalidRuntime(String status) {
        return "INTERRUPTED".equals(status) || "CANCELLED".equals(status)
                || "FAILED".equals(status) || "TERMINATED".equals(status);
    }
}
