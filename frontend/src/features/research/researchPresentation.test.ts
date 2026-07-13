import { describe, expect, it } from 'vitest';

import { AgentRun, ResearchRunDetail, ResearchThesis, ThesisFinding } from '../../shared/types';
import {
  groupThesisFindings,
  presentAgentRun,
  presentConfidence,
  presentResearchProgress,
  presentThesisStage,
  summarizeResearchDiagnostics
} from './researchPresentation';

describe('researchPresentation', () => {
  it('projects the active plan step into a clear Chinese progress summary', () => {
    const detail: ResearchRunDetail = {
      run: {
        id: 16,
        thesisId: 1,
        runDate: '2026-07-13',
        themeCodes: [],
        sourceCount: 9,
        fetchedSourceCount: 8,
        articleCount: 56,
        eventCount: 36,
        evidenceCount: 56,
        status: 'RUNNING'
      },
      plannedSources: [],
      agentRuns: [],
      planSteps: [
        step('plan_sources', 'COMPLETED'),
        step('fetch_sources', 'COMPLETED'),
        step('classify_events', 'COMPLETED'),
        step('extract_evidence', 'COMPLETED'),
        { ...step('compose_report', 'RUNNING'), startedAt: '2026-07-13T23:29:45' },
        step('summarize_run', 'PENDING')
      ],
      reportAvailable: false,
      canRegenerateReport: false
    };

    const progress = presentResearchProgress(detail, new Date('2026-07-13T23:30:03').getTime());

    expect(progress.headline).toBe('正在生成研究报告');
    expect(progress.currentLabel).toBe('生成研究报告');
    expect(progress.completedSteps).toBe(4);
    expect(progress.totalSteps).toBe(6);
    expect(progress.elapsedSeconds).toBe(18);
    expect(progress.metrics).toContain('8/9 个来源');
    expect(progress.metrics).toContain('56 条候选证据');
  });

  it('turns raw agent JSON into a bounded Chinese diagnostic summary', () => {
    const run: AgentRun = {
      id: 1,
      nodeName: 'article-interpret',
      status: 'FALLBACK',
      durationMs: 12,
      output: JSON.stringify({
        oneSentenceSummary: '这是一条用户可以理解的文章摘要',
        rawJson: 'x'.repeat(1000)
      })
    };

    const presented = presentAgentRun(run);

    expect(presented.label).toBe('理解文章');
    expect(presented.summary).toBe('这是一条用户可以理解的文章摘要');
    expect(presented.summary.length).toBeLessThanOrEqual(160);
  });

  it('derives a user-facing thesis stage and bounds each finding lane', () => {
    const findings: ThesisFinding[] = [
      finding(1, 'SUPPORT', '支持一'),
      finding(2, 'SUPPORT', '支持二'),
      finding(3, 'SUPPORT', '支持三'),
      finding(4, 'COUNTER', '反证一')
    ];

    expect(presentThesisStage(thesis(), findings.length)).toEqual({
      label: '证据积累中',
      tone: 'active',
      description: '已有初步发现，仍需验证关键变量'
    });
    expect(presentConfidence('MEDIUM')).toBe('中等置信');
    expect(presentConfidence()).toBe('尚未评级');

    const grouped = groupThesisFindings(findings, 2);
    expect(grouped.SUPPORT.items.map((item) => item.summary)).toEqual(['支持一', '支持二']);
    expect(grouped.SUPPORT.total).toBe(3);
    expect(grouped.SUPPORT.remaining).toBe(1);
    expect(grouped.COUNTER.remaining).toBe(0);
    expect(grouped.UNKNOWN.items).toEqual([]);
  });

  it('summarizes research diagnostics without exposing the full source list', () => {
    const detail: ResearchRunDetail = {
      run: {
        id: 16,
        runDate: '2026-07-13',
        themeCodes: [],
        sourceCount: 9,
        fetchedSourceCount: 8,
        status: 'COMPLETED'
      },
      plannedSources: Array.from({ length: 9 }, (_, index) => ({ sourceName: `来源 ${index + 1}` })),
      planSteps: [],
      agentRuns: Array.from({ length: 3 }, (_, index) => ({
        id: index + 1,
        nodeName: 'article-interpret',
        status: 'COMPLETED',
        durationMs: 10
      })),
      reportAvailable: true,
      canRegenerateReport: true
    };

    expect(summarizeResearchDiagnostics(detail)).toEqual({
      fetchedSources: 8,
      plannedSources: 9,
      agentRuns: 3,
      label: '已获取 8/9 个来源 · 执行详情默认收起'
    });
  });
});

function step(stepId: string, status: string) {
  return { stepId, title: stepId, status };
}

function thesis(): ResearchThesis {
  return {
    id: 1,
    question: '科技板块冲高后近期大跌回落，周期是否还能持续',
    subjectType: 'INDUSTRY',
    subjectName: '半导体设备',
    status: 'OPEN'
  };
}

function finding(id: number, stance: ThesisFinding['stance'], summary: string): ThesisFinding {
  return { id, thesisId: 1, stance, summary };
}
