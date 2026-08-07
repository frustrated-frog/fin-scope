import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { SingleStockForecastPanel } from './SingleStockForecastPanel';

const forecast = {
  instrumentCode: '600519.SH', asOfDate: '2026-08-06', horizonDays: 20,
  status: 'NO_OBSERVED_EDGE', conclusion: '样本外概率尚未稳定优于基础上涨率。',
  barCount: 2400, labeledSampleCount: 2320, upProbability: 0.53,
  expectedNetReturn: 0.018, lowerNetReturn: -0.072, upperNetReturn: 0.096,
  dataFingerprint: 'abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890',
  sourceCode: 'EASTMONEY_DIRECT', sourceFamily: 'EASTMONEY', qualityStatus: 'FRESH_PRIMARY',
  validation: { outOfSampleCount: 850, independentSampleCount: 43, accuracy: 0.535,
    brierScore: 0.248, baselineBrierScore: 0.246, observedUpRate: 0.56 },
  recentObservations: [
    { signalDate: '2026-05-08', probability: 0.61, actualNetReturn: 0.034, correct: true },
    { signalDate: '2026-04-08', probability: 0.48, actualNetReturn: -0.021, correct: true }
  ],
  warnings: ['收益基于前复权日线模拟']
};

beforeEach(() => vi.unstubAllGlobals());

test('runs a twenty-day forecast and presents probability with sample-out evidence', async () => {
  const fetch = vi.fn(async () => apiResponse(forecast));
  vi.stubGlobal('fetch', fetch);
  const user = userEvent.setup();

  render(<SingleStockForecastPanel addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.type(screen.getByLabelText('股票代码'), '600519');
  await user.click(screen.getByRole('button', { name: '运行 20 日预测' }));

  expect((await screen.findAllByText('53.0%')).length).toBeGreaterThan(0);
  expect(screen.getByText('未发现稳定优势')).toBeInTheDocument();
  expect(screen.getByText('样本外证据')).toBeInTheDocument();
  expect(screen.getByText('43 个独立样本')).toBeInTheDocument();
  expect(screen.getByRole('table', { name: '最近样本外预测' })).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith('/api/quant/single-stock-forecasts', expect.objectContaining({
    method: 'POST', body: JSON.stringify({ code: '600519' })
  }));
});

test('rejects malformed code locally and does not fabricate a forecast', async () => {
  const fetch = vi.fn();
  vi.stubGlobal('fetch', fetch);
  const addToast = vi.fn();
  const user = userEvent.setup();

  render(<SingleStockForecastPanel addToast={addToast} setMessage={vi.fn()} />);
  await user.type(screen.getByLabelText('股票代码'), 'abc');
  await user.click(screen.getByRole('button', { name: '运行 20 日预测' }));

  expect(addToast).toHaveBeenCalledWith('请输入六位 A 股代码', 'error');
  expect(fetch).not.toHaveBeenCalled();
  expect(screen.queryByText(/%/)).not.toBeInTheDocument();
});

test('shows data gate instead of a probability when history is insufficient', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => apiResponse({
    ...forecast, status: 'INSUFFICIENT_DATA', conclusion: '历史日线不足 750 根。',
    barCount: 420, labeledSampleCount: undefined, upProbability: undefined,
    expectedNetReturn: undefined, lowerNetReturn: undefined, upperNetReturn: undefined,
    validation: undefined, recentObservations: []
  })));
  const user = userEvent.setup();

  render(<SingleStockForecastPanel addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.type(screen.getByLabelText('股票代码'), '001309');
  await user.click(screen.getByRole('button', { name: '运行 20 日预测' }));

  expect(await screen.findByText('历史样本不足')).toBeInTheDocument();
  expect(screen.getByText('已取得 420 根日线，正式预测至少需要 750 根。')).toBeInTheDocument();
  expect(screen.queryByText('53.0%')).not.toBeInTheDocument();
});
