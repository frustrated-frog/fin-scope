import { describe, expect, it } from 'vitest';

import { AgentRun, ResearchRunDetail } from '../../shared/types';
import { presentAgentRun, presentResearchProgress } from './researchPresentation';

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
});

function step(stepId: string, status: string) {
  return { stepId, title: stepId, status };
}
