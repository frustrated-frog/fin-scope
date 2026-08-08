import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { SingleStockForecastPanel } from './SingleStockForecastPanel';

const summary = { id: 7, instrumentCode: '600519.SH', asOfDate: '2026-08-06',
  status: 'NO_CLEAR_EDGE', upProbability: 0.53, dataFingerprint: 'abcdef',
  modelVersion: 'logistic-platt-qualified-v3', reportSchemaVersion: 'single-stock-research-v3',
  sameDataAsPrevious: false, createdAt: '2026-08-08T14:00:00' };

const report = {
  reportSchemaVersion: 'single-stock-research-v3', modelVersion: 'logistic-platt-qualified-v3',
  instrumentCode: '600519.SH', asOfDate: '2026-08-06', horizonDays: 20,
  status: 'NO_CLEAR_EDGE', conclusion: '样本外没有稳定优于同股买入并持有。',
  barCount: 2400, labeledSampleCount: 2320, upProbability: 0.53, rawProbability: 0.59,
  probabilityInterval: { status: 'AVAILABLE', lower: .47, upper: .61, confidenceLevel: .95,
    method: 'MOVING_BLOCK_BOOTSTRAP', validIterations: 500,
    limitation: '仅覆盖校准映射的抽样误差，不覆盖模型、突发事件与市场结构变化' },
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
  qualification: { status: 'CONDITIONAL', reason: '区间仍跨越无优势边界',
    trial: { trialId: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      featureVersion: 'price-volume-7-v1', labelVersion: 'net-return-positive-20d-v1',
      splitVersion: 'forward-60-20-20-purged-v1', calibrationVersion: 'platt-v1',
      bootstrapVersion: 'moving-block-v1', randomSeed: 7,
      modelVersion: 'logistic-platt-qualified-v3' },
    splitAudit: {
      development: { startDate: '2015-01-01', endDate: '2021-01-01', sampleCount: 1392,
        independentSampleCount: 70, positiveCount: 38, purgedCount: 20 },
      calibration: { startDate: '2021-01-02', endDate: '2023-08-01', sampleCount: 464,
        independentSampleCount: 24, positiveCount: 13, purgedCount: 0 },
      lockedTest: { startDate: '2023-08-02', endDate: '2026-08-01', sampleCount: 464,
        independentSampleCount: 24, positiveCount: 13, purgedCount: 0 },
      labelHorizonDays: 20, independentStrideDays: 20,
      rule: '严格前向 60/20/20；训练标签退出日必须早于待预测日' },
    calibration: { status: 'FITTED', method: 'PLATT', sampleCount: 24, positiveCount: 13,
      slope: .72, intercept: .03, rawLogLoss: .69, calibratedLogLoss: .66 },
    lockedTest: { baselineProbability: .54,
      rawMetrics: { sampleCount: 24, accuracy: .54, brierScore: .249, baselineBrierScore: .248,
        brierSkillScore: -.004, logLoss: .69, expectedCalibrationError: .12 },
      calibratedMetrics: { sampleCount: 24, accuracy: .58, brierScore: .238, baselineBrierScore: .248,
        brierSkillScore: .04, logLoss: .66, expectedCalibrationError: .08 },
      baselineMetrics: { sampleCount: 24, accuracy: .54, brierScore: .248, baselineBrierScore: .248,
        brierSkillScore: 0, logLoss: .69, expectedCalibrationError: 0 },
      reliabilityBins: [
        { lowerBound: 0, upperBound: .2, count: 2, meanProbability: .15, observedUpRate: 0, calibrationError: .15 },
        { lowerBound: .2, upperBound: .4, count: 4, meanProbability: .32, observedUpRate: .25, calibrationError: .07 },
        { lowerBound: .4, upperBound: .6, count: 8, meanProbability: .51, observedUpRate: .5, calibrationError: .01 },
        { lowerBound: .6, upperBound: .8, count: 7, meanProbability: .68, observedUpRate: .71, calibrationError: .03 },
        { lowerBound: .8, upperBound: 1, count: 3, meanProbability: .86, observedUpRate: 1, calibrationError: .14 }
      ] },
    confidenceIntervals: {
      brierSkillScore: { status: 'AVAILABLE', lower: -.08, upper: .14, confidenceLevel: .95, method: 'MOVING_BLOCK_BOOTSTRAP', validIterations: 1000 },
      accuracy: { status: 'AVAILABLE', lower: .42, upper: .72, confidenceLevel: .95, method: 'MOVING_BLOCK_BOOTSTRAP', validIterations: 1000 },
      excessReturn: { status: 'AVAILABLE', lower: -.18, upper: .07, confidenceLevel: .95, method: 'MOVING_BLOCK_BOOTSTRAP', validIterations: 1000 },
      sharpeRatio: { status: 'AVAILABLE', lower: -.22, upper: .91, confidenceLevel: .95, method: 'MOVING_BLOCK_BOOTSTRAP', validIterations: 1000 }
    } },
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
  expect(screen.getByText('校准后上涨概率')).toBeInTheDocument();
  expect(screen.getByText('47.0% — 61.0%')).toBeInTheDocument();
  expect(screen.getByText('原始模型 59.0%')).toBeInTheDocument();
  expect(screen.getByText('预测可信度与概率校准')).toBeInTheDocument();
  expect(screen.getByText('Brier Skill')).toBeInTheDocument();
  expect(screen.getAllByText('Log Loss').length).toBeGreaterThan(0);
  expect(screen.getAllByText('ECE').length).toBeGreaterThan(0);
  expect(screen.getByRole('table', { name: '锁定测试概率质量对照' })).toBeInTheDocument();
  expect(screen.getByText('朴素基准')).toBeInTheDocument();
  expect(screen.getAllByText('锁定测试').length).toBeGreaterThan(0);
  expect(screen.getByText(/仅覆盖校准映射的抽样误差/)).toBeInTheDocument();
});

test('explains that legacy v2 history has no qualification evidence', async () => {
  const legacy = { ...run, report: { ...report, reportSchemaVersion: 'single-stock-research-v2',
    modelVersion: 'logistic-walk-forward-v2', rawProbability: undefined,
    probabilityInterval: undefined, qualification: undefined } };
  vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) =>
    apiResponse(init?.method === 'POST' ? legacy : [])));
  const user = userEvent.setup();

  render(<SingleStockForecastPanel addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.type(screen.getByLabelText('股票代码'), '600519');
  await user.click(screen.getByRole('button', { name: '运行完整研究' }));

  expect(await screen.findByText('该记录生成时尚未启用锁定资格检验')).toBeInTheDocument();
});

test('defensively rejects a historical qualified status contradicted by locked metrics', async () => {
  const inconsistent = { ...run, report: { ...report, qualification: { ...report.qualification!,
    status: 'QUALIFIED' as const, lockedTest: { ...report.qualification!.lockedTest,
      calibratedMetrics: { ...report.qualification!.lockedTest.calibratedMetrics,
        brierSkillScore: -.34, logLoss: 1.327 },
      rawMetrics: { ...report.qualification!.lockedTest.rawMetrics, logLoss: 1.077 } } } } };
  vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) =>
    apiResponse(init?.method === 'POST' ? inconsistent : [])));
  const user = userEvent.setup();

  render(<SingleStockForecastPanel addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.type(screen.getByLabelText('股票代码'), '603618');
  await user.click(screen.getByRole('button', { name: '运行完整研究' }));

  expect(await screen.findByText('未通过资格检验')).toBeInTheDocument();
  expect(screen.getByText('锁定测试的概率质量未达到当前资格门槛')).toBeInTheDocument();
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
