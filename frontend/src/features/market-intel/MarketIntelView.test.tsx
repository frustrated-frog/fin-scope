import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { MarketIntelView } from './MarketIntelView';

vi.mock('../../shared/api/client', () => ({
  api: vi.fn()
}));

const overview = {
  instrument: { id: 7, code: '600519', type: 'STOCK', name: '贵州茅台', market: 'SH' },
  snapshot: { id: 12, instrumentId: 7, asOf: '2026-07-14T15:00:00', fingerprint: 'snapshot-12', signals: [] },
  intradayTimeline: [
    { id: 99, observedAt: '2026-07-14T09:30:00', price: 1498, tradeVolume: 61000, intervalTradeAmount: 90000000, mainNetInflow: 12000000, mainNetInflowSharePct: 13.33, turnoverRate: 2.61, volumeRatio: 1.35 },
    { id: 100, observedAt: '2026-07-14T09:35:00', price: 1499, tradeVolume: 71000, intervalTradeAmount: 105000000, mainNetInflow: 15000000, mainNetInflowSharePct: 14.29, turnoverRate: 2.88, volumeRatio: 1.48 },
    { id: 101, observedAt: '2026-07-14T10:10:00', price: 1501, tradeVolume: 81000, intervalTradeAmount: 120000000, mainNetInflow: 18000000, mainNetInflowSharePct: 15, turnoverRate: 3.21, volumeRatio: 1.67 }
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
  factorObservations: [{
    factorRef: 'factor:PRICE_FLOW_ALIGNMENT:2026-07-14T15:00',
    factorCode: 'PRICE_FLOW_ALIGNMENT',
    label: '价格与资金协同',
    category: 'FLOW',
    observedAt: '2026-07-14T15:00:00',
    window: '2d',
    value: -1,
    state: 'DIVERGENT',
    sampleCount: 2,
    metricRefs: ['flow:102:mainNetInflow'],
    qualityStatus: 'COMPLETE',
    calculationVersion: 'capital-factor-v1',
    interpretationBoundary: '只描述价格和公开资金净额的方向关系。'
  }],
  watchConditions: [{
    id: 'watch:MAIN_FLOW_SHARE',
    label: '主力净额占比转正',
    factorRef: 'factor:MAIN_FLOW_SHARE:2026-07-14T15:00',
    operator: '>',
    threshold: 0,
    unit: '比例'
  }],
  factorVersion: 'capital-factor-v1',
  signalVersion: 'capital-signal-v2',
  health: { status: 'FRESH_PRIMARY', asOf: '2026-07-14T15:00:00', providerCode: 'EASTMONEY', warnings: [] }
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
        marketState: 'PRICE_FLOW_DIVERGENCE',
        executiveSummary: '目前只能确认量价资金出现背离，拆单仍是假设。',
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
        observations: [{
          dimension: 'FLOW',
          claim: '价格上涨但主力净额转负，形成价格与资金背离。',
          factorRefs: ['factor:PRICE_FLOW_ALIGNMENT:2026-07-14T15:00'],
          metricRefs: ['flow:102:mainNetInflow']
        }],
        counterEvidence: ['日内早盘曾连续净流入'],
        watchConditionRefs: ['watch:MAIN_FLOW_SHARE'],
        confidence: 'MID',
        factorVersion: 'capital-factor-v1',
        signalVersion: 'capital-signal-v2',
        evidenceRefs: [{
          ref: 'flow:102:mainNetInflow',
          label: '主力净额',
          category: 'FLOW',
          value: -30000000,
          unit: '元',
          observedAt: '2026-07-14T15:00:00'
        }],
        rejectedOutputCount: 1,
        rejectionReasons: ['观察项引用了未知因子'],
        disclaimer: '不构成投资建议'
      }) as never;
    }
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });
});

afterEach(() => {
  vi.restoreAllMocks();
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

test('shows the newest capital evidence first', async () => {
  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  const heading = await screen.findByRole('heading', { name: '资金证据带' });
  const evidenceTape = heading.closest('section');
  const displayedTimes = Array.from(evidenceTape?.querySelectorAll('ol time') ?? [])
    .map((time) => time.textContent);

  expect(displayedTimes).toEqual(['10:10', '09:35', '09:30']);
});

test('uses stable evidence row keys when aggregated points do not have database ids', async () => {
  const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
  vi.mocked(api).mockImplementation((path: string) => {
    if (path === '/api/market-intel/instruments') return Promise.resolve([overview.instrument]) as never;
    if (path.includes('/capital-behavior')) return Promise.resolve({
      ...overview,
      intradayTimeline: overview.intradayTimeline.map((point) => ({ ...point, id: null }))
    }) as never;
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });

  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);
  expect(await screen.findByRole('heading', { name: '资金证据带' })).toBeInTheDocument();

  expect(consoleError.mock.calls.flat().join(' ')).not.toContain('same key');
  consoleError.mockRestore();
});

test('shows the newest daily capital trend first', async () => {
  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  const heading = await screen.findByRole('heading', { name: '日线资金趋势' });
  const dailyTrend = heading.closest('section');
  const displayedDates = Array.from(dailyTrend?.querySelectorAll('ol time') ?? [])
    .map((time) => time.getAttribute('datetime'));

  expect(displayedDates).toEqual(['2026-07-14T15:00:00', '2026-07-13T15:00:00']);
});

test('runs agent analysis only after click and labels constrained hypotheses', async () => {
  const user = userEvent.setup();
  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  await user.click(await screen.findByRole('button', { name: '运行 Agent 解读' }));

  await waitFor(() => expect(api).toHaveBeenCalledWith(
    '/api/market-intel/instruments/7/capital-interpretations?force=true',
    { method: 'POST' }
  ));
  expect(await screen.findByText('目前只能确认量价资金出现背离，拆单仍是假设。')).toBeInTheDocument();
  expect(screen.getByText('模型解读')).toBeInTheDocument();
  expect(screen.getByText('价格与资金背离')).toBeInTheDocument();
  expect(screen.getByText('资金方向')).toBeInTheDocument();
  expect(screen.getByText('价格与资金协同')).toBeInTheDocument();
  expect(screen.getAllByText(/主力净额/).length).toBeGreaterThan(0);
  expect(screen.getAllByText(/-3000.00 万元/).length).toBeGreaterThan(0);
  expect(screen.getByText('主力净额占比转正')).toBeInTheDocument();
  expect(screen.getByText('1 项模型输出未通过证据门禁')).toBeInTheDocument();
  expect(screen.getByText('观察项引用了未知因子')).toBeInTheDocument();
  expect(screen.getByText('低置信度')).toBeInTheDocument();
  expect(screen.getByText('缺少逐笔明细')).toBeInTheDocument();
});

test('clearly labels rule fallback instead of presenting it as model analysis', async () => {
  vi.mocked(api).mockImplementation((path: string, options?: RequestInit) => {
    if (path === '/api/market-intel/instruments') return Promise.resolve([overview.instrument]) as never;
    if (path.includes('/capital-behavior')) return Promise.resolve(overview) as never;
    if (path.includes('/capital-interpretations') && options?.method === 'POST') {
      return Promise.resolve({
        id: 34,
        status: 'FALLBACK',
        interpretationType: 'AGENT',
        marketState: 'NEUTRAL',
        plainSummary: '模型超时，当前展示规则结果。',
        executiveSummary: '模型超时，当前展示规则结果。',
        facts: [],
        hypotheses: [],
        observations: [],
        counterEvidence: [],
        dataGaps: ['缺少 Level-2'],
        observationPoints: [],
        watchConditionRefs: [],
        evidenceRefs: [],
        rejectedOutputCount: 0,
        rejectionReasons: [],
        fallbackReason: 'LLM_TIMEOUT',
        disclaimer: '不构成投资建议'
      }) as never;
    }
    return Promise.reject(new Error('unexpected api call: ' + path));
  });
  const user = userEvent.setup();
  const addToast = vi.fn();
  render(<MarketIntelView addToast={addToast} setMessage={vi.fn()} />);

  await user.click(await screen.findByRole('button', { name: '运行 Agent 解读' }));

  expect(await screen.findByText('规则兜底')).toBeInTheDocument();
  expect(screen.getByText('模型在 60 秒内未完成，已自动展示规则解读。')).toBeInTheDocument();
  expect(addToast).toHaveBeenCalledWith('模型在 60 秒内未完成，已自动展示规则解读。', 'info');
});

test('clearly explains when factor coverage is insufficient', async () => {
  vi.mocked(api).mockImplementation((path: string, options?: RequestInit) => {
    if (path === '/api/market-intel/instruments') return Promise.resolve([overview.instrument]) as never;
    if (path.includes('/capital-behavior')) return Promise.resolve(overview) as never;
    if (path.includes('/capital-interpretations') && options?.method === 'POST') {
      return Promise.resolve({
        id: 35,
        status: 'INSUFFICIENT_DATA',
        interpretationType: 'AGENT',
        marketState: 'INSUFFICIENT_DATA',
        plainSummary: '有效因子维度不足。',
        executiveSummary: '有效因子维度不足。',
        facts: [],
        hypotheses: [],
        observations: [],
        counterEvidence: [],
        dataGaps: ['日线样本不足'],
        observationPoints: [],
        watchConditionRefs: [],
        evidenceRefs: [],
        rejectedOutputCount: 0,
        rejectionReasons: [],
        fallbackReason: 'INSUFFICIENT_FACTOR_COVERAGE',
        disclaimer: '不构成投资建议'
      }) as never;
    }
    return Promise.reject(new Error('unexpected api call: ' + path));
  });
  const user = userEvent.setup();
  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  await user.click(await screen.findByRole('button', { name: '运行 Agent 解读' }));

  expect(await screen.findByText('数据不足')).toBeInTheDocument();
  expect(screen.getByText('有效因子维度不足，未调用模型。')).toBeInTheDocument();
  expect(screen.getByText('日线样本不足')).toBeInTheDocument();
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
        health: { status: 'PARTIAL_FRESH', asOf: '2026-07-14T15:00:00', providerCode: 'EASTMONEY', warnings: ['成交额、成交量、换手率或量比尚未补齐'] }
      }) as never;
    }
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });

  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText(/成交额、成交量、换手率或量比尚未补齐/)).toBeInTheDocument();
  expect(screen.getByText('部分可用')).toBeInTheDocument();
});

test('deduplicates and translates provider diagnostics before showing them', async () => {
  vi.mocked(api).mockImplementation((path: string) => {
    if (path === '/api/market-intel/instruments') return Promise.resolve([overview.instrument]) as never;
    if (path.includes('/capital-behavior')) {
      return Promise.resolve({
        ...overview,
        health: {
          status: 'PARTIAL_FRESH',
          asOf: '2026-07-15T11:30:00',
          providerCode: 'EASTMONEY_CAPITAL_FLOW',
          warnings: [
            'DAILY_MARKET_UNAVAILABLE:CONNECTION_ERROR',
            'QUOTE_UNAVAILABLE:CONNECTION_ERROR',
            'QUOTE_UNAVAILABLE:CONNECTION_ERROR；DAILY_MARKET_UNAVAILABLE:CONNECTION_ERROR'
          ]
        }
      }) as never;
    }
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });

  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('东方财富资金流')).toBeInTheDocument();
  const notice = screen.getByText('本次刷新仅部分成功').closest('[role="status"]');
  expect(notice).toHaveTextContent('日线行情暂不可用（连接失败）');
  expect(notice).toHaveTextContent('实时报价暂不可用（连接失败）');
  expect(notice?.textContent?.match(/日线行情暂不可用/g)).toHaveLength(1);
  expect(notice?.textContent?.match(/实时报价暂不可用/g)).toHaveLength(1);
  expect(screen.queryByText(/DAILY_MARKET_UNAVAILABLE/)).not.toBeInTheDocument();
  expect(screen.queryByText(/QUOTE_UNAVAILABLE/)).not.toBeInTheDocument();
});

test('keeps polling long enough for the backend model timeout fallback', async () => {
  const originalSetTimeout = window.setTimeout.bind(window);
  const timer = vi.spyOn(window, 'setTimeout').mockImplementation((handler, timeout, ...args) => {
    if (timeout === 650) {
      queueMicrotask(() => {
        if (typeof handler === 'function') handler(...args);
      });
      return 1;
    }
    return originalSetTimeout(handler, timeout, ...args);
  });
  let polls = 0;
  const running = {
    id: 58,
    status: 'RUNNING',
    interpretationType: 'AGENT',
    plainSummary: 'Agent 正在分析',
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
    disclaimer: '不构成投资建议'
  };
  vi.mocked(api).mockImplementation((path: string, options?: RequestInit) => {
    if (path === '/api/market-intel/instruments') return Promise.resolve([overview.instrument]) as never;
    if (path.includes('/capital-behavior')) return Promise.resolve(overview) as never;
    if (path.includes('/capital-interpretations') && options?.method === 'POST') {
      return Promise.resolve(running) as never;
    }
    if (path === '/api/market-intel/capital-interpretations/58') {
      polls += 1;
      return Promise.resolve(polls <= 20 ? running : {
        ...running,
        status: 'FALLBACK',
        fallbackReason: 'LLM_TIMEOUT',
        plainSummary: '模型超时，当前展示规则结果。'
      }) as never;
    }
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });
  const addToast = vi.fn();
  const user = userEvent.setup({ delay: null });

  render(<MarketIntelView addToast={addToast} setMessage={vi.fn()} />);
  await user.click(await screen.findByRole('button', { name: '运行 Agent 解读' }));

  await waitFor(() => expect(addToast).toHaveBeenCalledWith(
    '模型在 60 秒内未完成，已自动展示规则解读。',
    'info'
  ));
  expect(polls).toBe(21);
  timer.mockRestore();
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
        health: { status: 'UNAVAILABLE', asOf: null, providerCode: '', warnings: [] }
      }) as never;
    }
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });

  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('button', { name: '生成第一份资金快照' })).toBeInTheDocument();
  expect(screen.getByText('这个标的还没有资金快照')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('shows an explicit alert when capital analysis uses a stale snapshot', async () => {
  vi.mocked(api).mockImplementation((path: string) => {
    if (path === '/api/market-intel/instruments') return Promise.resolve([overview.instrument]) as never;
    if (path.includes('/capital-behavior')) return Promise.resolve({
      ...overview,
      health: {
        status: 'STALE_FALLBACK',
        asOf: '2026-07-14T14:33:00',
        providerCode: 'LAST_GOOD_SNAPSHOT',
        warnings: ['在线资金源均不可用，当前展示最近一次成功快照']
      }
    }) as never;
    return Promise.reject(new Error(`unexpected api call: ${path}`));
  });

  render(<MarketIntelView addToast={vi.fn()} setMessage={vi.fn()} />);

  const alert = await screen.findByRole('alert');
  expect(alert).toHaveTextContent('当前展示的是旧数据');
  expect(alert).toHaveTextContent('在线资金源均不可用');
  expect(alert).toHaveTextContent('请勿视为实时行情');
});
