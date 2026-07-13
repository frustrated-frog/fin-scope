import {
  AgentRun,
  ResearchRunDetail,
  ResearchRunPlanStep,
  ResearchThesis,
  ThesisFinding
} from '../../shared/types';

const STEP_PRESENTATION: Record<string, { label: string; active: string }> = {
  plan_sources: { label: '规划资料范围', active: '正在规划资料范围' },
  fetch_sources: { label: '获取研究资料', active: '正在获取研究资料' },
  classify_events: { label: '整理关键事件', active: '正在整理关键事件' },
  extract_evidence: { label: '筛选有效证据', active: '正在筛选有效证据' },
  compose_report: { label: '生成研究报告', active: '正在生成研究报告' },
  summarize_run: { label: '保存研究结果', active: '正在保存研究结果' }
};

const AGENT_LABELS: Record<string, string> = {
  'article-interpret': '理解文章',
  'evidence-extract': '提取证据',
  'event-classify': '归并事件',
  'report-synthesis': '生成报告',
  SourcePlanner: '规划来源',
  FetchService: '获取来源'
};

const FINISHED_STEP_STATUSES = new Set(['COMPLETED', 'SKIPPED']);

export type PresentedResearchStage = {
  id: string;
  label: string;
  status: string;
};

export type PresentedResearchProgress = {
  headline: string;
  currentLabel: string;
  completedSteps: number;
  totalSteps: number;
  percent: number;
  elapsedSeconds: number;
  metrics: string;
  stages: PresentedResearchStage[];
};

export type PresentedThesisStage = {
  label: string;
  tone: 'pending' | 'active' | 'settled';
  description: string;
};

export type PresentedFindingLane = {
  items: ThesisFinding[];
  total: number;
  remaining: number;
};

export type PresentedFindingGroups = Record<ThesisFinding['stance'], PresentedFindingLane>;

export function presentThesisStage(thesis: ResearchThesis, findingCount: number): PresentedThesisStage {
  if (thesis.status === 'CONCLUDED') {
    return { label: '已形成结论', tone: 'settled', description: '结论已经归档，可继续跟踪验证' };
  }
  if (thesis.conclusion?.trim()) {
    return { label: '持续验证', tone: 'active', description: '已有阶段性判断，等待新证据复核' };
  }
  if (findingCount > 0) {
    return { label: '证据积累中', tone: 'active', description: '已有初步发现，仍需验证关键变量' };
  }
  return { label: '待验证', tone: 'pending', description: '尚未形成有效证据，建议启动一次研究' };
}

export function presentConfidence(value?: ResearchThesis['confidence']) {
  if (value === 'HIGH') return '高置信';
  if (value === 'MEDIUM') return '中等置信';
  if (value === 'LOW') return '低置信';
  return '尚未评级';
}

export function groupThesisFindings(findings: ThesisFinding[], limit = 2): PresentedFindingGroups {
  return (['SUPPORT', 'COUNTER', 'UNKNOWN'] as const).reduce((groups, stance) => {
    const all = findings.filter((finding) => finding.stance === stance);
    groups[stance] = {
      items: all.slice(0, limit),
      total: all.length,
      remaining: Math.max(0, all.length - limit)
    };
    return groups;
  }, {} as PresentedFindingGroups);
}

export function summarizeResearchDiagnostics(detail: ResearchRunDetail) {
  const fetchedSources = detail.run.fetchedSourceCount ?? 0;
  const plannedSources = detail.plannedSources.length || detail.run.sourceCount || 0;
  const agentRuns = detail.agentRuns.length;
  return {
    fetchedSources,
    plannedSources,
    agentRuns,
    label: plannedSources
      ? `已获取 ${fetchedSources}/${plannedSources} 个来源 · 执行详情默认收起`
      : '暂无来源快照 · 执行详情默认收起'
  };
}

export function presentResearchProgress(detail: ResearchRunDetail, now = Date.now()): PresentedResearchProgress {
  const stages = (detail.planSteps || []).map((step) => ({
    id: step.stepId,
    label: presentStep(step).label,
    status: step.status
  }));
  const completedSteps = stages.filter((stage) => FINISHED_STEP_STATUSES.has(stage.status)).length;
  const runningStep = detail.planSteps.find((step) => step.status === 'RUNNING');
  const currentStep = runningStep || findLastStartedStep(detail.planSteps) || detail.planSteps[0];
  const currentPresentation = currentStep ? presentStep(currentStep) : { label: '准备研究', active: '正在准备研究' };
  const terminal = !['RUNNING', 'PENDING'].includes(detail.run.status);
  const headline = terminal
    ? detail.reportAvailable ? '研究报告已生成' : detail.run.status === 'FAILED' ? '研究运行未完成' : '研究运行已结束'
    : currentPresentation.active;
  const totalSteps = stages.length;

  return {
    headline,
    currentLabel: currentPresentation.label,
    completedSteps,
    totalSteps,
    percent: totalSteps ? Math.round((completedSteps / totalSteps) * 100) : 0,
    elapsedSeconds: elapsedSeconds(currentStep, now),
    metrics: [
      `${detail.run.fetchedSourceCount ?? 0}/${detail.run.sourceCount ?? 0} 个来源`,
      `${detail.run.articleCount ?? 0} 篇资料`,
      `${detail.run.evidenceCount ?? 0} 条候选证据`
    ].join(' · '),
    stages
  };
}

export function presentAgentRun(run: AgentRun): { label: string; summary: string } {
  return {
    label: AGENT_LABELS[run.nodeName] || humanizeIdentifier(run.nodeName),
    summary: boundText(extractAgentSummary(run), 160)
  };
}

function presentStep(step: ResearchRunPlanStep) {
  return STEP_PRESENTATION[step.stepId] || {
    label: step.title || humanizeIdentifier(step.stepId),
    active: `正在${step.title || humanizeIdentifier(step.stepId)}`
  };
}

function findLastStartedStep(steps: ResearchRunPlanStep[]) {
  return [...steps].reverse().find((step) => step.startedAt || FINISHED_STEP_STATUSES.has(step.status));
}

function elapsedSeconds(step: ResearchRunPlanStep | undefined, now: number) {
  if (!step?.startedAt) return 0;
  const startedAt = new Date(step.startedAt).getTime();
  const endedAt = step.endedAt ? new Date(step.endedAt).getTime() : now;
  if (!Number.isFinite(startedAt) || !Number.isFinite(endedAt)) return 0;
  return Math.max(0, Math.floor((endedAt - startedAt) / 1000));
}

function extractAgentSummary(run: AgentRun) {
  if (run.errorMessage) return run.errorMessage;
  if (!run.output) return run.fallbackReason || '该步骤未返回可展示摘要';
  try {
    const parsed = JSON.parse(run.output) as Record<string, unknown>;
    const candidate = parsed.oneSentenceSummary
      || parsed.coreEvent
      || parsed.summary
      || parsed.topicName
      || parsed.message;
    if (typeof candidate === 'string' && candidate.trim()) return candidate;
  } catch {
    // Non-JSON output is already a useful diagnostic summary.
  }
  return run.output;
}

function boundText(value: string, maxLength: number) {
  const normalized = value.replace(/\s+/g, ' ').trim();
  if (normalized.length <= maxLength) return normalized;
  return `${normalized.slice(0, maxLength - 1).trimEnd()}…`;
}

function humanizeIdentifier(value: string) {
  return value.replace(/[-_]+/g, ' ').trim() || '运行步骤';
}
