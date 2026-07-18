import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { FactorGuide } from './FactorGuide';
import { ResearchFactorDefinition } from './quantTypes';

afterEach(() => vi.unstubAllGlobals());

function definition(overrides: Partial<ResearchFactorDefinition> = {}): ResearchFactorDefinition {
  return {
    identity: { namespace: 'quant', code: 'EP', version: '1.0.0' },
    name: '盈利收益率', category: '价值', frequency: 'DAILY',
    expectedDirection: 'POSITIVE_HYPOTHESIS',
    plainMeaning: '每一单位市值对应的会计盈利',
    hypothesis: '较高盈利收益率可能获得估值修复，但必须由历史样本检验',
    economicRationale: '把价格倍数取倒数后更适合在同一交易日比较不同股票',
    interpretationBoundary: '亏损企业与一次性损益会让这个指标失真，它不是现金收益率',
    requiredFields: ['pe', 'availableAt'], availableAtRule: '只读取信号时点已披露快照',
    missingPolicy: 'PE 为零或不可见时返回缺失', calculationKey: '1 / pe',
    calculationVersion: 'fundamental-value-v1', sourceType: 'POINT_IN_TIME_FUNDAMENTAL',
    sourceRef: 'quant_fundamental_snapshot', evaluationPolicyCode: 'CROSS_SECTIONAL_FORWARD_RETURN',
    evaluationPolicyVersion: '1.0.0', status: 'CALCULATION_VERIFIED', ...overrides
  };
}

test('lets a beginner find one factor and read the manual before running diagnostics', async () => {
  const user = userEvent.setup();
  const onAnalyze = vi.fn();
  render(<FactorGuide
    definitions={[
      definition(),
      definition({
        identity: { namespace: 'capital', code: 'MAIN_FLOW_SHARE', version: '1.0.0' },
        name: '主力流入强度', category: '资金行为', status: 'EXPLORATORY',
        plainMeaning: '主力净流入占当日成交额的比例',
        interpretationBoundary: '主力是供应商统计口径，不代表真实机构，也不是买卖信号'
      })
    ]}
    selectedCode="EP"
    onSelect={vi.fn()}
    selectedDataset={{ id: 1, name: '学习样本', market: 'A_SHARE', dataKind: 'LEARNING_SAMPLE', status: 'READY' }}
    availableFactors={new Set(['EP'])}
    onAnalyze={onAnalyze}
  />);

  expect(screen.getByRole('heading', { name: '盈利收益率' })).toBeInTheDocument();
  expect(screen.getByText('它衡量什么')).toBeInTheDocument();
  expect(screen.getByText(/最容易误读/)).toBeInTheDocument();
  expect(screen.getByText(/不能证明真实市场有效性/)).toBeInTheDocument();
  expect(screen.getByText('1 / pe')).not.toBeVisible();
  await user.click(screen.getByText('公式、字段与版本'));
  expect(screen.getByText('1 / pe')).toBeInTheDocument();
  expect(onAnalyze).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: '用当前数据集验证' }));
  expect(onAnalyze).toHaveBeenCalledWith('EP');

  await user.type(screen.getByRole('searchbox', { name: '搜索因子' }), '主力');
  expect(screen.getByRole('button', { name: /主力流入强度/ })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /盈利收益率/ })).not.toBeInTheDocument();
});

test('keeps the long factor directory focusable as its own scroll region', () => {
  render(<FactorGuide
    definitions={[definition()]}
    selectedCode="EP"
    onSelect={vi.fn()}
    selectedDataset={{ id: 1, name: '学习样本', market: 'A_SHARE', dataKind: 'LEARNING_SAMPLE', status: 'READY' }}
    availableFactors={new Set(['EP'])}
    onAnalyze={vi.fn()}
  />);

  expect(screen.getByRole('navigation', { name: '可研究因子' })).toHaveAttribute('tabindex', '0');
});

test('blocks unsupported factors and explains a completed analysis before metrics', () => {
  const analysis = {
    datasetId: 2, datasetFingerprint: 'abcdef1234567890', factorCode: 'MAIN_FLOW_SHARE',
    sampleCount: 80, icMean: 0.05, icStd: 0.1, icIr: 0.5, positiveIcRatio: 0.65
  };
  const { rerender } = render(<FactorGuide
    definitions={[definition({
      identity: { namespace: 'capital', code: 'MAIN_FLOW_SHARE', version: '1.0.0' },
      name: '主力流入强度', category: '资金行为', status: 'EXPLORATORY'
    })]}
    selectedCode="MAIN_FLOW_SHARE" onSelect={vi.fn()}
    selectedDataset={{ id: 2, name: '真实样本', market: 'A_SHARE', dataKind: 'REAL', status: 'READY' }}
    availableFactors={new Set()}
    onAnalyze={vi.fn()}
  />);
  expect(screen.getByRole('button', { name: '用当前数据集验证' })).toBeDisabled();
  expect(screen.getByText(/当前数据集没有可计算输入/)).toBeInTheDocument();

  rerender(<FactorGuide
    definitions={[definition({
      identity: { namespace: 'capital', code: 'MAIN_FLOW_SHARE', version: '1.0.0' },
      name: '主力流入强度', category: '资金行为', status: 'EXPLORATORY'
    })]}
    selectedCode="MAIN_FLOW_SHARE" onSelect={vi.fn()}
    selectedDataset={{ id: 2, name: '真实样本', market: 'A_SHARE', dataKind: 'REAL', status: 'READY' }}
    availableFactors={new Set(['MAIN_FLOW_SHARE'])}
    analysis={analysis}
    onAnalyze={vi.fn()}
  />);
  const explanation = screen.getByText(/预设方向较一致/);
  const metric = screen.getByText('方向对齐 IC');
  expect(explanation.compareDocumentPosition(metric) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
});

test('requires approval before the research agent runs and renders its auditable finding', async () => {
  const user = userEvent.setup();
  const plan = {
    id: 9, datasetId: 1, datasetFingerprint: 'abcdef1234567890',
    factor: { namespace: 'quant', code: 'EP', version: '1.0.0' }, question: '证据是否支持预设方向？',
    status: 'AWAITING_APPROVAL', plan: ['检查数据集质量', '运行确定性横截面诊断'],
    allowedTools: ['inspect_dataset', 'run_factor_diagnostics'], maxToolCalls: 4, toolCallsUsed: 0,
    maxLlmCalls: 0, llmCallsUsed: 0, maxRunSeconds: 60, evidenceJson: '', evidenceHash: '',
    findingJson: '', stopReason: '', trace: []
  };
  const completed = {
    ...plan, status: 'COMPLETED', toolCallsUsed: 3, evidenceHash: 'a'.repeat(64), stopReason: 'EVIDENCE_GATE_BLOCKED',
    findingJson: JSON.stringify({ verdict: 'INCONCLUSIVE', summary: '当前证据不足。',
      counterEvidence: ['学习样本不能证明真实市场有效性'], blockingReasons: ['数据不是实盘研究数据'],
      nextSteps: ['在真实点时数据上复核'] }),
    trace: [{ id: 1, nodeName: 'inspect_dataset', status: 'SUCCESS', output: '数据集已读取' }]
  };
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    if (path.endsWith('/approve')) return apiResponse(completed);
    return apiResponse(plan, { status: 201 });
  }));

  render(<FactorGuide definitions={[definition()]} selectedCode="EP" onSelect={vi.fn()}
    selectedDataset={{ id: 1, name: '学习样本', market: 'A_SHARE', dataKind: 'LEARNING_SAMPLE', status: 'READY' }}
    availableFactors={new Set(['EP'])} onAnalyze={vi.fn()} />);

  await user.click(screen.getByRole('button', { name: '生成复核计划' }));
  expect(await screen.findByRole('button', { name: '批准并运行' })).toBeInTheDocument();
  expect(screen.getByText('0/4')).toBeInTheDocument();
  expect(screen.queryByText('当前证据不足。')).not.toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '批准并运行' }));
  expect(await screen.findByText('当前证据不足。')).toBeInTheDocument();
  expect(screen.getByText(/证据哈希 aaaaaaaaaaaaaaaa/)).toBeInTheDocument();
  expect(globalThis.fetch).toHaveBeenCalledTimes(2);
});

test('survives an empty budget-exhausted finding and explains the stop', async () => {
  const user = userEvent.setup();
  const plan = {
    id: 10, datasetId: 1, datasetFingerprint: 'abcdef1234567890',
    factor: { namespace: 'quant', code: 'EP', version: '1.0.0' }, question: 'test',
    status: 'AWAITING_APPROVAL', plan: ['检查数据集'], allowedTools: ['inspect_dataset'],
    maxToolCalls: 4, toolCallsUsed: 0, maxLlmCalls: 0, llmCallsUsed: 0, maxRunSeconds: 60,
    evidenceJson: '', evidenceHash: '', findingJson: '', stopReason: '', trace: []
  };
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => String(input).endsWith('/approve')
    ? apiResponse({ ...plan, status: 'BUDGET_EXHAUSTED', findingJson: '{}', stopReason: 'TOOL_BUDGET_EXHAUSTED' })
    : apiResponse(plan, { status: 201 })));

  render(<FactorGuide definitions={[definition()]} selectedCode="EP" onSelect={vi.fn()}
    selectedDataset={{ id: 1, name: '研究样本', market: 'A_SHARE', dataKind: 'REAL', status: 'READY' }}
    availableFactors={new Set(['EP'])} onAnalyze={vi.fn()} />);

  await user.click(screen.getByRole('button', { name: '生成复核计划' }));
  await user.click(await screen.findByRole('button', { name: '批准并运行' }));
  expect(await screen.findByText(/预算耗尽.*TOOL_BUDGET_EXHAUSTED/)).toBeInTheDocument();
});

test('explains multi-horizon, out-of-sample and cost evidence progressively', async () => {
  const user = userEvent.setup();
  render(<FactorGuide definitions={[definition()]} selectedCode="EP" onSelect={vi.fn()}
    selectedDataset={{ id: 2, name: '真实研究集', market: 'A_SHARE', dataKind: 'REAL', status: 'READY' }}
    availableFactors={new Set(['EP'])} onAnalyze={vi.fn()} analysis={{
      datasetId: 2, datasetFingerprint: 'fingerprint', factorCode: 'EP', sampleCount: 80,
      icMean: 0.05, icStd: 0.1, icIr: 0.5, positiveIcRatio: 0.65,
      directionAdjustedIcMean: 0.05, favorableIcRatio: 0.65, sampleEvidence: 'DIRECTIONALLY_ALIGNED',
      conclusion: 'INCONCLUSIVE', validationEligible: false, evaluationPolicyVersion: 'cross-sectional-evidence-v2',
      horizons: [1, 3, 5, 10, 20].map(horizonDays => ({
        horizonDays, sampleCount: 80 - horizonDays, totalEligibleDays: 82, minCrossSectionSize: 20,
        coverageRatio: 0.9, icMean: 0.05, icStd: 0.1, icIr: 0.5, favorableIcRatio: 0.65,
        directionAdjustedIcMean: horizonDays === 20 ? -0.01 : 0.05,
        directionAdjustedCiLower: -0.01, directionAdjustedCiUpper: 0.11,
        directionAdjustedQuantileSpread: 0.003, directionAdjustedMonotonicity: 0.4
      })),
      robustness: {
        protocolVersion: 'cross-sectional-robustness-v1', inSampleCount: 56, outOfSampleCount: 24,
        inSampleIcMean: 0.06, outOfSampleIcMean: -0.01, directionAdjustedInSampleIcMean: 0.06,
        directionAdjustedOutOfSampleIcMean: -0.01, outOfSampleDirectionAligned: false,
        rankTurnoverProxy: 0.42, netQuantileSpreadAt10Bps: 0.0026,
        netQuantileSpreadAt30Bps: 0.0017, costModel: 'rank-turnover-proxy-v1'
      }
    }} />);

  expect(screen.getByText(/样本外方向冲突/)).toBeInTheDocument();
  expect(screen.getByText(/换手代理 42.0%/)).toBeInTheDocument();
  expect(screen.getByText('多持有期证据')).toBeInTheDocument();
  expect(screen.queryByText('20 日')).not.toBeVisible();
  await user.click(screen.getByText('多持有期证据'));
  expect(screen.getByText('20 日')).toBeInTheDocument();
});
