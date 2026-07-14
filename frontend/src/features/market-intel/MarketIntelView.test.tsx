import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { MarketIntelView } from './MarketIntelView';

vi.mock('../../shared/api/client', () => ({
  api: vi.fn()
}));

const overview = {
  instrument: { id: 7, code: '600519', type: 'STOCK', name: '贵州茅台', market: 'SH' },
  snapshot: { id: 12, instrumentId: 7, asOf: '2026-07-14T15:00:00', fingerprint: 'snapshot-12', signals: [] },
  intradayTimeline: [
    { id: 101, observedAt: '2026-07-14T10:30:00', price: 1501, tradeVolume: 81000, intervalTradeAmount: 120000000, mainNetInflow: 18000000, mainNetInflowSharePct: 15, turnoverRate: 3.21, volumeRatio: 1.67 }
  ],
  dailyTrend: [
    { id: 98, observedAt: '2026-07-13T15:00:00', price: 1488, intervalTradeAmount: 100000000, mainNetInflow: 20000000, turnoverRate: 2.8 },
    { id: 102, observedAt: '2026-07-14T15:00:00', price: 1501, tradeVolume: 1210000, intervalTradeAmount: 180000000, mainNetInflow: -30000000, mainNetInflowSharePct: -16.67, turnoverRate: 3.21, volumeRatio: 1.67 }
  ],
  metrics: {
    latest: { tradeAmount: 180000000, tradeVolume: 1210000, turnoverRate: 3.21, volumeRatio: 1.67, mainNetInflow: -30000000, mainNetInflowSharePct: -16.67 },
    intradayStreak: { direction: 'INFLOW', periods: 3, granularity: 'MINUTE_5', since: '2026-07-14T10:20:00', through: '2026-07-14T10:30:00' },
    dailyStreak: { direction: 'OUTFLOW', periods: 2, granularity: 'DAY_1', since: '2026-07-13T15:00:00', through: '2026-07-14T15:00:00' },
    objectiveTags: [{ code: 'AMOUNT_EXPANSION_WITH_OUTFLOW', label: '放量净流出', explanation: '成交额明显放大，同时主力净流向为负。', window: '2d', version: 'capital-signal-v1', metricRefs: ['flow:102:intervalTradeAmount', 'flow:102:mainNetInflow'] }]
  },
  ruleExplanation: {
    summary: '成交额明显放大，但主力净流向转负，短线承接需要继续观察。',
    ruleVersion: 'capital-rules-v1',
    items: [{ level: 'WATCH', text: '量能放大但资金转弱', metricRefs: ['flow:102:mainNetInflow'] }],
    dataGaps: ['缺少逐笔成交和委托队列，不能确认拆单。']
  },
  health: { status: 'FRESH', asOf: '2026-07-14T15:00:00', providerCode: 'EASTMONEY', warnings: [] }
};

beforeEach(() => {
  vi.mocked(api).mockReset();
  vi.mocked(api).mockImplementation((path: string, options?: RequestInit) => {
    if (path === '/api/market-intel/instruments') {
      return Promise.resolve([overview.instrument]) as never;
    }
    if (path.includes('/capital-behavior')) {
      return Promise.resolve(overview) as never;
    }
    if (path.endsWith('/refresh') && options?.method === 'POST') {
      return Promise.resolve({ id: 55, instrumentId: 7, status: 'PENDING', successCount: 0, failureCount: 0 }) as never;
    }
    if (path === '/api/market-intel/refresh-runs/55') {
      return Promise.resolve({ id: 55, instrumentId: 7, status: 'SUCCEEDED', successCount: 1, failureCount: 0 }) as never;
    }
    if (path.includes('/capital-interpretations') && options?.method === 'POST') {
      return Promise.resolve({
        id: 33,
        status: 'SUCCEEDED',
        interpretationType: 'AGENT',
        plainSummary: '目前只能确认量价资金出现背离，拆单仍是假设。',
        facts: ['当日成交额 1.80 亿', '主力净流出 3000 万'],
        hypotheses: [{
          type: 'ORDER_SPLITTING',
          claim: '可能存在拆单成交',
          confidence: 'LOW',
          supportingMetricRefs: ['flow:102:mainNetInflow'],
          counterEvidence: ['缺少逐笔明细'],
          dataGaps: ['缺少 Level-2']
        }],
        dataGaps: ['缺少 Level-2'],
        observationPoints: ['观察尾盘资金是否继续流出'],
        disclaimer: '不构成投资建议'
      }) as never;
    }
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });
});

test('shows deterministic explanation before the user requests agent analysis', async () => {
  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '贵州茅台' })).toBeInTheDocument();
  expect(await screen.findByText('成交额明显放大，但主力净流向转负，短线承接需要继续观察。')).toBeInTheDocument();
  expect(screen.getByText('量能放大但资金转弱')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '资金证据带' })).toBeInTheDocument();
  expect(screen.getByText('成交量')).toBeInTheDocument();
  expect(screen.getByText('121.00 万手')).toBeInTheDocument();
  expect(screen.getByText('量比')).toBeInTheDocument();
  expect(screen.getByText('1.67')).toBeInTheDocument();
  expect(screen.getByText('主力净额占比')).toBeInTheDocument();
  expect(screen.getAllByText('-16.67%').length).toBeGreaterThan(0);
  expect(screen.getByText('连续净流入 3 个 5 分钟区间')).toBeInTheDocument();
  expect(screen.getByText('连续净流出 2 个交易日')).toBeInTheDocument();
  expect(screen.getByText('放量净流出')).toBeInTheDocument();
  expect(api).not.toHaveBeenCalledWith(expect.stringContaining('capital-interpretations'), expect.anything());
});

test('runs agent analysis only after click and labels constrained hypotheses', async () => {
  const user = userEvent.setup();
  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  await user.click(await screen.findByRole('button', { name: '运行 Agent 解读' }));

  await waitFor(() => expect(api).toHaveBeenCalledWith(
    '/api/market-intel/instruments/7/capital-interpretations',
    { method: 'POST' }
  ));
  expect(await screen.findByText('目前只能确认量价资金出现背离，拆单仍是假设。')).toBeInTheDocument();
  expect(screen.getByText('低置信度')).toBeInTheDocument();
  expect(screen.getByText('缺少逐笔明细')).toBeInTheDocument();
});

test('polls the refresh run before reloading the capital snapshot', async () => {
  const user = userEvent.setup();
  const addToast = vi.fn();
  render(<MarketIntelView addToast={addToast} setMessage={vi.fn()} />);

  await user.click(await screen.findByRole('button', { name: '刷新资金数据' }));

  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/market-intel/refresh-runs/55'));
  await waitFor(() => expect(addToast).toHaveBeenCalledWith('资金数据已刷新', 'success'));
  const overviewCalls = vi.mocked(api).mock.calls.filter(([path]) => String(path).includes('/capital-behavior'));
  expect(overviewCalls.length).toBeGreaterThanOrEqual(2);
});

test('shows the provider error when refresh fails', async () => {
  vi.mocked(api).mockImplementation((path: string, options?: RequestInit) => {
    if (path === '/api/market-intel/instruments') return Promise.resolve([overview.instrument]) as never;
    if (path.includes('/capital-behavior')) return Promise.resolve(overview) as never;
    if (path.endsWith('/refresh') && options?.method === 'POST') {
      return Promise.resolve({ id: 56, instrumentId: 7, status: 'PENDING', successCount: 0, failureCount: 0 }) as never;
    }
    if (path === '/api/market-intel/refresh-runs/56') {
      return Promise.resolve({
        id: 56,
        instrumentId: 7,
        status: 'FAILED',
        successCount: 0,
        failureCount: 1,
        errorType: 'ALL_FUND_FLOW_SOURCES_FAILED',
        errorMessage: '东财资金流接口暂不可用，请稍后重试'
      }) as never;
    }
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });
  const user = userEvent.setup();
  const addToast = vi.fn();
  render(<MarketIntelView addToast={addToast} setMessage={vi.fn()} />);

  await user.click(await screen.findByRole('button', { name: '刷新资金数据' }));

  await waitFor(() => expect(addToast).toHaveBeenCalledWith('东财资金流接口暂不可用，请稍后重试', 'error'));
});

test('shows the concrete provider warning when refresh is partial', async () => {
  vi.mocked(api).mockImplementation((path: string, options?: RequestInit) => {
    if (path === '/api/market-intel/instruments') return Promise.resolve([overview.instrument]) as never;
    if (path.includes('/capital-behavior')) return Promise.resolve(overview) as never;
    if (path.endsWith('/refresh') && options?.method === 'POST') {
      return Promise.resolve({ id: 57, instrumentId: 7, status: 'PENDING', successCount: 0, failureCount: 0 }) as never;
    }
    if (path === '/api/market-intel/refresh-runs/57') {
      return Promise.resolve({
        id: 57,
        instrumentId: 7,
        status: 'PARTIAL',
        successCount: 1,
        failureCount: 0,
        errorType: 'PARTIAL_DATA',
        errorMessage: '实时行情接口暂不可用'
      }) as never;
    }
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });
  const user = userEvent.setup();
  const addToast = vi.fn();
  render(<MarketIntelView addToast={addToast} setMessage={vi.fn()} />);

  await user.click(await screen.findByRole('button', { name: '刷新资金数据' }));

  await waitFor(() => expect(addToast).toHaveBeenCalledWith('实时行情接口暂不可用', 'info'));
});

test('surfaces incomplete snapshot warnings in the health summary', async () => {
  vi.mocked(api).mockImplementation((path: string) => {
    if (path === '/api/market-intel/instruments') return Promise.resolve([overview.instrument]) as never;
    if (path.includes('/capital-behavior')) {
      return Promise.resolve({
        ...overview,
        health: { status: 'INCOMPLETE', asOf: '2026-07-14T15:00:00', providerCode: 'EASTMONEY', warnings: ['成交额、成交量、换手率或量比尚未补齐'] }
      }) as never;
    }
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });

  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('成交额、成交量、换手率或量比尚未补齐')).toBeInTheDocument();
  expect(screen.getByText('INCOMPLETE')).toBeInTheDocument();
});

test('treats an instrument without a snapshot as a first-run state instead of an error', async () => {
  vi.mocked(api).mockImplementation((path: string) => {
    if (path === '/api/market-intel/instruments') return Promise.resolve([overview.instrument]) as never;
    if (path.includes('/capital-behavior')) {
      return Promise.resolve({
        instrument: overview.instrument,
        snapshot: null,
        intradayTimeline: [],
        dailyTrend: [],
        ruleExplanation: null,
        health: { status: 'EMPTY', asOf: null, providerCode: '', warnings: [] }
      }) as never;
    }
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });

  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('button', { name: '生成第一份资金快照' })).toBeInTheDocument();
  expect(screen.getByText('这个标的还没有资金快照')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
