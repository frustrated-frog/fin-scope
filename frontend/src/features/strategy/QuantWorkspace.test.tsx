import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { QuantWorkspace } from './QuantWorkspace';

test('keeps dataset, agent confirmation and experiment actions explicit', async () => {
  const requests: string[] = [];
  let datasets: unknown[] = [];
  let strategies: unknown[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input); const method = init?.method ?? 'GET'; requests.push(`${method} ${path}`);
    if (path === '/api/quant/datasets' && method === 'GET') return apiResponse(datasets);
    if (path === '/api/quant/factors') return apiResponse([{ code: 'EP', name: '盈利收益率', category: '价值', direction: 'HIGH', description: '市盈率倒数', lookbackDays: 0, pointInTime: true }]);
    if (path === '/api/quant/strategies') return apiResponse(strategies);
    if (path === '/api/quant/experiments') return apiResponse([]);
    if (path === '/api/quant/datasets/learning-sample') {
      const value = { id: 1, name: 'A股多因子学习样本 1', market: 'A_SHARE', dataKind: 'LEARNING_SAMPLE', status: 'READY', fingerprint: 'abcdef1234567890' };
      datasets = [value]; return apiResponse(value);
    }
    if (path === '/api/quant/strategy-drafts') return apiResponse({ id: 2, status: 'VALIDATED', spec: { name: '质量价值', datasetId: 1, benchmark: 'EQUAL_WEIGHT', investmentHypothesis: '高质量低估值获得长期补偿', riskBoundary: '控制回撤', factors: [{ code: 'EP', weight: 1, direction: 'HIGH' }], portfolio: { topN: 10, rebalanceEvery: 20, weighting: 'EQUAL' }, execution: { signalPrice: 'CLOSE', fillPrice: 'NEXT_OPEN', slippageBps: 5 }, cost: {} }, validationIssues: [] });
    if (path === '/api/quant/strategy-drafts/2/confirm') {
      const value = { id: 3, name: '质量价值', datasetId: 1, version: 1, specJson: '{}', strategyFingerprint: '1234567890', datasetFingerprint: 'abcdef', engineVersion: 'quant-java-v1', source: 'AGENT' };
      strategies = [value]; return apiResponse(value);
    }
    return apiResponse({});
  }));

  const user = userEvent.setup();
  render(<QuantWorkspace addToast={vi.fn()} setMessage={vi.fn()} />);
  expect(screen.getByRole('button', { name: /单股预测/ })).toHaveAttribute('aria-current', 'page');
  await user.click(screen.getByRole('button', { name: /策略实验室/ }));
  expect(await screen.findByText('先建立一份学习样本')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: /新建学习样本/ }));
  expect(await screen.findByText(/虚拟学习数据/)).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '生成策略草案' }));
  expect(await screen.findByText('等待你的确认')).toBeInTheDocument();
  expect(requests).not.toContain('POST /api/quant/strategy-drafts/2/confirm');
  await user.click(screen.getByRole('button', { name: '确认并锁定版本' }));
  expect(requests).toContain('POST /api/quant/strategy-drafts/2/confirm');
  expect(await screen.findByRole('button', { name: '启动实验' })).toBeInTheDocument();
});

test('opens a persisted capital draft on the factor manual without auto-running research', async () => {
  const requests: string[] = [];
  const capitalFactor = {
    identity: { namespace: 'capital', code: 'MAIN_FLOW_SHARE', version: '1.0.0' },
    name: '主力流入强度', category: '资金行为', frequency: 'DAILY',
    expectedDirection: 'POSITIVE_HYPOTHESIS', plainMeaning: '主力净流入占成交额比例',
    hypothesis: '较高资金流强度可能对应短期需求压力，但必须验证',
    economicRationale: '成交额归一化后才能初步比较不同股票',
    interpretationBoundary: '主力是供应商统计口径，不是买卖信号',
    requiredFields: ['mainNetInflow', 'amount'], availableAtRule: '只读冻结数据',
    missingPolicy: '缺失时不填零', calculationKey: 'mainNetInflow / amount',
    calculationVersion: 'v1', sourceType: 'FROZEN_CAPITAL_FLOW', sourceRef: 'snapshot',
    evaluationPolicyCode: 'CROSS_SECTIONAL_FORWARD_RETURN', evaluationPolicyVersion: '1.0.0',
    status: 'EXPLORATORY'
  };
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input); requests.push(`${init?.method ?? 'GET'} ${path}`);
    if (path === '/api/quant/datasets') return apiResponse([{ id: 4, name: '真实研究集', market: 'A_SHARE', dataKind: 'REAL', status: 'READY' }]);
    if (path === '/api/factor-research/factors') return apiResponse([capitalFactor]);
    if (path === '/api/quant/strategies' || path === '/api/quant/experiments') return apiResponse([]);
    if (path === '/api/quant/datasets/4/quality') return apiResponse({ datasetId: 4, status: 'READY', availableFactors: ['MAIN_FLOW_SHARE'] });
    if (path === '/api/factor-research/research-drafts/9') return apiResponse({
      id: 9, sourceType: 'CAPITAL_BEHAVIOR', instrumentCode: '600519.SH', instrumentName: '贵州茅台',
      observedAt: '2026-07-14T15:00:00', signalCode: 'PRICE_FLOW_DIVERGENCE', factor: capitalFactor.identity,
      snapshotId: 12, snapshotFingerprint: 'snapshot-12', evidenceRefs: ['snapshot:12'], objectiveTags: [],
      evaluationMode: 'CROSS_SECTIONAL_FACTOR_STUDY', status: 'DRAFT',
      requiredNextSteps: ['冻结同日股票池资金数据', '预注册失败条件'], createdAt: '2026-07-16T01:00:00'
    });
    return apiResponse({});
  }));

  render(<QuantWorkspace
    addToast={vi.fn()}
    setMessage={vi.fn()}
    entryIntent={{ draftId: 9, factorCode: 'MAIN_FLOW_SHARE', sourceLabel: '贵州茅台' }}
    onEntryIntentConsumed={vi.fn()}
  />);

  expect(await screen.findByRole('heading', { name: '主力流入强度' })).toBeInTheDocument();
  expect(await screen.findByText('来源于资金行为研究草稿 #9')).toBeInTheDocument();
  expect(screen.getByText('尚未运行诊断或回测')).toBeInTheDocument();
  expect(requests.some(path => path.includes('/analysis'))).toBe(false);
  expect(requests).not.toContain('POST /api/quant/experiments');
});
