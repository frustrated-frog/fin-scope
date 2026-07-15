import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

import { CapitalAgentInterpretationPanel } from './CapitalAgentInterpretationPanel';

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-07-15T13:30:00'));
});

afterEach(() => {
  vi.useRealTimers();
});

test('shows elapsed time and increasingly explicit guidance while the Agent is busy', () => {
  render(
    <CapitalAgentInterpretationPanel
      interpretation={null}
      factorObservations={[]}
      watchConditions={[]}
      busy
      onRun={vi.fn()}
    />
  );

  expect(screen.getByRole('button', { name: 'Agent 解读中 · 0s' })).toBeDisabled();
  expect(screen.getByRole('status')).toHaveTextContent('正在整理因子与资金证据');

  act(() => { vi.advanceTimersByTime(12_000); });
  expect(screen.getByRole('button', { name: 'Agent 解读中 · 12s' })).toBeDisabled();
  expect(screen.getByRole('status')).toHaveTextContent('模型正在分析资金行为');

  act(() => { vi.advanceTimersByTime(19_000); });
  expect(screen.getByRole('button', { name: 'Agent 解读中 · 31s' })).toBeDisabled();
  expect(screen.getByRole('status')).toHaveTextContent('模型响应较慢，仍在继续');
  expect(screen.getByRole('status')).toHaveTextContent('超时后会自动展示规则解读');
});

test('adds breathing room before the pending verdict while analysis is running', () => {
  render(
    <CapitalAgentInterpretationPanel
      interpretation={{
        id: 60,
        status: 'PENDING',
        interpretationType: 'AGENT',
        plainSummary: '',
        facts: [],
        hypotheses: [],
        dataGaps: [],
        observationPoints: [],
        observations: [],
        counterEvidence: [],
        watchConditionRefs: [],
        evidenceRefs: [],
        rejectedOutputCount: 0,
        rejectionReasons: [],
        disclaimer: ''
      }}
      factorObservations={[]}
      watchConditions={[]}
      busy
      onRun={vi.fn()}
    />
  );

  expect(screen.getByText('资金行为待确认').closest('.market-intel-agent-report'))
    .toHaveClass('is-pending');
});

test('explains the concrete timeout budget when the Agent falls back to rules', () => {
  render(
    <CapitalAgentInterpretationPanel
      interpretation={{
        id: 59,
        status: 'FALLBACK',
        interpretationType: 'AGENT',
        plainSummary: '当前展示规则结果。',
        facts: [],
        hypotheses: [],
        dataGaps: [],
        observationPoints: [],
        observations: [],
        counterEvidence: [],
        watchConditionRefs: [],
        evidenceRefs: [],
        rejectedOutputCount: 0,
        rejectionReasons: [],
        disclaimer: '不构成投资建议',
        fallbackReason: 'LLM_TIMEOUT'
      }}
      factorObservations={[]}
      watchConditions={[]}
      busy={false}
      onRun={vi.fn()}
    />
  );

  expect(screen.getByText('模型在 60 秒内未完成，已自动展示规则解读。')).toBeInTheDocument();
});

test('renders the historical evaluation referenced by an Agent observation', () => {
  render(
    <CapitalAgentInterpretationPanel
      interpretation={{
        id: 61,
        status: 'SUCCEEDED',
        interpretationType: 'AGENT',
        plainSummary: '历史样本支持该观察。',
        marketState: 'MIXED',
        executiveSummary: '资金分化',
        facts: [],
        hypotheses: [],
        dataGaps: [],
        observationPoints: [],
        observations: [{
          dimension: 'FLOW',
          claim: '历史样本平均收益为1.25%',
          factorRefs: [],
          metricRefs: [],
          evaluationRefs: ['evaluation:capital-evaluation-v2:AMOUNT_EXPANSION_WITH_INFLOW:3d']
        }],
        counterEvidence: [],
        watchConditionRefs: [],
        confidence: 'MID',
        evidenceRefs: [],
        rejectedOutputCount: 0,
        rejectionReasons: [],
        disclaimer: '不构成投资建议'
      }}
      factorObservations={[]}
      historicalEvaluations={[{
        signalType: 'AMOUNT_EXPANSION_WITH_INFLOW',
        signalLabel: '放量流入',
        horizonDays: 3,
        sampleCount: 8,
        averageReturn: 0.0125,
        stabilityStatus: 'INSUFFICIENT_SAMPLE',
        evaluationStatus: 'EXPLORATORY'
      }]}
      watchConditions={[]}
      busy={false}
      onRun={vi.fn()}
    />
  );

  expect(screen.getByText('历史评价')).toBeInTheDocument();
  expect(screen.getByText('放量流入 · 3 日')).toBeInTheDocument();
  expect(screen.getByText('8 个样本 · 平均 +1.25%')).toBeInTheDocument();
});
