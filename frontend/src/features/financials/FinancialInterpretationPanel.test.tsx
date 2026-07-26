import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { FinancialInterpretationPanel } from './FinancialInterpretationPanel';
import { FinancialInterpretation } from './financialTypes';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

afterEach(() => {
  vi.useRealTimers();
  vi.mocked(api).mockReset();
});

test('continues polling while the interpretation remains in the same pending status', async () => {
  vi.useFakeTimers();
  let statusCalls = 0;
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path === '/api/financials/reports/9/interpretations?limit=20') return [];
    if (path === '/api/financials/reports/9/interpretations/latest') throw new Error('尚未生成');
    if (path === '/api/financials/reports/9/interpretations' && options?.method === 'POST') {
      return pending('QUEUED');
    }
    if (path === '/api/financials/interpretations/42') {
      statusCalls += 1;
      return statusCalls < 3 ? pending('RUNNING') : completed();
    }
    if (path === '/api/financials/interpretations/42/evidence') return [];
    throw new Error(`unexpected api call: ${path}`);
  });

  render(<FinancialInterpretationPanel reportId={9} />);
  await act(async () => { await Promise.resolve(); });
  fireEvent.click(screen.getByRole('button', { name: '生成 Agent 解读' }));
  await act(async () => { await Promise.resolve(); });

  await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
  await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });

  expect(statusCalls).toBe(3);
  expect(screen.getByText('模型生成的经营叙事')).toBeInTheDocument();
  expect(screen.getByText('核心变化')).toBeInTheDocument();
  expect(screen.getByText('营业收入延续增长趋势')).toBeInTheDocument();
  expect(screen.getByText('三表联动')).toBeInTheDocument();
  expect(screen.getByText('利润增长但经营现金流偏弱')).toBeInTheDocument();
  expect(screen.getByText('收入增长得到同比指标支持')).toBeInTheDocument();
});

test('resumes the latest pending task and retries after a transient status error', async () => {
  vi.useFakeTimers();
  let statusCalls = 0;
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (path === '/api/financials/reports/9/interpretations?limit=20') return [];
    if (path === '/api/financials/reports/9/interpretations/latest') return pending('RUNNING');
    if (path === '/api/financials/interpretations/42') {
      statusCalls += 1;
      if (statusCalls === 1) throw new Error('temporary network failure');
      return completed();
    }
    if (path === '/api/financials/interpretations/42/evidence') return [];
    throw new Error(`unexpected api call: ${path}`);
  });

  render(<FinancialInterpretationPanel reportId={9} />);
  await act(async () => { await Promise.resolve(); });
  expect(screen.getByText('正在组织经营叙事')).toBeInTheDocument();

  await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
  expect(screen.getByRole('alert')).toHaveTextContent('temporary network failure');
  await act(async () => { await vi.advanceTimersByTimeAsync(4_000); });

  expect(statusCalls).toBe(2);
  expect(screen.getByText('模型生成的经营叙事')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

function pending(status: 'QUEUED' | 'RUNNING'): FinancialInterpretation {
  return {
    id: 42,
    reportId: 9,
    snapshotId: 21,
    promptVersion: 'financial-interpretation-v1',
    modelName: 'GLM-5',
    status,
    snapshotStale: false
  };
}

function completed(): FinancialInterpretation {
  return {
    ...pending('RUNNING'),
    status: 'SUCCESS',
    generationMode: 'LLM',
    result: {
      operatingState: 'STABLE',
      confidence: 'MEDIUM',
      executiveSummary: [{ claim: '模型生成的经营叙事', claimType: 'FACT', refs: [] }],
      periodChanges: [
        { claim: '营业收入延续增长趋势', claimType: 'FACT', refs: ['M_REVENUE_YOY'] }
      ],
      crossStatementInsights: [
        { claim: '利润增长但经营现金流偏弱', claimType: 'INFERENCE', refs: ['M_REVENUE_YOY'] }
      ],
      dimensions: [{
        code: 'GROWTH',
        assessment: 'POSITIVE',
        summary: '成长趋势改善',
        refs: ['M_REVENUE_YOY'],
        details: [
          { claim: '收入增长得到同比指标支持', claimType: 'FACT', refs: ['M_REVENUE_YOY'] }
        ]
      }],
      positiveSignals: [],
      risks: [],
      turningPoints: [],
      watchpoints: [],
      limitations: [],
      disclaimer: '仅用于研究，不构成投资建议。'
    }
  };
}
