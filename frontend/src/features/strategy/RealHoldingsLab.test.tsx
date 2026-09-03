import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { RealHoldingsLab } from './RealHoldingsLab';

const account = {
  cash: 2400, marketValue: 3100, totalEquity: 5500, realizedProfit: 80,
  unrealizedProfit: 600, dividendIncome: 20, totalProfit: 700, concentration: 1,
  cashTracked: true, calculatedAt: '2026-08-31T15:01:00',
  positions: [{
    instrumentCode: '600570.SH', instrumentName: '恒生电子', quantity: 100,
    totalCost: 2500, averageCost: 25, lastPrice: 31, quoteDate: '2026-08-31',
    quoteQuality: 'RAW_QUOTE', marketValue: 3100, realizedProfit: 80,
    unrealizedProfit: 600, dividendIncome: 20, totalProfit: 700, weight: 1
  }]
};

const decision = {
  id: 21, instrumentCode: '600570.SH', instrumentName: '恒生电子',
  decisionDate: '2026-08-31', forecastRunId: 12, horizonDays: 5,
  modelVersion: 'panel-logit-v10', dataFingerprint: 'sha256:abc', action: 'HOLD',
  suggestedQuantity: 0, expectedEdgeAfterCost: 0.018, p10RiskAmount: -155,
  p90UpsideAmount: 260, currentMarketValue: 3100, projectedWeight: 0.56,
  evidence: ['校准上涨概率 61.0%'], blockers: [], explanation: '证据不足以改变仓位。',
  benchmark: '同一只股票保持当时持仓不动', policyVersion: 'holding-policy-v1',
  validationStatus: 'PENDING', maturityDate: '2026-09-07', createdAt: '2026-08-31T15:01:00'
};

beforeEach(() => vi.unstubAllGlobals());

test('shows real account evidence and automatically evaluates frozen holding advice', async () => {
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith('/stock-account')) return apiResponse(account);
    if (url.endsWith('/stock-transactions')) return apiResponse([]);
    if (url.endsWith('/holding-decisions/refresh') && init?.method === 'POST') return apiResponse([decision]);
    if (url.endsWith('/holding-decisions')) return apiResponse([]);
    return apiResponse({});
  });
  vi.stubGlobal('fetch', fetch);

  render(<RealHoldingsLab addToast={vi.fn()} />);

  expect(await screen.findByText('¥5,500.00')).toBeInTheDocument();
  expect(screen.getAllByText('恒生电子').length).toBeGreaterThan(0);
  expect(await screen.findByText('保持持有')).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith('/api/strategy/holding-decisions/refresh', expect.objectContaining({ method: 'POST' }));
});

test('records cash events without pretending they are stock trades', async () => {
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (init?.method === 'POST') return apiResponse({ id: 1 });
    if (url.endsWith('/stock-account')) return apiResponse({ ...account, positions: [] });
    return apiResponse([]);
  });
  vi.stubGlobal('fetch', fetch);
  const user = userEvent.setup();

  render(<RealHoldingsLab addToast={vi.fn()} />);
  await user.click(await screen.findByRole('button', { name: '记录新交易' }));
  await user.selectOptions(screen.getByLabelText('事件类型'), 'CASH_DEPOSIT');
  await user.type(screen.getByLabelText('现金金额'), '5000');
  await user.click(screen.getByRole('button', { name: '写入不可变账本' }));

  const create = fetch.mock.calls.find(([input, init]) => String(input).endsWith('/stock-transactions') && init?.method === 'POST');
  expect(JSON.parse(String(create?.[1]?.body))).toMatchObject({ type: 'CASH_DEPOSIT', cashAmount: 5000 });
  expect(JSON.parse(String(create?.[1]?.body)).code).toBeUndefined();
});

test('opens historical position entry directly and explains that it does not deduct cash', async () => {
  const fetch = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith('/stock-account')) return apiResponse({ ...account, positions: [] });
    return apiResponse([]);
  });
  vi.stubGlobal('fetch', fetch);
  const user = userEvent.setup();

  render(<RealHoldingsLab addToast={vi.fn()} />);
  await user.click(await screen.findByRole('button', { name: '补录已有持仓' }));

  expect(screen.getByLabelText('事件类型')).toHaveValue('OPENING_BALANCE');
  expect(screen.getByText(/只建立持仓数量和成本，不扣减账户现金/)).toBeInTheDocument();
});

test('opens holding analysis with path risk and frozen prediction evidence', async () => {
  const analysis = {
    instrumentCode: '600570.SH', instrumentName: '恒生电子', entryDate: '2026-07-15',
    asOfDate: '2026-08-31', holdingCalendarDays: 47, observedTradingDays: 34,
    costBasis: 25, latestPrice: 31, quantity: 100, totalCost: 2500,
    marketValue: 3100, unrealizedProfit: 600, holdingReturn: 0.24,
    maximumFavorableExcursion: 0.31, maximumAdverseExcursion: -0.06,
    maximumDrawdown: -0.09, maximumDrawdownDays: 6, annualizedVolatility: 0.28,
    qualityStatus: 'COMPLETE', sourceCode: 'CACHE', method: 'QFQ_NORMALIZED_TO_RAW_QUOTE',
    warnings: [], forecast: { runId: 12, asOfDate: '2026-08-31', horizonDays: 5,
      status: 'CONDITIONAL', upProbability: 0.61, p10: -0.04, p50: 0.018, p90: 0.08,
      modelVersion: 'panel-logit-v10' },
    series: [
      { tradeDate: '2026-07-15', close: 25, returnSinceEntry: 0, drawdown: 0 },
      { tradeDate: '2026-08-31', close: 31, returnSinceEntry: 0.24, drawdown: -0.03 }
    ]
  };
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith('/stock-account')) return apiResponse(account);
    if (url.endsWith('/stock-transactions')) return apiResponse([]);
    if (url.endsWith('/holding-decisions/refresh') && init?.method === 'POST') return apiResponse([decision]);
    if (url.endsWith('/holding-decisions')) return apiResponse([]);
    if (url.includes('/stock-positions/600570.SH/analysis')) return apiResponse(analysis);
    return apiResponse({});
  });
  vi.stubGlobal('fetch', fetch);
  const user = userEvent.setup();

  render(<RealHoldingsLab addToast={vi.fn()} />);
  await user.click(await screen.findByRole('button', { name: /查看恒生电子持仓量化分析/ }));

  expect(await screen.findByText('持仓收益路径')).toBeInTheDocument();
  expect(screen.getByText('最大有利波动')).toBeInTheDocument();
  expect(screen.getByText('61.00%')).toBeInTheDocument();
  expect(screen.getByText('模型冻结证据')).toBeInTheDocument();
});

test('reclassifies a backfilled buy through append-only reversal and opening events', async () => {
  const buy = {
    id: 9, clientRequestId: 'legacy-buy', instrumentCode: '600570.SH',
    instrumentName: '恒生电子', type: 'BUY', tradeDate: '2026-07-15',
    quantity: 100, price: 25, commission: 0, stampDuty: 0, transferFee: 0,
    otherFee: 0, createdAt: '2026-09-04T09:30:00'
  };
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith('/stock-account')) return apiResponse(account);
    if (url.endsWith('/holding-decisions/refresh') && init?.method === 'POST') return apiResponse([]);
    if (url.endsWith('/holding-decisions')) return apiResponse([]);
    if (url.endsWith('/stock-transactions/9/reclassify-opening') && init?.method === 'POST') {
      return apiResponse({ ...buy, id: 11, type: 'OPENING_BALANCE' });
    }
    if (url.endsWith('/stock-transactions')) return apiResponse([buy]);
    return apiResponse({});
  });
  vi.stubGlobal('fetch', fetch);
  const user = userEvent.setup();

  render(<RealHoldingsLab addToast={vi.fn()} />);
  await user.click(await screen.findByRole('button', { name: '交易流水' }));
  await user.click(screen.getByRole('button', { name: '改为期初' }));

  const call = fetch.mock.calls.find(([input, init]) =>
    String(input).endsWith('/stock-transactions/9/reclassify-opening') && init?.method === 'POST');
  expect(call).toBeDefined();
  expect(JSON.parse(String(call?.[1]?.body))).toMatchObject({ tradeDate: expect.any(String) });
});
