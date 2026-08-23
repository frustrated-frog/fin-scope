import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';

import { MarketPulseView } from './MarketPulseView';

const workspace = {
  businessDate: '2026-08-21',
  qualityStatus: 'PARTIAL',
  generatedAt: '2026-08-23T18:00:00',
  warnings: ['行业宽度未接入，评分已降低置信度'],
  dailyReview: {
    businessDate: '2026-08-21',
    headline: '急跌后缩量修复，反弹持续性仍需量能确认',
    indexOverview: '创业板指领涨（+1.43%），上证指数相对偏弱（+0.04%），指数风格分化明显',
    breadthConclusion: '上涨比例 63%，上涨扩散较强；涨跌中位数 +0.70%',
    leadingSectors: ['创新药：5日+5.80%，轮动分78，阶段持续'],
    weakeningSectors: ['半导体：1日+0.37%，5日-2.10%，阶段弱势'],
    confirmedEvents: ['创新药：mRNA 肿瘤疫苗临床数据更新（事件82 / 行情78）'],
    riskSignals: ['量能偏弱：5日/20日平均成交额比仅 0.81'],
    nextSessionWatchlist: ['两市成交额能否重新放大，并与指数方向形成同向确认'],
    evidence: ['全A上涨比例 63%，涨跌中位数 +0.70%'],
    qualityStatus: 'PARTIAL'
  },
  historyPoints: [
    { businessDate: '2026-08-21', marketStage: 'RANGE_ROTATION', confidenceScore: 72, advanceRatio: 0.63, totalAmount: 2300000000000, medianChangePct: 0.7, leadingSectorName: '创新药', leadingSectorScore: 78, headline: '急跌后缩量修复，反弹持续性仍需量能确认', qualityStatus: 'PARTIAL' },
    { businessDate: '2026-08-20', marketStage: 'POST_SELL_OFF_REPAIR', confidenceScore: 68, advanceRatio: 0.55, totalAmount: 2100000000000, medianChangePct: 0.3, leadingSectorName: '贵金属', leadingSectorScore: 70, headline: '急跌后进入修复', qualityStatus: 'PARTIAL' },
    { businessDate: '2026-08-19', marketStage: 'SELL_OFF', confidenceScore: 83, advanceRatio: 0.18, totalAmount: 2500000000000, medianChangePct: -2.1, leadingSectorName: '银行', leadingSectorScore: 66, headline: '风险偏好快速收缩，市场进入集中调整', qualityStatus: 'PARTIAL' },
    { businessDate: '2026-08-18', marketStage: 'RANGE_ROTATION', confidenceScore: 70, advanceRatio: 0.42, totalAmount: 2200000000000, medianChangePct: -0.2, leadingSectorName: '种植业与林业', leadingSectorScore: 71, headline: '高位分歧加大，防御方向相对占优', qualityStatus: 'PARTIAL' },
    { businessDate: '2026-08-17', marketStage: 'TREND_EXPANSION', confidenceScore: 80, advanceRatio: 0.72, totalAmount: 2300000000000, medianChangePct: 1.2, leadingSectorName: '半导体', leadingSectorScore: 82, headline: '放量上行，科技主线与市场宽度共振', qualityStatus: 'PARTIAL' }
  ],
  breadth: {
    businessDate: '2026-08-21', sourceCode: 'AKSHARE_EASTMONEY_A_SPOT', sourceFamily: 'EASTMONEY',
    qualityStatus: 'FRESH_PRIMARY', advanceCount: 3200, declineCount: 1800, flatCount: 100,
    validCount: 5100, advanceRatio: 3200 / 5100, totalAmount: 2300000000000,
    limitUpCount: 68, limitDownCount: 4, medianChangePct: 0.7,
    interpretation: '主要指数与个股宽度共振走强', warnings: [],
    indices: [
      { code: '000001.SH', name: '上证指数', businessDate: '2026-08-21', close: 3905.2, return1d: 0.6, return5d: -0.3, return20d: 2.1, sourceCode: 'EASTMONEY_DIRECT', qualityStatus: 'FRESH_PRIMARY' },
      { code: '399001.SZ', name: '深证成指', businessDate: '2026-08-21', close: 14094.17, return1d: 0.8, return5d: -0.5, return20d: 3.2, sourceCode: 'EASTMONEY_DIRECT', qualityStatus: 'FRESH_PRIMARY' },
      { code: '399006.SZ', name: '创业板指', businessDate: '2026-08-21', close: 3100, return1d: 1.1, return5d: -1.2, return20d: 4.5, sourceCode: 'EASTMONEY_DIRECT', qualityStatus: 'FRESH_PRIMARY' },
      { code: '000300.SH', name: '沪深300', businessDate: '2026-08-21', close: 4618.9, return1d: 0.5, return5d: -0.2, return20d: 2.0, sourceCode: 'EASTMONEY_DIRECT', qualityStatus: 'FRESH_PRIMARY' },
      { code: '000852.SH', name: '中证1000', businessDate: '2026-08-21', close: 7200, return1d: 0.9, return5d: 0.2, return20d: 5.1, sourceCode: 'EASTMONEY_DIRECT', qualityStatus: 'FRESH_PRIMARY' }
    ]
  },
  regime: {
    businessDate: '2026-08-21',
    trendState: 'RANGE',
    liquidityState: 'SHRINKING',
    riskAppetiteState: 'NEUTRAL',
    rotationState: 'FAST',
    marketStage: 'RANGE_ROTATION',
    confidenceScore: 72,
    qualityStatus: 'PARTIAL',
    explanation: '震荡轮动：趋势与流动性尚未形成同向突破',
    evidence: ['20日收益 1.20%，价格相对MA20 0.30%'],
    features: { return1d: 0.006, return5d: -0.012, return20d: 0.012, amountRatio5To20: 0.81, maxDrawdown20: -0.042, volatility20: 0.24, sectorDispersion: 0.028 }
  },
  recentRegimes: [
    { businessDate: '2026-08-21', marketStage: 'RANGE_ROTATION', confidenceScore: 72, features: { return1d: 0.006 } },
    { businessDate: '2026-08-20', marketStage: 'POST_SELL_OFF_REPAIR', confidenceScore: 68, features: { return1d: 0.012 } },
    { businessDate: '2026-08-19', marketStage: 'SELL_OFF', confidenceScore: 83, features: { return1d: -0.034 } }
  ],
  sectors: [
    { sectorCode: 'BK1040', sectorName: '创新药', return1d: 2.1, return5d: 5.8, mainNetInflow: 3200000000, rotationScore: 78, stage: 'PERSISTENT', explanations: ['5日收益 5.80%'] },
    { sectorCode: 'BK0737', sectorName: '贵金属', return1d: 0.8, return5d: 2.2, mainNetInflow: 1100000000, rotationScore: 61, stage: 'EMERGING', explanations: ['资金排名较前次改善'] }
  ],
  eventConfirmations: [
    { radarEventId: 7, title: 'mRNA 肿瘤疫苗临床数据更新', sectorName: '创新药', eventScore: 82, marketReactionScore: 78, confirmationState: 'CONFIRMED', eligibleForRanking: true, evidence: ['事件与市场同向确认'] }
  ],
  candidates: [
    { instrumentCode: '600000.SH', name: '示例医药', researchRank: 1, calibratedProbability: 0.67, healthStatus: 'HEALTHY', sectorName: '创新药', sectorStage: 'PERSISTENT', whyNow: '行业持续且股票模型通过稳健性门禁', reasons: ['模型排名第 1'], risks: ['行业进入拥挤阶段'], invalidationConditions: ['行业轮动阶段转弱'] }
  ]
};

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation((input: RequestInfo | URL, options?: RequestInit) => {
    const path = String(input);
    const data = path.endsWith('/dates') ? ['2026-08-21']
      : path.endsWith('/refresh') && options?.method === 'POST' ? { status: 'SUCCEEDED' }
        : path.includes('/backfill?') && options?.method === 'POST' ? { status: 'SUCCEEDED', results: workspace.historyPoints }
          : path.endsWith('/2026-08-17') ? { ...workspace, businessDate: '2026-08-17', dailyReview: { ...workspace.dailyReview, businessDate: '2026-08-17', headline: '放量上行，科技主线与市场宽度共振' } }
        : workspace;
    return Promise.resolve({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ success: true, code: 'SUCCESS', message: 'success', traceId: 'trace', timestamp: '2026-08-23T10:00:00Z', data })
    });
  }));
});

test('renders the decision chain from regime to verified stock candidates', async () => {
  render(<MarketPulseView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '震荡轮动' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '急跌后缩量修复，反弹持续性仍需量能确认' })).toBeInTheDocument();
  expect(screen.getByText(/创业板指领涨/)).toBeInTheDocument();
  expect(screen.getAllByText(/上涨比例 63%/)).toHaveLength(2);

  fireEvent.click(screen.getByRole('tab', { name: '市场结构' }));

  expect(screen.getByText('市场节奏轨')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '市场宽度' })).toBeInTheDocument();
  expect(screen.getByText('上证指数')).toBeInTheDocument();
  expect(screen.getByText('中证1000')).toBeInTheDocument();
  expect(screen.getByText('3,200')).toBeInTheDocument();
  expect(screen.getByText('2.30 万亿')).toBeInTheDocument();
  expect(screen.getByText('主要指数与个股宽度共振走强')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '创新药' })).toBeInTheDocument();
  expect(screen.getByText('事件与行情确认')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '示例医药' })).toBeInTheDocument();
  expect(screen.getByText('行业持续且股票模型通过稳健性门禁')).toBeInTheDocument();
  expect(screen.getByText(/研究候选不是买入指令/)).toBeInTheDocument();
});

test('switches from the default daily review to historical evolution', async () => {
  render(<MarketPulseView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('tab', { name: '今日复盘' })).toHaveAttribute('aria-selected', 'true');
  fireEvent.click(screen.getByRole('tab', { name: '历史演变' }));

  expect(screen.getByRole('heading', { name: '近 20 日市场演变' })).toBeInTheDocument();
  expect(screen.getByText('贵金属')).toBeInTheDocument();
  expect(screen.getByText('2.10 万亿')).toBeInTheDocument();
});

test('refreshes and reloads the frozen workspace', async () => {
  const addToast = vi.fn();
  render(<MarketPulseView addToast={addToast} setMessage={vi.fn()} />);

  fireEvent.click(await screen.findByRole('button', { name: '刷新今日判断' }));

  await waitFor(() => expect(addToast).toHaveBeenCalledWith('市场机会判断已刷新', 'success'));
  expect(fetch).toHaveBeenCalledWith('/api/market-pulse/refresh', expect.objectContaining({ method: 'POST' }));
});

test('backfills the five requested days and opens one historical review', async () => {
  const addToast = vi.fn();
  render(<MarketPulseView addToast={addToast} setMessage={vi.fn()} />);

  fireEvent.click(await screen.findByRole('tab', { name: '历史演变' }));
  fireEvent.click(screen.getByRole('button', { name: '补全 8.17–8.21 判断' }));

  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/market-pulse/backfill?startDate=2026-08-17&endDate=2026-08-21',
    expect.objectContaining({ method: 'POST' })
  ));
  expect(screen.getAllByRole('button', { name: '查看当日复盘' })).toHaveLength(5);

  fireEvent.click(screen.getAllByRole('button', { name: '查看当日复盘' })[4]);

  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/market-pulse/2026-08-17', expect.any(Object)));
  expect(await screen.findByRole('heading', { name: '放量上行，科技主线与市场宽度共振' })).toBeInTheDocument();
  expect(screen.getByRole('tab', { name: '今日复盘' })).toHaveAttribute('aria-selected', 'true');
});
