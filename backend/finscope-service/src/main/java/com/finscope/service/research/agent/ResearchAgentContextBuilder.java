package com.finscope.service.research.agent;

import com.finscope.dao.research.agent.ResearchAgentRepository;
import com.finscope.dao.research.mission.ResearchMissionRepository;
import com.finscope.dao.research.runtime.ResearchRuntimeRepository;
import com.finscope.domain.research.agent.ResearchAgentDecision;
import com.finscope.domain.research.agent.ResearchAgentState;
import com.finscope.domain.research.agent.ResearchAgentTraceView;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.ResearchMode;
import com.finscope.domain.research.mission.ResearchMission;
import com.finscope.domain.research.mission.ResearchMissionGap;
import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.domain.research.mission.ResearchToolDescriptor;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.service.research.mission.ResearchToolRegistry;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResearchAgentContextBuilder {
    public static final int MAX_PROMPT_CHARACTERS = 24_000;
    private static final int RECENT_PAIRS = 4;

    private final ResearchMissionRepository missionRepository;
    private final ResearchAgentRepository agentRepository;
    private final ResearchRuntimeRepository runtimeRepository;
    private final ResearchToolRegistry toolRegistry;

    public ResearchAgentContextBuilder(ResearchMissionRepository missionRepository,
                                       ResearchAgentRepository agentRepository,
                                       ResearchRuntimeRepository runtimeRepository,
                                       ResearchToolRegistry toolRegistry) {
        this.missionRepository = missionRepository;
        this.agentRepository = agentRepository;
        this.runtimeRepository = runtimeRepository;
        this.toolRegistry = toolRegistry;
    }

    public ResearchDecisionContext build(Long runId) {
        ResearchMission mission = missionRepository.findMission(runId)
                .orElseThrow(() -> new IllegalStateException("研究 Mission 不存在：" + runId));
        ResearchAgentState state = agentRepository.findState(runId)
                .orElseThrow(() -> new IllegalStateException("研究 Agent 状态不存在：" + runId));
        ResearchRuntimeCheckpoint runtime = runtimeRepository.findCheckpoint(runId)
                .orElseThrow(() -> new IllegalStateException("研究 Runtime 不存在：" + runId));
        List<ResearchMissionGap> gaps = missionRepository.findGaps(runId);
        List<ResearchMissionTask> tasks = missionRepository.findTasks(runId);
        ResearchMissionGap latestGap = gaps.isEmpty() ? null : gaps.get(gaps.size() - 1);
        ResearchAgentTraceView trace = agentRepository.findTrace(runId);

        ResearchDecisionContext context = new ResearchDecisionContext();
        context.setResearchRunId(runId);
        context.setNextIteration(state.getDecisionCount() + 1);
        int runtimeRemaining = Math.max(0, runtime.getMaxActions() - runtime.getConsumedActions());
        int searchBudget = runtime.getMaxActions() <= ResearchMode.QUICK.getMaxIterations()
                ? ResearchMode.QUICK.getSearchActionBudget()
                : ResearchMode.DEEP.getSearchActionBudget();
        int searchRemaining = Math.max(0, searchBudget - completedSearchActions(trace));
        context.setRemainingActions(Math.min(runtimeRemaining, searchRemaining));
        context.setMission(mission);
        context.setState(state);
        context.setLatestGap(latestGap);
        context.setAttemptedFingerprints(state.getAttemptedFingerprints());
        context.setPrompt(buildPrompt(mission, tasks, state, latestGap, trace, context.getRemainingActions()));
        return context;
    }

    private String buildPrompt(ResearchMission mission,
                               List<ResearchMissionTask> tasks,
                               ResearchAgentState state,
                               ResearchMissionGap gap,
                               ResearchAgentTraceView trace,
                               int remainingActions) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("研究合同\n")
                .append("目标：").append(safe(mission.getGoal())).append('\n')
                .append("对象：").append(safe(mission.getSubject())).append('\n')
                .append("范围：").append(safe(mission.getScopeSummary())).append('\n')
                .append("成功条件：").append(mission.getSuccessCriteria()).append('\n')
                .append("计划版本：").append(mission.getPlanVersion()).append('\n')
                .append("当前子目标：").append(safe(state.getCurrentSubgoal())).append('\n')
                .append("计划摘要：").append(safe(state.getPlanSummary())).append('\n')
                .append("剩余搜索动作：").append(remainingActions).append('\n')
                .append("工作记忆：").append(limit(safe(state.getMemorySummary()), 8_000)).append('\n')
                .append("证据摘要：").append(safe(state.getEvidenceSummary())).append('\n')
                .append("已尝试动作：").append(state.getAttemptedFingerprints()).append('\n');
        appendGap(prompt, gap);
        appendTasks(prompt, tasks);
        appendTrace(prompt, trace);
        prompt.append("可用工具\n");
        for (ResearchToolDescriptor tool : toolRegistry.list()) {
            if (!"public_news_search".equals(tool.getCode())
                    && !"research_material_search".equals(tool.getCode())
                    && !"evidence_assess".equals(tool.getCode())) {
                continue;
            }
            prompt.append("- ").append(tool.getCode()).append("：")
                    .append(tool.getDescription()).append("；input=")
                    .append(tool.getInputSchema()).append('\n');
        }
        prompt.append("请选择一个下一步决策。不得重复已尝试动作。只输出决策 JSON。");
        return limit(prompt.toString(), MAX_PROMPT_CHARACTERS);
    }

    private void appendTasks(StringBuilder prompt, List<ResearchMissionTask> tasks) {
        prompt.append("计划任务\n");
        if (tasks == null) return;
        for (ResearchMissionTask task : tasks) {
            if ("COMPLETED".equals(task.getStatus()) || "SKIPPED".equals(task.getStatus())) continue;
            prompt.append("- ").append(safe(task.getTaskKey())).append("[")
                    .append(safe(task.getStatus())).append("] ")
                    .append(safe(task.getTitle())).append("；tool=")
                    .append(safe(task.getToolCode())).append("；intent=")
                    .append(safe(task.getIntent())).append("；query=")
                    .append(limit(safe(task.getQueryText()), 240)).append('\n');
        }
    }

    private void appendGap(StringBuilder prompt, ResearchMissionGap gap) {
        if (gap == null) {
            prompt.append("最新证据缺口：尚未评估\n");
            return;
        }
        prompt.append("最新证据缺口：evidence=").append(gap.getEvidenceCount())
                .append(", sources=").append(gap.getSourceCount())
                .append(", support=").append(gap.getSupportCount())
                .append(", counter=").append(gap.getCounterCount())
                .append(", sufficient=").append(gap.isSufficient())
                .append(", recommendedIntent=").append(gap.getRecommendedIntent())
                .append(", warnings=").append(gap.getWarnings()).append('\n');
    }

    private void appendTrace(StringBuilder prompt, ResearchAgentTraceView trace) {
        List<ResearchAgentDecision> decisions = trace == null
                ? Collections.<ResearchAgentDecision>emptyList() : trace.getDecisions();
        List<ResearchToolObservation> observations = trace == null
                ? Collections.<ResearchToolObservation>emptyList() : trace.getObservations();
        Map<Long, ResearchToolObservation> byDecision = new HashMap<Long, ResearchToolObservation>();
        for (ResearchToolObservation observation : observations) {
            byDecision.put(observation.getDecisionId(), observation);
        }
        int start = Math.max(0, decisions.size() - RECENT_PAIRS);
        prompt.append("最近决策与观察\n");
        for (int index = start; index < decisions.size(); index++) {
            ResearchAgentDecision decision = decisions.get(index);
            prompt.append("- #").append(decision.getIteration()).append(' ')
                    .append(decision.getDecisionType()).append(' ')
                    .append(safe(decision.getToolCode())).append("：")
                    .append(limit(safe(decision.getDecisionSummary()), 800)).append('\n');
            ResearchToolObservation observation = byDecision.get(decision.getId());
            if (observation != null) {
                prompt.append("  Observation[").append(observation.getStatus()).append("]：")
                        .append(limit(safe(observation.getObservationSummary()), 800))
                        .append("；evidenceDelta=").append(observation.getEvidenceDelta())
                        .append("；sourceDelta=").append(observation.getSourceDelta()).append('\n');
            }
        }
    }

    private int completedSearchActions(ResearchAgentTraceView trace) {
        int count = 0;
        if (trace == null) return count;
        for (ResearchAgentDecision decision : trace.getDecisions()) {
            if (isExternalTool(decision.getToolCode())
                    && ("COMPLETED".equals(decision.getStatus()) || "FAILED".equals(decision.getStatus()))) {
                count++;
            }
        }
        return count;
    }

    private boolean isExternalTool(String toolCode) {
        return "public_news_search".equals(toolCode) || "research_material_search".equals(toolCode);
    }

    private String safe(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim(); }
    private String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
