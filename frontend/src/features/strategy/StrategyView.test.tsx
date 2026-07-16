import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { StrategyView } from './StrategyView';

test('shows empty workspace and creates the first fund', async () => {
  const requests: Array<{ url: string; method: string }> = [];
  const overview = { holdings: [], targetWeight: 0, currentWeight: 0 };
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input); const method = init?.method ?? 'GET'; requests.push({ url, method });
    if (url.startsWith('/api/quant/')) return apiResponse([], { status: 200 });
    if (url === '/api/strategy/overview' && method === 'GET') return apiResponse(overview, { status: 200 });
    if (url === '/api/strategy/playbooks') return apiResponse([], { status: 200 });
    if (url === '/api/strategy/stock-theses') return apiResponse([], { status: 200 });
    if (url === '/api/strategy/reviews') return apiResponse([], { status: 200 });
    if (url === '/api/strategy/holdings' && method === 'POST') {
      overview.holdings = [{ id: 1, code: '020608', name: '测试基金', type: 'FUND', role: 'CORE', targetWeight: 60, currentWeight: 0, revision: 0 }] as never[];
      overview.targetWeight = 60;
      return apiResponse(overview.holdings[0], { status: 200 });
    }
    return apiResponse({}, { status: 200 });
  }));
  const user = userEvent.setup();
  render(<StrategyView addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.click(screen.getByRole('button', { name: /长期投资工作台/ }));
  expect(await screen.findByText('还没有组合资产')).toBeInTheDocument();
  await user.click(screen.getAllByRole('button', { name: '添加资产' })[0]);
  await user.type(screen.getByLabelText('标的代码'), '020608');
  await user.type(screen.getByLabelText('目标权重'), '60');
  await user.click(screen.getByRole('button', { name: '保存资产' }));
  expect(requests).toContainEqual({ url: '/api/strategy/holdings', method: 'POST' });
  expect(await screen.findByText('测试基金')).toBeInTheDocument();
});
