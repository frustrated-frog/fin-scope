import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { QuantWorkspace } from './QuantWorkspace';

test('keeps dataset, agent confirmation and experiment actions explicit', async () => {
  const requests: string[] = [];
  let datasets: unknown[] = [];
  let strategies: unknown[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input); const method = init?.method ?? 'GET'; requests.push(`${method} ${path}`);
    if (path === '/api/quant/datasets' && method === 'GET') return new Response(JSON.stringify(datasets));
    if (path === '/api/quant/factors') return new Response(JSON.stringify([{ code: 'EP', name: '盈利收益率', category: '价值', direction: 'HIGH', description: '市盈率倒数', lookbackDays: 0, pointInTime: true }]));
    if (path === '/api/quant/strategies') return new Response(JSON.stringify(strategies));
    if (path === '/api/quant/experiments') return new Response(JSON.stringify([]));
    if (path === '/api/quant/datasets/learning-sample') {
      const value = { id: 1, name: 'A股多因子学习样本 1', market: 'A_SHARE', dataKind: 'LEARNING_SAMPLE', status: 'READY', fingerprint: 'abcdef1234567890' };
      datasets = [value]; return new Response(JSON.stringify(value));
    }
    if (path === '/api/quant/strategy-drafts') return new Response(JSON.stringify({ id: 2, status: 'VALIDATED', spec: { name: '质量价值', datasetId: 1, benchmark: 'EQUAL_WEIGHT', investmentHypothesis: '高质量低估值获得长期补偿', riskBoundary: '控制回撤', factors: [{ code: 'EP', weight: 1, direction: 'HIGH' }], portfolio: { topN: 10, rebalanceEvery: 20, weighting: 'EQUAL' }, execution: { signalPrice: 'CLOSE', fillPrice: 'NEXT_OPEN', slippageBps: 5 }, cost: {} }, validationIssues: [] }));
    if (path === '/api/quant/strategy-drafts/2/confirm') {
      const value = { id: 3, name: '质量价值', datasetId: 1, version: 1, specJson: '{}', strategyFingerprint: '1234567890', datasetFingerprint: 'abcdef', engineVersion: 'quant-java-v1', source: 'AGENT' };
      strategies = [value]; return new Response(JSON.stringify(value));
    }
    return new Response('{}');
  }));

  const user = userEvent.setup();
  render(<QuantWorkspace addToast={vi.fn()} setMessage={vi.fn()} />);
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
