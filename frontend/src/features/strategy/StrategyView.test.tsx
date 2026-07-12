import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { StrategyView } from './StrategyView';

test('shows empty workspace and creates the first fund', async () => {
  const requests: Array<{ url: string; method: string }> = [];
  const overview = { holdings: [], targetWeight: 0, currentWeight: 0 };
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input); const method = init?.method ?? 'GET'; requests.push({ url, method });
    if (url === '/api/strategy/overview' && method === 'GET') return new Response(JSON.stringify(overview), { status: 200 });
    if (url === '/api/strategy/playbooks') return new Response(JSON.stringify([]), { status: 200 });
    if (url === '/api/strategy/stock-theses') return new Response(JSON.stringify([]), { status: 200 });
    if (url === '/api/strategy/reviews') return new Response(JSON.stringify([]), { status: 200 });
    if (url === '/api/strategy/holdings' && method === 'POST') {
      overview.holdings = [{ id: 1, code: '020608', name: '测试基金', type: 'FUND', role: 'CORE', targetWeight: 60, currentWeight: 0, revision: 0 }] as never[];
      overview.targetWeight = 60;
      return new Response(JSON.stringify(overview.holdings[0]), { status: 200 });
    }
    return new Response('{}', { status: 200 });
  }));
  const user = userEvent.setup();
  render(<StrategyView addToast={vi.fn()} setMessage={vi.fn()} />);
  expect(await screen.findByText('还没有组合资产')).toBeInTheDocument();
  await user.click(screen.getAllByRole('button', { name: '添加资产' })[0]);
  await user.type(screen.getByLabelText('标的代码'), '020608');
  await user.type(screen.getByLabelText('目标权重'), '60');
  await user.click(screen.getByRole('button', { name: '保存资产' }));
  expect(requests).toContainEqual({ url: '/api/strategy/holdings', method: 'POST' });
  expect(await screen.findByText('测试基金')).toBeInTheDocument();
});
