import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { FinancialsView } from './FinancialsView';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const instrument = { id: 7, code: '600519', name: '贵州茅台', type: 'STOCK', market: 'SH' };
const report = {
  id: 9,
  instrumentId: 7,
  periodEnd: '2025-12-31',
  reportType: 'ANNUAL',
  scope: 'CONSOLIDATED',
  currency: 'CNY',
  audited: true,
  qualityStatus: 'FRESH',
  sourceCode: 'AKSHARE_EASTMONEY'
};
const reportView = {
  instrument,
  report,
  statements: {
    INCOME: [
      line(1, 'INCOME', '营业总收入', 'REVENUE', 123456789000),
      line(2, 'INCOME', '归属于母公司股东的净利润', 'NET_PROFIT_PARENT', 45678900000),
      {
        ...line(5, 'INCOME', '营业总收入（单季）', 'REVENUE', 600000000),
        periodRole: 'CURRENT_QUARTER',
        valueOrigin: 'DERIVED'
      }
    ],
    BALANCE_SHEET: [
      line(3, 'BALANCE_SHEET', '资产总计', 'TOTAL_ASSETS', 300000000000)
    ],
    CASH_FLOW: [
      line(4, 'CASH_FLOW', '经营活动产生的现金流量净额', 'OPERATING_CASH_FLOW', 50000000000)
    ]
  },
  metrics: [
    { id: 1, reportId: 9, metricCode: 'GROSS_MARGIN', label: '毛利率', value: 91.2, unit: '%', qualityStatus: 'FRESH' }
  ],
  findings: [
    {
      id: 1,
      reportId: 9,
      ruleCode: 'PROFIT_CASH_DIVERGENCE',
      severity: 'HIGH',
      direction: 'RISK',
      title: '利润与经营现金流背离',
      explanation: '经营现金流不足净利润的 50%。',
      limitations: '规则只描述可复算现象。'
    }
  ],
  dataGaps: ['缺少上年同期营业收入，无法计算营收同比']
};

beforeEach(() => {
  vi.mocked(api).mockReset();
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (path === '/api/financials/instruments') return [instrument];
    if (path === '/api/financials/instruments/7/reports') return [report];
    if (path === '/api/financials/reports/9') return reportView;
    if (path === '/api/financials/reports/9/documents') return [];
    throw new Error(`unexpected api call: ${path}`);
  });
});

test('opens the latest report as a three-statement analysis workbench', async () => {
  render(<FinancialsView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '贵州茅台财报底稿' })).toBeInTheDocument();
  expect(await screen.findByText('营业总收入')).toBeInTheDocument();
  expect(screen.getAllByText('利润表').length).toBeGreaterThan(0);
  expect(screen.getAllByText('资产负债表').length).toBeGreaterThan(0);
  expect(screen.getAllByText('现金流量表').length).toBeGreaterThan(0);
  expect(screen.getByText('1,234.57亿')).toBeInTheDocument();
  expect(screen.getByText('毛利率')).toBeInTheDocument();
  expect(screen.getByText('91.20%')).toBeInTheDocument();
  expect(screen.getByText('利润与经营现金流背离')).toBeInTheDocument();
  expect(screen.getByText('缺少上年同期营业收入，无法计算营收同比')).toBeInTheDocument();
});

test('switches between all three concrete statements without losing the report context', async () => {
  const user = userEvent.setup();
  render(<FinancialsView addToast={vi.fn()} setMessage={vi.fn()} />);
  await screen.findByText('营业总收入');

  await user.click(screen.getByRole('tab', { name: '资产负债表' }));
  expect(screen.getByText('资产总计')).toBeInTheDocument();
  expect(screen.queryByText('营业总收入')).not.toBeInTheDocument();

  await user.click(screen.getByRole('tab', { name: '现金流量表' }));
  expect(screen.getByText('经营活动产生的现金流量净额')).toBeInTheDocument();
  expect(screen.getAllByText('2025 年报').length).toBeGreaterThan(0);
});

test('switches cumulative flow statements to explicitly derived single-quarter values', async () => {
  const user = userEvent.setup();
  render(<FinancialsView addToast={vi.fn()} setMessage={vi.fn()} />);
  await screen.findByText('营业总收入');

  await user.click(screen.getByRole('tab', { name: '利润表' }));
  expect(screen.getByText('1,234.57亿')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '单季度' }));

  expect(screen.getByText('营业总收入（单季）')).toBeInTheDocument();
  expect(screen.getByText('6.00亿')).toBeInTheDocument();
  expect(screen.getByText('单季派生')).toBeInTheDocument();
  expect(screen.queryByText('1,234.57亿')).not.toBeInTheDocument();
});

test('fetches a selected reporting period when the local archive is empty', async () => {
  const user = userEvent.setup();
  const addToast = vi.fn();
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path === '/api/financials/instruments') return [instrument];
    if (path === '/api/financials/instruments/7/reports') return [];
    if (path === '/api/financials/instruments/7/refresh' && options?.method === 'POST') return reportView;
    if (path === '/api/financials/reports/9/documents') return [];
    throw new Error(`unexpected api call: ${path}`);
  });

  render(<FinancialsView addToast={addToast} setMessage={vi.fn()} />);
  await user.clear(await screen.findByLabelText('报告期末'));
  await user.type(screen.getByLabelText('报告期末'), '2025-12-31');
  await user.selectOptions(screen.getByLabelText('报告类型'), 'ANNUAL');
  await user.click(screen.getByRole('button', { name: '抓取并解析财报' }));

  expect(await screen.findByText('营业总收入')).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/api/financials/instruments/7/refresh', {
    method: 'POST',
    body: JSON.stringify({ periodEnd: '2025-12-31', reportType: 'ANNUAL' })
  });
  expect(addToast).toHaveBeenCalledWith('2025 年报已抓取并完成分析', 'success');
});

test('uploads a PDF as report evidence using multipart form data', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path === '/api/financials/instruments') return [instrument];
    if (path === '/api/financials/instruments/7/reports') return [report];
    if (path === '/api/financials/reports/9') return reportView;
    if (path === '/api/financials/reports/9/documents') return [];
    if (path === '/api/financials/documents/upload' && options?.method === 'POST') {
      return { id: 11, reportId: 9, originalFileName: 'report.pdf', parseStatus: 'PARSED', pageCount: 1 };
    }
    throw new Error(`unexpected api call: ${path}`);
  });
  render(<FinancialsView addToast={vi.fn()} setMessage={vi.fn()} />);
  await screen.findByRole('heading', { name: '贵州茅台财报底稿' });

  await user.click(screen.getByRole('tab', { name: '原文凭证' }));
  const file = new File(['%PDF-1.4'], 'report.pdf', { type: 'application/pdf' });
  await user.upload(screen.getByLabelText('上传财报 PDF'), file);
  await user.click(screen.getByRole('button', { name: '上传并解析 PDF' }));

  await waitFor(() => expect(api).toHaveBeenCalledWith(
    '/api/financials/documents/upload',
    expect.objectContaining({ method: 'POST', body: expect.any(FormData) })
  ));
  expect(await screen.findByText('report.pdf')).toBeInTheDocument();
});

test('offers an evidence-constrained Agent interpretation for the selected report', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path === '/api/financials/instruments') return [instrument];
    if (path === '/api/financials/instruments/7/reports') return [report];
    if (path === '/api/financials/reports/9') return reportView;
    if (path === '/api/financials/reports/9/documents') return [];
    if (path === '/api/financials/reports/9/interpretations/latest') throw new Error('尚未生成');
    if (path === '/api/financials/reports/9/interpretations?limit=20') return [];
    if (path === '/api/financials/reports/9/interpretations' && options?.method === 'POST') {
      return interpretation;
    }
    if (path === '/api/financials/interpretations/41/evidence') return [
      {
        id: 'M_REVENUE_YOY',
        type: 'METRIC',
        label: '营业收入同比',
        value: '12.30%',
        period: '2025-12-31',
        detail: '营业收入同比增长率'
      }
    ];
    throw new Error(`unexpected api call: ${path}`);
  });

  render(<FinancialsView addToast={vi.fn()} setMessage={vi.fn()} />);
  await screen.findByText('营业总收入');
  await user.click(screen.getByRole('tab', { name: 'Agent 解读' }));

  expect(await screen.findByRole('button', { name: '生成 Agent 解读' })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '生成 Agent 解读' }));

  expect(await screen.findByText('经营状态改善，收入保持增长。')).toBeInTheDocument();
  expect(screen.getByText('核心变化')).toBeInTheDocument();
  expect(screen.getByText('营收同比延续增长。')).toBeInTheDocument();
  expect(screen.getByText('三表联动')).toBeInTheDocument();
  expect(screen.getByText('收入增长与现金回收需要结合观察。')).toBeInTheDocument();
  expect(screen.getByText('同比收入指标支持成长性判断。')).toBeInTheDocument();
  await user.click(screen.getAllByRole('button', { name: '营业收入同比证据' })[0]);
  expect(await screen.findByRole('dialog', { name: '证据详情' })).toHaveTextContent('12.30%');
});

test('keeps the previous successful interpretation visible when regeneration fails', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockImplementation(async (path: string, options?: RequestInit) => {
    if (path === '/api/financials/instruments') return [instrument];
    if (path === '/api/financials/instruments/7/reports') return [report];
    if (path === '/api/financials/reports/9') return reportView;
    if (path === '/api/financials/reports/9/documents') return [];
    if (path === '/api/financials/reports/9/interpretations/latest') return interpretation;
    if (path === '/api/financials/reports/9/interpretations?limit=20') return [interpretation];
    if (path === '/api/financials/reports/9/interpretations' && options?.method === 'POST') {
      return { ...interpretation, id: 42, status: 'QUEUED', result: undefined };
    }
    if (path === '/api/financials/interpretations/42') {
      return { ...interpretation, id: 42, status: 'FAILED', result: undefined, failureMessage: '模型服务暂不可用' };
    }
    throw new Error(`unexpected api call: ${path}`);
  });

  render(<FinancialsView addToast={vi.fn()} setMessage={vi.fn()} />);
  await screen.findByText('营业总收入');
  await user.click(screen.getByRole('tab', { name: 'Agent 解读' }));
  expect(await screen.findByText('经营状态改善，收入保持增长。')).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '重新生成' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('模型服务暂不可用');
  expect(screen.getByText('经营状态改善，收入保持增长。')).toBeInTheDocument();
});

const interpretation = {
  id: 41,
  reportId: 9,
  snapshotId: 21,
  promptVersion: 'financial-interpretation-v1',
  modelName: 'test-model',
  status: 'SUCCESS',
  generationMode: 'LLM',
  snapshotStale: false,
  createdAt: '2026-07-17T10:00:00',
  result: {
    operatingState: 'IMPROVING',
    confidence: 'HIGH',
    executiveSummary: [
      { claim: '经营状态改善，收入保持增长。', claimType: 'FACT', refs: ['M_REVENUE_YOY'] }
    ],
    periodChanges: [
      { claim: '营收同比延续增长。', claimType: 'FACT', refs: ['M_REVENUE_YOY'] }
    ],
    crossStatementInsights: [
      { claim: '收入增长与现金回收需要结合观察。', claimType: 'INFERENCE', refs: ['M_REVENUE_YOY'] }
    ],
    dimensions: [
      {
        code: 'GROWTH',
        assessment: 'POSITIVE',
        summary: '成长性改善。',
        refs: ['M_REVENUE_YOY'],
        details: [
          { claim: '同比收入指标支持成长性判断。', claimType: 'FACT', refs: ['M_REVENUE_YOY'] }
        ]
      }
    ],
    positiveSignals: [],
    risks: [],
    turningPoints: [],
    watchpoints: [],
    limitations: [],
    disclaimer: '仅用于研究，不构成投资建议。'
  }
};

function line(id: number, statementType: string, sourceLabel: string, conceptCode: string, normalizedValue: number) {
  return {
    id,
    reportId: 9,
    statementType,
    sourceLabel,
    conceptCode,
    periodRole: statementType === 'BALANCE_SHEET' ? 'CURRENT_PERIOD_END' : 'CURRENT_YTD',
    normalizedValue,
    currency: 'CNY',
    unitMultiplier: 1,
    valueOrigin: 'REPORTED',
    sourceCode: 'AKSHARE_EASTMONEY',
    displayOrder: id,
    qualityStatus: 'FRESH'
  };
}
