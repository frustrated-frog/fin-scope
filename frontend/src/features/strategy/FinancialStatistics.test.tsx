import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { FinancialStatistics } from './FinancialStatistics';
import { ResearchBrief } from './ResearchBrief';

test('opens research and statistics independently and reads existing reports without starting a job', async () => {
  const open = vi.fn(),
    stats = vi.fn(),
    user = userEvent.setup();
  const item = {
    id: 6,
    state: 'PUBLISHED',
    trigger_key: 'manual:a',
    plan: { title: '下跌时相关性更高吗？' },
    report: { finding: '样本没有支持这个假设。' },
  };
  const calls = vi.fn(async (path: string) => apiResponse(path.endsWith('/6') ? item : { items: [item] }));
  vi.stubGlobal('fetch', calls);
  render(<ResearchBrief onStudies={open} onStatistics={stats} />);
  await user.click(await screen.findByRole('button', { name: /下跌时相关性/ }));
  expect(open).toHaveBeenCalledWith(6);
  await user.click(screen.getByRole('button', { name: /金融统计与组合研究/ }));
  expect(stats).toHaveBeenCalledOnce();
  expect(calls.mock.calls.every(([path]) => path.startsWith('/api/quant/research-center'))).toBe(true);
});

test('distinguishes the same-day index groups from prior known stages and preserves the submitted design', async () => {
  const user = userEvent.setup(),
    requests: string[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (path: string, init?: RequestInit) => {
      if (path.includes('watchlist')) {
        return apiResponse([{ type: 'STOCK', name: '示例股票', code: '600015' }]);
      }
      requests.push(String(init?.body));
      return apiResponse({
        start: '2026-04-01',
        end: '2026-09-30',
        benchmark: 'SH:000300',
        sources: {},
        distribution: {
          n: 4,
          compound: 0.02,
          mean: 0.005,
          logMean: 0.004,
          volatility: 0.14,
          bins: [0, 0, 1, 2, 1, 0],
          percentiles: [-0.02, 0, 0.02],
          skew: 0,
          excessKurtosis: 0,
          lagOne: null,
          absoluteLagOne: null,
          lagPairs: 2,
          meanInterval: null,
        },
        pricing: { n: 4, beta: 0.8, annualAlpha: 0.02, rSquared: 0.77 },
        pair: { n: 4, correlation: 0.44, rolling: [], frontier: null },
        regimes: [{ label: '上涨', n: 3, mean: 0.008 }],
        marketStages: [{ stage: 'RANGE_ROTATION', n: 1, mean: -0.02 }],
      });
    }),
  );
  render(<FinancialStatistics onBack={vi.fn()} />);
  await screen.findByRole('heading', { name: '日收益落在哪些区间？' });
  await user.click(screen.getByRole('button', { name: '市场状态' }));
  expect(screen.getByText(/覆盖 1 \/ 4 天/)).toBeInTheDocument();
  expect(within(screen.getAllByRole('table')[1]).getByText('震荡轮动')).toBeInTheDocument();
  await user.selectOptions(screen.getByLabelText('研究资产'), 'SH:600015');
  expect(requests).toHaveLength(1);
  await user.click(screen.getByRole('button', { name: '计算分析' }));
  expect(await screen.findByRole('heading', { name: /示例股票 与 创业板指/ })).toBeInTheDocument();
  expect(JSON.parse(requests[1]).asset).toBe('SH:600015');
  await user.selectOptions(screen.getByLabelText('对照资产'), 'SH:600015');
  expect(screen.getByRole('button', { name: '计算分析' })).toBeDisabled();
});
