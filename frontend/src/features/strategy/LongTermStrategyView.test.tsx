import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { LongTermStrategyView } from './LongTermStrategyView';

beforeEach(() => vi.unstubAllGlobals());

test('records optional stock quantity and average cost for forecast context', async () => {
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (init?.method === 'POST') return apiResponse({});
    if (String(input).endsWith('/overview')) {
      return apiResponse({ holdings: [], targetWeight: 0, currentWeight: 0 });
    }
    return apiResponse([]);
  });
  vi.stubGlobal('fetch', fetch);
  const user = userEvent.setup();

  render(<LongTermStrategyView addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.click(await screen.findByRole('button', { name: '添加资产' }));
  await user.selectOptions(screen.getByLabelText('资产类型'), 'STOCK');
  await user.type(screen.getByLabelText('标的代码'), '600519');
  await user.type(screen.getByLabelText('目标权重'), '5');
  await user.type(screen.getByLabelText('持有数量'), '10');
  await user.type(screen.getByLabelText('平均成本'), '1400');
  await user.click(screen.getByRole('button', { name: '保存资产' }));

  const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST');
  expect(JSON.parse(String(post?.[1]?.body))).toMatchObject({
    code: '600519', type: 'STOCK', quantity: 10, averageCost: 1400
  });
});
