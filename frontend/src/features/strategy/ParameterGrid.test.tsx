import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { defaultRecipe } from './ClassicStrategies';
import { ParameterGrid } from './ParameterGrid';

test('previews a grid before submitting and displays actual results with empty pending cells', async () => {
  const posts: RequestInit[] = [];
  const common = {
    rebalance: 'WEEKLY',
    allocation: 'EQUAL',
    holdings: 3,
    id: 11,
    state: 'QUEUED',
    version: null,
    net: null,
    benchmark: null,
    fees: null,
    fills: null,
  };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_input: unknown, init?: RequestInit) => {
      if (init?.method === 'POST') {
        posts.push(init);
        return apiResponse({ batch: 'batch-1', count: 4 });
      }
      return apiResponse(
        posts.length
          ? [
              {
                batch: 'batch-1',
                created: '2026-11-27T15:10:00',
                rows: [
                  common,
                  { ...common, id: 10, state: 'DONE', version: 12, net: 0, benchmark: 0, fees: 0, fills: 0 },
                ],
              },
            ]
          : [],
      );
    }),
  );
  const open = vi.fn(),
    user = userEvent.setup();
  render(<ParameterGrid version={9} recipe={defaultRecipe} capital={10000} onResult={open} />);
  expect(await screen.findByText('此基线尚无批量回测记录。')).toBeVisible();
  await user.click(screen.getByRole('button', { name: '每周' }));
  expect(screen.getByRole('button', { name: '提交 1 组回测' })).toBeDisabled();
  await user.click(screen.getByRole('button', { name: '每周' }));
  await user.click(screen.getByRole('button', { name: '逆波动率' }));
  expect(posts).toHaveLength(0);
  await user.click(screen.getByRole('button', { name: '提交 4 组回测' }));
  const [{ body, headers }] = posts;
  expect(JSON.parse(body as string)).toEqual({
    frequencies: ['DAILY', 'WEEKLY'],
    allocations: ['EQUAL', 'INVERSE_VOL'],
    holdings: [3],
  });
  expect(headers).toHaveProperty('Idempotency-Key');
  expect(await screen.findByText('0.00%')).toBeVisible();
  expect(screen.getByText('排队中')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /分析 #12/ }));
  expect(open).toHaveBeenCalledWith(12);
});

test('switches between risk, costs and actual closing NAV for completed variants', async ()=>{
  vi.stubGlobal('fetch',vi.fn(async ()=>apiResponse([{batch:'charts',created:'2026-11-27T15:00',
    rows:[{id:11,version:9,state:'DONE',rebalance:'WEEKLY',allocation:'EQUAL',holdings:3,net:-.03,
      benchmark:0,fees:12,fills:4,analytics:{drawdown:.06,turnover:null,feeRate:.0012,closes:[{day:'2026-11-26',net:-.01},{day:'2026-11-27',net:-.03}]}}]}])));
  render(<ParameterGrid version={9} recipe={defaultRecipe} capital={10000} onResult={vi.fn()}/>);
  expect(await screen.findByRole('img',{name:/风险与收益/})).toBeVisible();
  await userEvent.click(screen.getByRole('button',{name:'换手与费用'}));
  expect(screen.getByText('这一批尚无可绘制的观测。')).toBeVisible();
  await userEvent.click(screen.getByRole('button',{name:'净值对比'}));
  expect(screen.getByRole('img',{name:/净值对比/})).toBeVisible();
});
