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

test('reads a detailed report with material limits, teaching, numeric evidence and chapter navigation', async () => {
  const report = detailed();
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (path.endsWith('interpretations?limit=20')) return [report];
    if (path.endsWith('interpretations/latest')) return report;
    if (path.endsWith('/42/evidence')) return [{ id: 'M_REVENUE_YOY', type: 'METRIC', label: '营业收入同比', value: '12.3', unit: '%', period: '2026-03-31' }];
    throw new Error(`unexpected api call: ${path}`);
  });
  render(<FinancialInterpretationPanel reportId={9} />);
  expect(await screen.findByRole('navigation', { name: '研究报告章节目录' })).toBeInTheDocument();
  expect(screen.getByText('3 份同口径报告')).toBeInTheDocument();
  expect(screen.getByText('尚未接入审计意见原文')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /本期业绩与历史趋势/ })).toHaveAttribute('href', expect.stringContaining('PERFORMANCE_TRENDS'));
  expect(screen.getByText('也可能受结算时点影响')).toBeInTheDocument();
  expect(screen.getByText('下一步核对回款与附注')).toBeInTheDocument();
  fireEvent.click(screen.getByText('通俗解释 · 怎么读这一章'));
  expect(screen.getByText('通俗解释 · 怎么读这一章').closest('details')).toHaveAttribute('open');
  expect(screen.getByText('同比是与上年同期比较')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '核查营业收入同比的数值' }));
  expect(screen.getByRole('dialog', { name: '证据详情' })).toHaveTextContent('12.30%');
  expect(screen.getByRole('dialog', { name: '证据详情' })).toHaveTextContent('2026-03-31');
});

test('keeps the previous successful report when regeneration fails and can read a historical legacy version', async () => {
  const report = detailed();
  const legacy = { ...completed(), id: 43, createdAt: '2026-07-17T22:51:00' };
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path.endsWith('interpretations?limit=20')) return [report, legacy];
    if (path.endsWith('interpretations/latest')) return report;
    if (path.endsWith('/evidence')) return [];
    if (options?.method === 'POST') return { ...pending('RUNNING'), id: 44, status: 'FAILED', failureMessage: '上游服务暂时不可用' };
    throw new Error(`unexpected api call: ${path}`);
  });
  render(<FinancialInterpretationPanel reportId={9} />);
  await screen.findByRole('navigation', { name: '研究报告章节目录' });
  fireEvent.click(screen.getByRole('button', { name: '重新生成' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('上游服务暂时不可用');
  expect(screen.getByRole('navigation', { name: '研究报告章节目录' })).toBeInTheDocument();
  fireEvent.change(screen.getByRole('combobox', { name: '历史解读版本' }), { target: { value: '43' } });
  expect(screen.queryByRole('navigation', { name: '研究报告章节目录' })).not.toBeInTheDocument();
  expect(screen.getByText('六维分析')).toBeInTheDocument();
  expect(screen.getByText(/这份历史解读使用旧版结构/)).toBeInTheDocument();
});

test('clearly identifies a partial rule report when the model is unavailable', async () => {
  const report = { ...detailed(), status: 'FALLBACK' as const, generationMode: 'DETERMINISTIC_FALLBACK' as const };
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (path.endsWith('interpretations?limit=20')) return [report];
    if (path.endsWith('interpretations/latest')) return report;
    if (path.endsWith('/evidence')) return [];
    throw new Error(`unexpected api call: ${path}`);
  });
  render(<FinancialInterpretationPanel reportId={9} />);
  expect(await screen.findByRole('status')).toHaveTextContent('本次模型解读未完成');
});

function detailed(): FinancialInterpretation {
  const report = completed();
  report.promptVersion = 'financial-interpret-v5';
  report.result = {
    ...report.result!,
    reportVersion: 'financial-interpret-v5',
    dimensions: [],
    reportScope: {
      companyName: '示例公司', market: 'SH', periodEnd: '2026-03-31', reportType: 'Q1', scope: 'CONSOLIDATED',
      currency: 'CNY', sourceCode: 'AKSHARE', historicalReportCount: 3, modelEvidenceCount: 80,
      comparablePeriods: ['2025-03-31'], materialLimitations: ['尚未接入审计意见原文']
    },
    sections: [{
      code: 'PERFORMANCE_TRENDS', title: '本期业绩与历史趋势', assessment: 'NEUTRAL', confidence: 'MEDIUM',
      summary: '收入增长，但原因需要继续核查', refs: ['M_REVENUE_YOY'],
      facts: [{ claim: '营业收入同比增长', claimType: 'FACT', confidence: 'HIGH', refs: ['M_REVENUE_YOY'] }],
      analysis: [{ claim: '收入变化需要结合业务资料理解', claimType: 'INFERENCE', confidence: 'MEDIUM', refs: ['M_REVENUE_YOY'] }],
      counterEvidence: [{ claim: '也可能受结算时点影响', claimType: 'INFERENCE', confidence: 'LOW', refs: ['M_REVENUE_YOY'] }],
      watchpoints: [{ claim: '下一步核对回款与附注', claimType: 'WATCHPOINT', confidence: 'MEDIUM', refs: ['M_REVENUE_YOY'] }],
      limitations: ['缺少销量和价格拆分'], learningExplanation: '同比是与上年同期比较', commonMisreading: '高增长不一定意味着长期趋势确立'
    }]
  };
  return report;
}
