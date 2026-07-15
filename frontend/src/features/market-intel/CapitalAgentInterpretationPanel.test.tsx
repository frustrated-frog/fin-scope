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
