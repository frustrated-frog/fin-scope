import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { SingleStockForecastPanel } from './SingleStockForecastPanel';

const summary = { id: 7, instrumentCode: '600519.SH', asOfDate: '2026-08-06',
  status: 'NO_CLEAR_EDGE', upProbability: 0.53, dataFingerprint: 'abcdef',
  modelVersion: 'logistic-walk-forward-v2', reportSchemaVersion: 'single-stock-research-v2',
  sameDataAsPrevious: false, createdAt: '2026-08-08T14:00:00' };

const report = {
  reportSchemaVersion: 'single-stock-research-v2', modelVersion: 'logistic-walk-forward-v2',
  instrumentCode: '600519.SH', asOfDate: '2026-08-06', horizonDays: 20,
  status: 'NO_CLEAR_EDGE', conclusion: '样本外没有稳定优于同股买入并持有。',
  barCount: 2400, labeledSampleCount: 2320, upProbability: 0.53,
  expectedNetReturn: 0.018, lowerNetReturn: -0.072, upperNetReturn: 0.096,
  dataFingerprint: 'abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890',
  sourceCode: 'PYTDX', sourceFamily: 'TDX', qualityStatus: 'FRESH_FALLBACK', lastClose: 1505,
  strategyPolicy: { signalThreshold: .6, holdingDays: 20, entryRule: 'T+1 开盘买入',
    exitRule: 'T+20 收盘卖出', overlapPolicy: '持仓不重叠', roundTripCostRate: .0015,
    benchmark: '同股买入并持有' },
  validation: { outOfSampleCount: 850, independentSampleCount: 43, accuracy: .535,
    brierScore: .248, baselineBrierScore: .246, observedUpRate: .56 },
  inSample: { sampleCount: 1400, accuracy: .61, brierScore: .22, evidenceRole: '拟合诊断，不作为有效性证据' },
  outOfSample: { sampleCount: 850, accuracy: .535, brierScore: .248,
    baselineBrierScore: .246, evidenceRole: '滚动验证，决定最终结论' },
  performance: { benchmarkLabel: '同股买入并持有', excessReturn: -.08, tradeCount: 18,
    profitableTradeRate: .5, turnover: 7.2, totalCost: .025, holdingTimeRatio: .42,
    averageHoldingDays: 20,
    strategy: { totalReturn: .32, annualizedReturn: .08, annualizedVolatility: .21,
      sharpeRatio: .38, dailyWinRate: .47, maxDrawdown: .24,
      maxDrawdownStartDate: '2022-01-01', maxDrawdownTroughDate: '2022-04-01',
      maxDrawdownRecoveryDate: '2022-09-01', maxDrawdownDurationDays: 162 },
    benchmark: { totalReturn: .40, annualizedReturn: .10, annualizedVolatility: .23,
      sharpeRatio: .43, dailyWinRate: .51, maxDrawdown: .31,
      maxDrawdownStartDate: '2021-02-01', maxDrawdownTroughDate: '2021-08-01',
      maxDrawdownRecoveryDate: '2023-01-01', maxDrawdownDurationDays: 480 }, trades: [] },
  equityCurve: [
    { tradeDate: '2020-01-01', strategyNav: 1, benchmarkNav: 1, drawdown: 0, invested: false },
    { tradeDate: '2026-08-06', strategyNav: 1.32, benchmarkNav: 1.40, drawdown: -.05, invested: true }
  ],
  factorExplanations: [{ code: 'MOMENTUM_20', name: '20 日动量', category: '趋势',
    formula: '收盘价 / 20 日前收盘价 - 1', window: '20 个交易日', currentValue: .08,
    historicalPercentile: .72, standardizedValue: .6, coefficient: .3, contribution: .18,
    direction: '支持上涨', economicMeaning: '观察一个月价格趋势。', boundary: '震荡期可能反转。' }],
  annualPerformance: [{ year: 2025, strategyReturn: .12, benchmarkReturn: .18,
    excessReturn: -.06, maxDrawdown: .14, tradeCount: 4 }],
  regimePerformance: [{ regime: 'UPTREND', label: '上行阶段', sampleDays: 320,
    strategyReturn: .22, benchmarkReturn: .28, excessReturn: -.06, sharpeRatio: .8,
    maxDrawdown: .12, tradeCount: 8, holdingTimeRatio: .51 }],
  parameterStability: { positiveExcessRatio: .4, worstExcessReturn: -.12,
    worstSharpeRatio: .1, scenarios: [
      { holdingDays: 20, threshold: .6, primary: true, annualizedReturn: .08,
        excessReturn: -.08, sharpeRatio: .38, maxDrawdown: .24, tradeCount: 18 }
    ] },
  recentObservations: [{ signalDate: '2026-05-08', probability: .61,
    actualNetReturn: .034, correct: true }], warnings: ['收益基于前复权日线模拟']
};

const run = { ...summary, report, holdingSnapshot: { held: true, instrumentCode: '600519.SH',
  instrumentName: '贵州茅台', quantity: 10, averageCost: 1400, lastClose: 1505,
  estimatedMarketValue: 15050, unrealizedReturn: .075,
  interpretation: '没有发现稳定优势，不能由本次概率单独强化。' } };

beforeEach(() => vi.unstubAllGlobals());

test('runs and presents a complete same-stock benchmark research report', async () => {
  const fetch = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) =>
    apiResponse(init?.method === 'POST' ? run : []));
  vi.stubGlobal('fetch', fetch);
  const user = userEvent.setup();

  render(<SingleStockForecastPanel addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.type(screen.getByLabelText('股票代码'), '600519');
  await user.click(screen.getByRole('button', { name: '运行完整研究' }));

  expect((await screen.findAllByText('53.0%')).length).toBeGreaterThan(0);
  expect(screen.getAllByText('同股买入并持有').length).toBeGreaterThan(0);
  expect(screen.getByText('最大回撤持续时间')).toBeInTheDocument();
  expect(screen.getByText('持仓时间占比')).toBeInTheDocument();
  expect(screen.getByText('20 日动量')).toBeInTheDocument();
  expect(screen.getByText('样本内 / 样本外')).toBeInTheDocument();
  expect(screen.getByText('相邻参数稳定性')).toBeInTheDocument();
  expect(screen.getByText('分年度表现')).toBeInTheDocument();
  expect(screen.getByText('标的自身趋势阶段')).toBeInTheDocument();
  expect(screen.getByText('我的持仓快照')).toBeInTheDocument();
});

test('opens an immutable historical report without posting a new run', async () => {
  const fetch = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) =>
    apiResponse(String(input).endsWith('/7') ? run : [summary]));
  vi.stubGlobal('fetch', fetch);
  const user = userEvent.setup();

  render(<SingleStockForecastPanel addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.click(await screen.findByRole('button', { name: /600519.SH/ }));

  expect(await screen.findByText('我的持仓快照')).toBeInTheDocument();
  expect(fetch.mock.calls.some(([input]) => String(input) === '/api/quant/single-stock-forecasts/7')).toBe(true);
  expect(fetch.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
});

test('rejects malformed code locally and does not post a forecast', async () => {
  const fetch = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => apiResponse([]));
  vi.stubGlobal('fetch', fetch);
  const addToast = vi.fn();
  const user = userEvent.setup();

  render(<SingleStockForecastPanel addToast={addToast} setMessage={vi.fn()} />);
  await user.type(screen.getByLabelText('股票代码'), 'abc');
  await user.click(screen.getByRole('button', { name: '运行完整研究' }));

  expect(addToast).toHaveBeenCalledWith('请输入六位 A 股代码', 'error');
  expect(fetch.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
});

test('treats a malformed history payload as an empty archive', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => apiResponse({})));

  render(<SingleStockForecastPanel addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('运行第一份研究后，它会永久留在这里。')).toBeInTheDocument();
});

test('saves and shows an insufficient-data run without fabricating probability', async () => {
  const insufficient = { ...run, status: 'INSUFFICIENT_DATA', upProbability: undefined,
    report: { ...report, status: 'INSUFFICIENT_DATA', upProbability: undefined,
      conclusion: '历史日线不足 750 根。', barCount: 420, performance: undefined } };
  vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) =>
    apiResponse(init?.method === 'POST' ? insufficient : [])));
  const user = userEvent.setup();

  render(<SingleStockForecastPanel addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.type(screen.getByLabelText('股票代码'), '001309');
  await user.click(screen.getByRole('button', { name: '运行完整研究' }));

  expect(await screen.findByRole('heading', { name: '数据不足' })).toBeInTheDocument();
  expect(screen.getByText(/已取得 420 根日线/)).toBeInTheDocument();
  expect(screen.queryByText('53.0%')).not.toBeInTheDocument();
});
