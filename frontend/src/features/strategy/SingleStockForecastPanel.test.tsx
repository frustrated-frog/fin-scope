import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { SingleStockForecastPanel } from './SingleStockForecastPanel';

const summary = { id: 7, instrumentCode: '600519.SH', asOfDate: '2026-08-06',
  status: 'NO_CLEAR_EDGE', upProbability: 0.53, dataFingerprint: 'abcdef',
  modelVersion: 'logistic-platt-selective-v4', reportSchemaVersion: 'single-stock-research-v4',
  horizonDays: 5, maturityStatus: 'PENDING',
  sameDataAsPrevious: false, createdAt: '2026-08-08T14:00:00' };

const report = {
  reportSchemaVersion: 'single-stock-research-v4', modelVersion: 'logistic-platt-selective-v4',
  instrumentCode: '600519.SH', asOfDate: '2026-08-06', horizonDays: 5,
  status: 'NO_CLEAR_EDGE', conclusion: '样本外没有稳定优于同股买入并持有。',
  decision: 'ABSTAIN', decisionReason: '概率位于拒绝区间，当前信息不足以形成方向优势。',
  barCount: 2400, labeledSampleCount: 2320, upProbability: 0.53, rawProbability: 0.59,
  probabilityInterval: { status: 'AVAILABLE', lower: .47, upper: .61, confidenceLevel: .95,
    method: 'MOVING_BLOCK_BOOTSTRAP', validIterations: 500,
    limitation: '仅覆盖校准映射的抽样误差，不覆盖模型、突发事件与市场结构变化' },
  expectedNetReturn: 0.018, lowerNetReturn: -0.072, upperNetReturn: 0.096,
  dataFingerprint: 'abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890',
  sourceCode: 'PYTDX', sourceFamily: 'TDX', qualityStatus: 'FRESH_FALLBACK', lastClose: 1505,
  strategyPolicy: { signalThreshold: .6, holdingDays: 5, entryRule: 'T+1 开盘买入',
    exitRule: 'T+6 开盘卖出', overlapPolicy: '持仓不重叠', roundTripCostRate: .0015,
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
  selectiveValidation: { lowerThreshold: .4, upperThreshold: .6, sampleCount: 24,
    coveredCount: 14, coverage: .5833, coveredAccuracy: .714, abstainRate: .4167 },
  context: { market: { code: '000300.SH', label: '沪深300', status: 'AVAILABLE',
      coverage: 1, regime: 'UPTREND' },
    industry: { label: '行业代理指数', status: 'UNAVAILABLE', coverage: 0,
      reason: '未匹配到可靠行业代理' }, featureCodes: ['MOMENTUM_20', 'MARKET_MOMENTUM_20'],
    alignmentRule: '按目标交易日左连接，不向未来填充' },
  modelCompetition: { selectedModel: 'BOOSTED_STUMPS', selectionEndDate: '2021-01-01',
    calibrationStartDate: '2021-01-02', selectionRule: '锁定测试不参与冠军选择', candidates: [
      { code: 'LOGISTIC', name: '正则化逻辑回归', selected: false, selectionSampleCount: 20,
        accuracy: .55, brierScore: .245, logLoss: .68, baselineBrierScore: .25, reason: '未胜出',
        validationFoldCount: 3, brierStd: .018, role: 'CHALLENGER',
        modelVersion: 'competition-logistic-platt-v6', rawProbability: .57,
        calibratedProbability: .55, shadowDecision: 'ABSTAIN', qualificationStatus: 'CONDITIONAL',
        lockedMetrics: { sampleCount: 24, accuracy: .54, brierScore: .247,
          baselineBrierScore: .25, brierSkillScore: .012, logLoss: .684,
          expectedCalibrationError: .09 } },
      { code: 'BOOSTED_STUMPS', name: '轻量梯度提升树桩', selected: true, selectionSampleCount: 20,
        accuracy: .6, brierScore: .23, logLoss: .65, baselineBrierScore: .25,
        reason: '开发区内部验证最优', validationFoldCount: 3, brierStd: .011, role: 'CHAMPION',
        modelVersion: 'competition-boosted-stumps-platt-v6', rawProbability: .64,
        calibratedProbability: .62, shadowDecision: 'UP', qualificationStatus: 'QUALIFIED',
        lockedMetrics: { sampleCount: 24, accuracy: .63, brierScore: .226,
          baselineBrierScore: .25, brierSkillScore: .096, logLoss: .641,
          expectedCalibrationError: .06 } },
      { code: 'REGIME_LOGISTIC', name: '市场状态感知逻辑回归', selected: false,
        selectionSampleCount: 20, accuracy: .59, brierScore: .234, logLoss: .657,
        baselineBrierScore: .25, reason: '多折结果接近冠军，进入影子观察', validationFoldCount: 3,
        brierStd: .009, role: 'CHALLENGER', modelVersion: 'competition-regime-logistic-platt-v6',
        rawProbability: .61, calibratedProbability: .59, shadowDecision: 'ABSTAIN',
        qualificationStatus: 'CONDITIONAL', lockedMetrics: { sampleCount: 24, accuracy: .58,
          brierScore: .236, baselineBrierScore: .25, brierSkillScore: .056, logLoss: .659,
          expectedCalibrationError: .07 } },
      { code: 'RULE_BASELINE', name: '规则基线', selected: false, selectionSampleCount: 20,
        accuracy: .52, brierScore: .252, logLoss: .697, baselineBrierScore: .25,
        reason: '保留可解释的简单基线', validationFoldCount: 3, brierStd: .014,
        role: 'BASELINE', modelVersion: 'competition-rule-baseline-platt-v6', rawProbability: .51,
        calibratedProbability: .50, shadowDecision: 'ABSTAIN', qualificationStatus: 'FAILED',
        lockedMetrics: { sampleCount: 24, accuracy: .5, brierScore: .251,
          baselineBrierScore: .25, brierSkillScore: -.004, logLoss: .695,
          expectedCalibrationError: .11 } }
    ] },
  leakageAudit: { status: 'PASSED', checkedSampleCount: 2320,
    checks: ['滚动特征只使用信号日及以前数据', '锁定测试不参与冠军选择'] },
  qlibReference: { status: 'NOT_RUN', role: '可选离线对照实验，不参与线上预测', runtimeDependency: false },
  recentObservations: [{ signalDate: '2026-05-08', probability: .61,
    actualNetReturn: .034, correct: true }], warnings: ['收益基于前复权日线模拟']
};

const run = { ...summary, maturityStatus: 'MATURED', report, holdingSnapshot: { held: true, instrumentCode: '600519.SH',
  instrumentName: '贵州茅台', quantity: 10, averageCost: 1400, lastClose: 1505,
  estimatedMarketValue: 15050, unrealizedReturn: .075,
  interpretation: '没有发现稳定优势，不能由本次概率单独强化。' },
  outcome: { entryDate: '2026-08-07', exitDate: '2026-08-14', entryOpen: 1500,
    exitOpen: 1540, actualNetReturn: .0251, actualDirection: 'UP', correct: null,
    settledAt: '2026-08-14T17:30:00', sourceCode: 'PYTDX' },
  modelHealth: { instrumentCode: '600519.SH', horizonDays: 5,
    modelVersion: 'logistic-platt-selective-v4', status: 'HEALTHY',
    directionOutputPaused: false, sampleCount: 12, coveredCount: 8, abstainedCount: 4,
    coverage: .6667, coveredAccuracy: .75, brierScore: .214, baselineBrierScore: .25,
    logLoss: .612, observedUpRate: .5833, firstAsOfDate: '2026-05-01',
    lastAsOfDate: '2026-08-06', conclusion: '近 12 次到期预测的概率质量优于 0.5 无信息基准，方向门禁开放。' },
  modelRace: { instrumentCode: '600519.SH', horizonDays: 5, championCode: 'BOOSTED_STUMPS',
    status: 'EVIDENCE_ACCUMULATING', sampleCount: 8, minimumPromotionSamples: 12,
    conclusion: '真实到期证据仍在积累，当前不触发模型晋升审查。', candidates: [
      { modelCode: 'BOOSTED_STUMPS', modelName: '轻量梯度提升树桩', role: 'CHAMPION',
        sampleCount: 8, coveredCount: 6, coverage: .75, coveredAccuracy: .667,
        brierScore: .231, brierSkillScore: .076, logLoss: .651,
        brierDeltaVsChampion: 0, logLossDeltaVsChampion: 0, promotionEligible: false },
      { modelCode: 'REGIME_LOGISTIC', modelName: '市场状态感知逻辑回归', role: 'CHALLENGER',
        sampleCount: 8, coveredCount: 5, coverage: .625, coveredAccuracy: .8,
        brierScore: .219, brierSkillScore: .124, logLoss: .637,
        brierDeltaVsChampion: -.012, logLossDeltaVsChampion: -.014, promotionEligible: false },
      { modelCode: 'RULE_BASELINE', modelName: '规则基线', role: 'BASELINE', sampleCount: 8,
        coveredCount: 3, coverage: .375, coveredAccuracy: .667, brierScore: .252,
        brierSkillScore: -.008, logLoss: .697, brierDeltaVsChampion: .021,
        logLossDeltaVsChampion: .046, promotionEligible: false }
    ] } };

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
  expect(screen.getByText('暂不判断')).toBeInTheDocument();
  expect(screen.getByText('覆盖后命中率')).toBeInTheDocument();
  expect(screen.getByText('信号覆盖率')).toBeInTheDocument();
  expect(screen.getByText('市场上下文与模型赛马')).toBeInTheDocument();
  expect(screen.getByText('沪深300')).toBeInTheDocument();
  expect(screen.getByText('轻量梯度提升树桩')).toBeInTheDocument();
  expect(screen.getAllByText('市场状态感知逻辑回归').length).toBeGreaterThan(0);
  expect(screen.getByText('历史离线资格赛')).toBeInTheDocument();
  expect(screen.getByText('真实到期影子赛')).toBeInTheDocument();
  expect(screen.getByText('8 / 12')).toBeInTheDocument();
  expect(screen.getByText('冻结概率 62.0%')).toBeInTheDocument();
  expect(screen.getByText(/不会自动换模/)).toBeInTheDocument();
  expect(screen.getByText('防未来检查通过')).toBeInTheDocument();
  expect(screen.getByText('Qlib 未参与本次预测')).toBeInTheDocument();
  expect(screen.getByText('真实到期验证与模型健康')).toBeInTheDocument();
  expect(screen.getByText('方向门禁开放')).toBeInTheDocument();
  expect(screen.getAllByText('+2.5%').length).toBeGreaterThan(0);
  expect(screen.getByText('本次当时弃权')).toBeInTheDocument();
  expect(screen.getByText('0.214')).toBeInTheDocument();
  const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST');
  expect(JSON.parse(String(post?.[1]?.body))).toEqual({ code: '600519', horizonDays: 5 });
});

test('switches to one day as an independent registered horizon', async () => {
  const fetch = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) =>
    apiResponse(init?.method === 'POST' ? { ...run, horizonDays: 1,
      report: { ...report, horizonDays: 1 } } : []));
  vi.stubGlobal('fetch', fetch);
  const user = userEvent.setup();

  render(<SingleStockForecastPanel addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.click(screen.getByRole('button', { name: /1 日/ }));
  await user.type(screen.getByLabelText('股票代码'), '600519');
  await user.click(screen.getByRole('button', { name: '运行完整研究' }));

  const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST');
  expect(JSON.parse(String(post?.[1]?.body))).toEqual({ code: '600519', horizonDays: 1 });
  expect(await screen.findByText('1D')).toBeInTheDocument();
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

test('labels gated model decisions as shadow outcomes rather than public calls', async () => {
  const gated = { ...run, outcome: { ...run.outcome, correct: true },
    report: { ...report, decision: 'ABSTAIN' as const, modelDecision: 'UP' as const } };
  vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) =>
    apiResponse(init?.method === 'POST' ? gated : [])));
  const user = userEvent.setup();

  render(<SingleStockForecastPanel addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.type(screen.getByLabelText('股票代码'), '600519');
  await user.click(screen.getByRole('button', { name: '运行完整研究' }));

  expect(await screen.findByText('影子方向命中')).toBeInTheDocument();
  expect(screen.getByText(/健康门禁未向用户输出方向/)).toBeInTheDocument();
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
