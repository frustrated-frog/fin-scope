import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { RealHoldingsLab } from './RealHoldingsLab';

const account = {
  cash: 2400, marketValue: 3100, totalEquity: 5500, realizedProfit: 80,
  unrealizedProfit: 600, dividendIncome: 20, totalProfit: 700, concentration: 1,
  calculatedAt: '2026-08-31T15:01:00',
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
  await user.click(await screen.findByRole('button', { name: '记录交易' }));
  await user.selectOptions(screen.getByLabelText('事件类型'), 'CASH_DEPOSIT');
  await user.type(screen.getByLabelText('现金金额'), '5000');
  await user.click(screen.getByRole('button', { name: '写入不可变账本' }));

  const create = fetch.mock.calls.find(([input, init]) => String(input).endsWith('/stock-transactions') && init?.method === 'POST');
  expect(JSON.parse(String(create?.[1]?.body))).toMatchObject({ type: 'CASH_DEPOSIT', cashAmount: 5000 });
  expect(JSON.parse(String(create?.[1]?.body)).code).toBeUndefined();
});
