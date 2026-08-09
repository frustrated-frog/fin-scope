import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { StockLearningCardPanel } from './StockLearningCardPanel';

test('lets the learner select a stock and starts the agent without requiring a thesis', async () => {
  const requests: string[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input); requests.push(`${init?.method ?? 'GET'} ${path}`);
    if (path === '/api/stock-learning-cards/600519' && !init?.method) {
      return apiResponse({ card: { code: '600519', name: '贵州茅台' }, latestRun: null });
    }
    if (path === '/api/stock-learning-cards/600519/runs' && init?.method === 'POST') {
      return apiResponse({ id: 5, status: 'RUNNING' });
    }
    return apiResponse({});
  }));
  const user = userEvent.setup();
  render(<StockLearningCardPanel addToast={vi.fn()} setMessage={vi.fn()} />);

  await user.type(screen.getByLabelText('股票代码'), '600519');
  await user.click(screen.getByRole('button', { name: '生成学习卡' }));

  expect(requests).toContain('POST /api/stock-learning-cards/600519/runs');
  expect(await screen.findByText('研究 Agent 正在收集公开证据…')).toBeInTheDocument();
  expect(screen.queryByText('买入条件')).not.toBeInTheDocument();
});
