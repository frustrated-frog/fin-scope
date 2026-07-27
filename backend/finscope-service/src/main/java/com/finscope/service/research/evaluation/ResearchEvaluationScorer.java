package com.finscope.service.research.evaluation;

import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.evaluation.ResearchEvaluation;
import com.finscope.domain.research.evaluation.ResearchEvaluationMetric;
import com.finscope.domain.research.agent.ResearchAgentTrajectoryMetrics;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.domain.research.runtime.ResearchRuntimeEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ResearchEvaluationScorer {
    public static final String VERSION = "deep-research-rules-v2";

    public ResearchEvaluation score(ResearchEvaluationSnapshot snapshot) {
        ResearchRun run = snapshot.getRun();
        ResearchReport report = snapshot.getReport();
        ResearchRuntimeCheckpoint checkpoint = snapshot.getCheckpoint();
        List<String> criticalIssues = new ArrayList<String>();
        List<ResearchEvaluationMetric> metrics = new ArrayList<ResearchEvaluationMetric>();

        int completion = completionScore(run, report, criticalIssues);
        if (checkpointRunConflict(run, checkpoint)) {
            criticalIssues.add("CHECKPOINT_RUN_STATUS_CONFLICT");
        }
        metrics.add(metric("completion", "产物完整性", completion, 20,
                "runStatus=" + safe(run == null ? null : run.getStatus()) + ", report=" + (report != null),
                "确保成功状态与研究报告同时落库"));

        int evidenceCount = snapshot.getActualEvidenceCount();
        if (report != null && (report.getEvidenceCount() > evidenceCount
                || report.getSourceCount() > snapshot.getActualSourceCount())) {
            criticalIssues.add("REPORT_OUTPUT_MISMATCH");
        }
        int evidence = Math.min(evidenceCount, 3) * 25 / 3;
        metrics.add(metric("evidence", "证据覆盖", evidence, 25, "evidenceCount=" + evidenceCount,
                "至少保留三条可追溯证据"));

        int sourceCount = snapshot.getActualSourceCount();
        int diversity = Math.min(sourceCount, 2) * 15 / 2;
        metrics.add(metric("source_diversity", "来源多样性", diversity, 15, "sourceCount=" + sourceCount,
                "至少使用两个独立来源交叉验证"));

        boolean validSequence = validSequence(snapshot, criticalIssues);
        if (!validSequence) {
            criticalIssues.add("INVALID_EVENT_SEQUENCE");
        }
        boolean danglingNode = hasDanglingStartedNode(snapshot.getEvents());
        if (danglingNode) {
            criticalIssues.add("DANGLING_STARTED_NODE");
        }
        boolean reportTraced = hasEvent(snapshot.getEvents(), "NODE_COMPLETED", "compose_report");
        boolean runtimeClosed = hasEvent(snapshot.getEvents(), "RUNTIME_COMPLETED", null)
                || hasEvent(snapshot.getEvents(), "RUNTIME_TERMINATED", null)
                || hasEvent(snapshot.getEvents(), "TERMINATED", null);
        int trace = validSequence && !danglingNode ? 10 : 0;
        trace += reportTraced ? 5 : 0;
        trace += runtimeClosed ? 5 : 0;
        metrics.add(metric("trace_integrity", "运行轨迹完整性", trace, 20,
                "events=" + snapshot.getEvents().size() + ", reportTraced=" + reportTraced
                        + ", runtimeClosed=" + runtimeClosed,
                "保留严格递增事件序列、报告节点和终止事件"));

        int budget = budgetScore(checkpoint, criticalIssues);
        metrics.add(metric("budget_safety", "预算安全", budget, 10,
                checkpoint == null ? "checkpoint=missing" : "consumed=" + checkpoint.getConsumedActions()
                        + "/" + checkpoint.getMaxActions(), "动作数不得超过运行预算"));

        int recovery = recoveryScore(checkpoint);
        metrics.add(metric("recovery", "恢复能力", recovery, 10,
                checkpoint == null ? "checkpoint=missing" : "resumeCount=" + checkpoint.getResumeCount()
                        + ", status=" + checkpoint.getStatus(), "中断后应从检查点恢复并进入终态"));

        int total = 0;
        for (ResearchEvaluationMetric metric : metrics) {
            total += metric.getScore();
        }
        ResearchEvaluation evaluation = new ResearchEvaluation();
        evaluation.setResearchRunId(run == null ? null : run.getId());
        evaluation.setEvaluatorVersion(VERSION);
        evaluation.setScore(total);
        evaluation.setCriticalIssues(criticalIssues);
        evaluation.setGateStatus(criticalIssues.isEmpty() && total >= 80 ? "PASS" : "BLOCK");
        evaluation.setSummary("score=" + total + ", gate=" + evaluation.getGateStatus()
                + ", criticalIssues=" + criticalIssues.size());
        evaluation.setMetrics(metrics);
        return evaluation;
    }

    public void appendTrajectoryMetric(ResearchEvaluation evaluation,
                                       ResearchAgentTrajectoryMetrics trajectory) {
        if (evaluation == null || trajectory == null || trajectory.getDecisionCount() == 0) {
            return;
        }
        List<ResearchEvaluationMetric> metrics = new ArrayList<ResearchEvaluationMetric>(evaluation.getMetrics());
        metrics.add(metric("agent_trajectory", "Agent轨迹质量", trajectory.getQualityScore(), 100,
                "decisions=" + trajectory.getDecisionCount()
                        + ", observationFollowup=" + decimal(trajectory.getObservationFollowupRate())
                        + ", duplicate=" + decimal(trajectory.getDuplicateActionRate())
                        + ", noProgress=" + decimal(trajectory.getNoProgressRate())
                        + ", fallback=" + decimal(trajectory.getFallbackRate()),
                "减少重复和无进展动作，并确保 Observation 进入后续决策"));
        evaluation.setMetrics(metrics);
    }

    private int completionScore(ResearchRun run, ResearchReport report, List<String> issues) {
        String status = run == null ? null : run.getStatus();
        if (ResearchEnums.RUN_STATUS_COMPLETED.equals(status) && report == null) {
            issues.add("COMPLETED_WITHOUT_REPORT");
            return 0;
        }
        if (ResearchEnums.RUN_STATUS_COMPLETED.equals(status) && report != null) {
            return 20;
        }
        if (ResearchEnums.RUN_STATUS_PARTIAL_SUCCESS.equals(status) && report != null) {
            return 14;
        }
        return report == null ? 0 : 8;
    }

    private boolean checkpointRunConflict(ResearchRun run, ResearchRuntimeCheckpoint checkpoint) {
        if (run == null || checkpoint == null) {
            return false;
        }
        if (ResearchEnums.RUN_STATUS_COMPLETED.equals(run.getStatus())) {
            return !"COMPLETED".equals(checkpoint.getStatus());
        }
        if (ResearchEnums.RUN_STATUS_RUNNING.equals(run.getStatus())) {
            return checkpoint.isTerminal();
        }
        return "COMPLETED".equals(checkpoint.getStatus())
                && ResearchEnums.RUN_STATUS_FAILED.equals(run.getStatus());
    }

    private int budgetScore(ResearchRuntimeCheckpoint checkpoint, List<String> issues) {
        if (checkpoint == null || checkpoint.getMaxActions() <= 0) {
            issues.add("MISSING_RUNTIME_CHECKPOINT");
            return 0;
        }
        if (checkpoint.getConsumedActions() > checkpoint.getMaxActions()) {
            issues.add("BUDGET_OVERRUN");
            return 0;
        }
        if (checkpoint.getConsumedActions() < 0) {
            issues.add("INVALID_ACTION_BUDGET");
            return 0;
        }
        return 10;
    }

    private int recoveryScore(ResearchRuntimeCheckpoint checkpoint) {
        if (checkpoint == null) {
            return 0;
        }
        if (checkpoint.getResumeCount() == 0) {
            return 10;
        }
        return checkpoint.isTerminal() ? 10 : 3;
    }

    private boolean validSequence(ResearchEvaluationSnapshot snapshot, List<String> issues) {
        List<ResearchRuntimeEvent> events = snapshot.getEvents();
        if (events.isEmpty() || !"RUN_CREATED".equals(events.get(0).getEventType())) {
            return false;
        }
        int previous = 0;
        int terminalCount = 0;
        int actionStarts = 0;
        int maxAssessmentRound = 0;
        Map<String, Integer> open = new HashMap<String, Integer>();
        Set<String> completed = new HashSet<String>();
        for (ResearchRuntimeEvent event : events) {
            if (event.getSequenceNo() <= previous) {
                return false;
            }
            previous = event.getSequenceNo();
            if (terminalCount > 0) {
                return false;
            }
            String type = event.getEventType();
            String node = event.getNodeId();
            if ("NODE_STARTED".equals(type)) {
                if (node == null || open.containsKey(node)) {
                    return false;
                }
                open.put(node, 1);
                if (event.getActionFingerprint() != null) {
                    actionStarts++;
                }
                if (node.startsWith("assess_evidence:")) {
                    maxAssessmentRound = Math.max(maxAssessmentRound, parseRound(node));
                }
            } else if ("NODE_COMPLETED".equals(type) || "NODE_FAILED".equals(type)) {
                if (node == null || !open.containsKey(node)) {
                    return false;
                }
                open.remove(node);
                if ("NODE_COMPLETED".equals(type)) {
                    completed.add(node);
                }
            } else if ("TERMINATED".equals(type)) {
                terminalCount++;
            }
        }
        ResearchRuntimeCheckpoint checkpoint = snapshot.getCheckpoint();
        if (checkpoint != null) {
            if (actionStarts != checkpoint.getConsumedActions()) {
                issues.add("ACTION_BUDGET_MISMATCH");
            }
            if (maxAssessmentRound != checkpoint.getIteration()) {
                issues.add("ITERATION_TRACE_MISMATCH");
            }
            if (checkpoint.isTerminal() && terminalCount != 1) {
                return false;
            }
            if ("TERMINATED".equals(checkpoint.getStatus())
                    && (checkpoint.getTerminationReason() == null
                    || "COMPLETED".equals(checkpoint.getTerminationReason()))) {
                return false;
            }
            if ("COMPLETED".equals(checkpoint.getStatus())
                    && !"COMPLETED".equals(checkpoint.getTerminationReason())) {
                return false;
            }
        }
        if (snapshot.getRun() != null && ResearchEnums.RUN_STATUS_COMPLETED.equals(snapshot.getRun().getStatus())) {
            String[] required = {"plan_sources", "classify_events", "extract_evidence", "compose_report", "verify_output"};
            for (String node : required) {
                if (!completed.contains(node)) {
                    return false;
                }
            }
        }
        return true;
    }

    private int parseRound(String node) {
        try {
            return Integer.parseInt(node.substring(node.indexOf(':') + 1));
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private boolean hasDanglingStartedNode(List<ResearchRuntimeEvent> events) {
        Map<String, Integer> open = new HashMap<String, Integer>();
        for (ResearchRuntimeEvent event : events) {
            String node = event.getNodeId();
            if (node == null) {
                continue;
            }
            if ("NODE_STARTED".equals(event.getEventType())) {
                open.put(node, open.containsKey(node) ? open.get(node) + 1 : 1);
            } else if ("NODE_COMPLETED".equals(event.getEventType()) || "NODE_FAILED".equals(event.getEventType())) {
                int count = open.containsKey(node) ? open.get(node) : 0;
                if (count > 1) {
                    open.put(node, count - 1);
                } else {
                    open.remove(node);
                }
            }
        }
        return !open.isEmpty();
    }

    private boolean hasEvent(List<ResearchRuntimeEvent> events, String type, String node) {
        for (ResearchRuntimeEvent event : events) {
            if (type.equals(event.getEventType()) && (node == null || node.equals(event.getNodeId()))) {
                return true;
            }
        }
        return false;
    }

    private ResearchEvaluationMetric metric(String code, String label, int score, int max,
                                            String evidence, String recommendation) {
        ResearchEvaluationMetric metric = new ResearchEvaluationMetric();
        metric.setMetricCode(code);
        metric.setLabel(label);
        metric.setScore(score);
        metric.setMaxScore(max);
        metric.setStatus(score == max ? "PASS" : score == 0 ? "FAIL" : "WARN");
        metric.setEvidence(evidence);
        metric.setRecommendation(recommendation);
        return metric;
    }

    private String safe(String value) {
        return value == null ? "missing" : value;
    }

    private String decimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
