import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';

import { MarketPulseView } from './MarketPulseView';

const workspace = {
  businessDate: '2026-08-21',
  qualityStatus: 'PARTIAL',
  generatedAt: '2026-08-23T18:00:00',
  warnings: ['行业宽度未接入，评分已降低置信度'],
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

test('refreshes and reloads the frozen workspace', async () => {
  const addToast = vi.fn();
  render(<MarketPulseView addToast={addToast} setMessage={vi.fn()} />);

  fireEvent.click(await screen.findByRole('button', { name: '刷新今日判断' }));

  await waitFor(() => expect(addToast).toHaveBeenCalledWith('市场机会判断已刷新', 'success'));
  expect(fetch).toHaveBeenCalledWith('/api/market-pulse/refresh', expect.objectContaining({ method: 'POST' }));
});
