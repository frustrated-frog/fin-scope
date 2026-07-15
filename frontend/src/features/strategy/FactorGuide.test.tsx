import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { FactorGuide } from './FactorGuide';
import { ResearchFactorDefinition } from './quantTypes';

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
